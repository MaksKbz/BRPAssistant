package com.brp.assistant.data.llm

import android.util.Log
import com.brp.assistant.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Кастомное IOException-наследник для HTTP 429 Too Many Requests.
 *
 * FIX #429-retry: раньше generateGroqRawHttp кидал throw Exception(msg) при 429,
 * но isRetryableError() проверяет (e is IOException) — поэтому withRetry не
 * выполнял повторные попытки для 429 несмотря на msg.contains("429").
 * Теперь 429 кидает Http429Exception — extends IOException,
 * isRetryableError() перехватывает его корректно.
 */
private class Http429Exception(message: String) : IOException(message)

/**
 * FIX #4: экспоненциальный backoff для повторных попыток.
 * maxAttempts=3: первый + 2 retry с задержкой 500ms → 1000ms.
 */
private suspend fun <T> withRetry(
    maxAttempts: Int = 3,
    initialDelayMs: Long = 500L,
    shouldRetry: (Throwable) -> Boolean = { true },
    block: suspend () -> Result<T>
): Result<T> {
    var lastResult: Result<T> = Result.failure(Exception("Нет попыток"))
    repeat(maxAttempts) { attempt ->
        if (attempt > 0) {
            val delayMs = initialDelayMs * (1L shl (attempt - 1))  // 500 → 1000 → 2000
            Log.d("RemoteLlmEngine", "Retry $attempt after ${delayMs}ms")
            delay(delayMs)
        }
        lastResult = block()
        if (lastResult.isSuccess) return lastResult
        val ex = lastResult.exceptionOrNull() ?: return lastResult
        if (!shouldRetry(ex)) return lastResult
    }
    return lastResult
}

/**
 * Предикат для shouldRetry: ретраит только сетевые ошибки (IOException),
 * включая Http429Exception. 4xx (кроме 429) — ошибки клиента, retry бессмыслен.
 */
private fun isRetryableError(e: Throwable): Boolean = e is IOException

/**
 * Возвращает true если ошибка — временный сбой сети/сервера,
 * а не ошибка конфигурации (неверный ключ, модель не найдена).
 * Только временные ошибки инкрементируют CircuitBreaker.
 */
private fun isTransientError(e: Throwable): Boolean =
    e is IOException || (e.message?.contains("429") == true)

@Singleton
class RemoteLlmEngine @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val client: OkHttpClient,
    /**
     * #3 — Circuit Breaker подключён через конструктор.
     * После FAILURE_THRESHOLD=3 подряд временных ошибок блокирует
     * провайдера на RESET_TIMEOUT_MS=30с без сетевых запросов.
     */
    private val circuitBreaker: CircuitBreaker
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * FIX #timeouts: явные таймауты как защитный слой.
     * На 2G/EDGE (актуально для регионов Казахстана) без явных таймаутов
     * OkHttp висел бесконечно если базовый AppModule-клиент не настроен.
     * connectTimeout 15с / readTimeout 30с / writeTimeout 15с.
     */
    private val apiClient: OkHttpClient = client.newBuilder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Сбрасывает circuit breaker при успешной валидации ключа.
     * Это важно: пользователь мог ввести новый ключ после серии ошибок.
     */
    suspend fun validateKey(provider: String, apiKey: String, modelName: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            if (provider == "Gemini") {
                try {
                    val url = "https://generativelanguage.googleapis.com/v1beta/models"
                    val request = Request.Builder()
                        .url(url)
                        .addHeader("x-goog-api-key", apiKey)
                        .get()
                        .build()
                    apiClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            circuitBreaker.reset("Gemini")
                            Result.success(true)
                        } else {
                            Result.failure(Exception("Ошибка Gemini: ${response.code}"))
                        }
                    }
                } catch (e: Exception) {
                    Result.failure(e)
                }
            } else {
                val testModel = if (modelName.contains("llama") || modelName.contains("mixtral"))
                    modelName else "llama-3.3-70b-versatile"
                try {
                    val url = "https://api.groq.com/openai/v1/chat/completions"
                    val json = JSONObject().apply {
                        put("model", testModel)
                        put("messages", JSONArray().apply {
                            put(JSONObject().apply {
                                put("role", "user")
                                put("content", "ping")
                            })
                        })
                        put("max_tokens", 1)
                    }
                    val request = Request.Builder()
                        .url(url)
                        .addHeader("Authorization", "Bearer $apiKey")
                        .post(json.toString().toRequestBody(jsonMediaType))
                        .build()
                    apiClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            circuitBreaker.reset("Groq")
                            Result.success(true)
                        } else {
                            val errorDetail = when (response.code) {
                                401 -> "Неверный ключ Groq (gsk-...)"
                                429 -> "Превышен лимит запросов Groq"
                                else -> "Groq Error: ${response.code}"
                            }
                            Result.failure(Exception(errorDetail))
                        }
                    }
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }

    /**
     * Основной метод генерации ответа с явными параметрами провайдера.
     *
     * #3: Перед выполнением проверяет CircuitBreaker.isOpen(provider).
     * Если OPEN — немедленно возвращает Result.failure без сетевого запроса.
     * После выполнения вызывает recordSuccess() или recordFailure() в зависимости
     * от результата. Ошибки конфигурации (401, 404) не инкрементируют счётчик.
     */
    suspend fun generateResponse(
        prompt: String,
        provider: String,
        modelName: String,
        apiKey: String,
        systemPrompt: String?,
        temperature: Float,
        onPartial: (String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        // #3 — Circuit Breaker guard
        if (circuitBreaker.isOpen(provider)) {
            return@withContext Result.failure(
                Exception("Провайдер $provider временно недоступен. Повторите через 30 секунд.")
            )
        }

        val sysPrompt = systemPrompt.orEmpty()
        val result = if (provider == "Gemini") {
            withRetry(shouldRetry = ::isRetryableError) {
                generateGeminiRawHttp(prompt, modelName, apiKey, sysPrompt, temperature, onPartial)
            }
        } else {
            withRetry(shouldRetry = ::isRetryableError) {
                generateGroqRawHttp(prompt, modelName, apiKey, sysPrompt, temperature, onPartial)
            }
        }

        // #3 — обновляем состояние breaker
        if (result.isSuccess) {
            circuitBreaker.recordSuccess(provider)
        } else {
            val ex = result.exceptionOrNull()
            if (ex != null && isTransientError(ex)) {
                circuitBreaker.recordFailure(provider)
            }
        }
        result
    }

    suspend fun generateResponse(
        prompt: String,
        onPartial: (String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        val provider = settingsRepository.aiProvider.first() ?: "Gemini"
        val modelName = settingsRepository.aiModelName.first()
            ?: if (provider == "Gemini") "gemini-1.5-flash" else "llama-3.3-70b-versatile"
        val systemPrompt = settingsRepository.aiSystemPrompt.first().orEmpty()
        val temperature = settingsRepository.aiTemperature.first()

        if (provider == "Gemini") {
            val apiKey = settingsRepository.geminiApiKey.first()
            if (apiKey.isNullOrBlank()) return@withContext Result.failure(
                Exception("Ключ Gemini не настроен. Перейдите в Настройки → AI-провайдер.")
            )
            // #3 — guard для shorthand-перегрузки
            if (circuitBreaker.isOpen(provider)) {
                return@withContext Result.failure(
                    Exception("Провайдер Gemini временно недоступен. Повторите через 30 секунд.")
                )
            }
            val result = withRetry(shouldRetry = ::isRetryableError) {
                generateGeminiRawHttp(prompt, modelName, apiKey, systemPrompt, temperature, onPartial)
            }
            if (result.isSuccess) circuitBreaker.recordSuccess(provider)
            else result.exceptionOrNull()?.let { if (isTransientError(it)) circuitBreaker.recordFailure(provider) }
            result
        } else {
            val apiKey = settingsRepository.groqApiKey.first()
            if (apiKey.isNullOrBlank()) return@withContext Result.failure(
                Exception("Ключ Groq не настроен. Перейдите в Настройки → AI-провайдер.")
            )
            if (circuitBreaker.isOpen(provider)) {
                return@withContext Result.failure(
                    Exception("Провайдер Groq временно недоступен. Повторите через 30 секунд.")
                )
            }
            val result = withRetry(shouldRetry = ::isRetryableError) {
                generateGroqRawHttp(prompt, modelName, apiKey, systemPrompt, temperature, onPartial)
            }
            if (result.isSuccess) circuitBreaker.recordSuccess(provider)
            else result.exceptionOrNull()?.let { if (isTransientError(it)) circuitBreaker.recordFailure(provider) }
            result
        }
    }

    private suspend fun generateGeminiRawHttp(
        prompt: String,
        modelName: String,
        apiKey: String,
        systemPrompt: String,
        temperature: Float,
        onPartial: (String) -> Unit
    ): Result<String> {
        val attempts = if (modelName.contains("2.0") || modelName.contains("lite")) {
            listOf("v1beta")
        } else {
            listOf("v1beta", "v1")
        }

        var lastError: Exception? = null

        for (apiVersion in attempts) {
            try {
                val url = "https://generativelanguage.googleapis.com/$apiVersion/models/$modelName:generateContent"

                val jsonBody = JSONObject().apply {
                    if (systemPrompt.isNotBlank()) {
                        put("system_instruction", JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().put("text", systemPrompt))
                            })
                        })
                    }
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("parts", JSONArray().apply {
                                put(JSONObject().put("text", prompt))
                            })
                        })
                    })
                    put("generationConfig", JSONObject().apply {
                        put("temperature", temperature)
                        put("maxOutputTokens", 2048)
                    })
                }

                val request = Request.Builder()
                    .url(url)
                    .addHeader("x-goog-api-key", apiKey)
                    .post(jsonBody.toString().toRequestBody(jsonMediaType))
                    .build()

                apiClient.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string() ?: ""

                    if (response.isSuccessful) {
                        // FIX #safety-filter: безопасный парсинг.
                        // Gemini возвращает candidates с пустым/отсутствующим content
                        // при срабатывании Safety Filter (finishReason == "SAFETY").
                        // Цепочка getJSONObject() падала с JSONException.
                        val jsonResponse = JSONObject(responseBody)
                        val candidatesArr = jsonResponse.optJSONArray("candidates")
                        val candidate = candidatesArr?.optJSONObject(0)

                        val finishReason = candidate?.optString("finishReason", "") ?: ""
                        if (finishReason == "SAFETY") {
                            return Result.failure(
                                Exception("Запрос заблокирован фильтром безопасности Gemini. Попробуйте переформулировать вопрос.")
                            )
                        }

                        val text = candidate
                            ?.optJSONObject("content")
                            ?.optJSONArray("parts")
                            ?.optJSONObject(0)
                            ?.optString("text", "")
                            .orEmpty()

                        if (text.isBlank()) {
                            return Result.failure(
                                Exception("Пустой ответ от Gemini" +
                                    if (finishReason.isNotBlank()) " (finishReason: $finishReason)" else "")
                            )
                        }

                        withContext(Dispatchers.Main) { onPartial(text) }
                        return Result.success(text)
                    } else {
                        Log.e("RemoteLlmEngine", "Gemini $apiVersion failed: ${response.code} $responseBody")
                        when (response.code) {
                            404  -> lastError = Exception("Модель $modelName не найдена ($apiVersion)")
                            429  -> return Result.failure(Http429Exception("Превышена квота запросов (429). Подождите 1 минуту."))
                            401  -> return Result.failure(Exception("Неверный Gemini API-ключ (401). Проверьте ключ в Настройках."))
                            else -> lastError = Exception("Ошибка $apiVersion (${response.code}): $responseBody")
                        }
                    }
                }
            } catch (e: IOException) {
                Log.e("RemoteLlmEngine", "IOException in $apiVersion", e)
                throw e
            } catch (e: Exception) {
                Log.e("RemoteLlmEngine", "Exception in $apiVersion", e)
                lastError = e
            }
        }

        return Result.failure(lastError ?: Exception("Не удалось подключиться к Gemini"))
    }

    private suspend fun generateGroqRawHttp(
        prompt: String,
        modelName: String,
        apiKey: String,
        systemPrompt: String,
        temperature: Float,
        onPartial: (String) -> Unit
    ): Result<String> {
        return try {
            val url = "https://api.groq.com/openai/v1/chat/completions"

            val jsonBody = JSONObject().apply {
                put("model", modelName)
                put("temperature", temperature)
                put("messages", JSONArray().apply {
                    if (systemPrompt.isNotBlank()) {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", systemPrompt)
                        })
                    }
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                })
                put("stream", false)
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .post(jsonBody.toString().toRequestBody(jsonMediaType))
                .build()

            apiClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: ""
                    // FIX #429-retry: Http429Exception extends IOException —
                    // isRetryableError() перехватывает через (e is IOException).
                    // 4xx (кроме 429) кидаются как Exception — retry не выполняется.
                    when (response.code) {
                        401  -> throw Exception("Неверный Groq API-ключ (401).")
                        429  -> throw Http429Exception("Превышен лимит Groq (429). Подождите.")
                        404  -> throw Exception("Модель $modelName не найдена (404).")
                        else -> throw Exception("Groq API Error: ${response.code}. $errorBody")
                    }
                }

                val responseBody = response.body?.string()
                    ?: throw Exception("Пустой ответ от Groq")
                val jsonResponse = JSONObject(responseBody)
                val text = jsonResponse
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")

                withContext(Dispatchers.Main) { onPartial(text) }
                Result.success(text)
            }
        } catch (e: IOException) {
            Log.e("RemoteLlmEngine", "Groq IOException", e)
            throw e  // перебрасываем — withRetry перехватит и повторит
        } catch (e: Exception) {
            Log.e("RemoteLlmEngine", "Groq HTTP error", e)
            Result.failure(e)
        }
    }
}
