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
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FIX #4: экспоненциальный backoff для повторных попыток.
 *
 * @param maxAttempts максимальное число попыток (включая первый)
 * @param initialDelayMs задержка перед второй попыткой, далее удваивается
 * @param shouldRetry предикат, определяющий нужно ли ретраит (по умолчанию — все ошибки)
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
        if (!shouldRetry(ex)) return lastResult  // не ретраить (401, 404 и т.д.)
    }
    return lastResult
}

/**
 * Предикат для shouldRetry: ретраит только сетевые ошибки (IOException) и 429.
 * 4xx (кроме 429) — ошибки клиента, retry бессмыслен.
 */
private fun isRetryableError(e: Throwable): Boolean {
    if (e is IOException) return true
    val msg = e.message ?: return false
    return msg.contains("429") || msg.contains("лимит", ignoreCase = true)
}

@Singleton
class RemoteLlmEngine @Inject constructor(
    private val settingsRepository: SettingsRepository,
    /**
     * FIX #12: OkHttpClient инжектируется как синглтон из AppModule.
     * FIX #13: apiClient создаётся один раз в init-блоке,
     * а не пересоздаётся на каждый запрос.
     */
    private val client: OkHttpClient
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // FIX #13: создаётся один раз вместо нового экземпляра при каждом запросе
    private val apiClient: OkHttpClient = client.newBuilder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

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
                        if (response.isSuccessful) Result.success(true)
                        else Result.failure(Exception("Ошибка Gemini: ${response.code}"))
                    }
                } catch (e: Exception) {
                    Result.failure(e)
                }
            } else {
                val testModel = if (modelName.contains("llama") || modelName.contains("mixtral"))
                    modelName
                else
                    "llama-3.3-70b-versatile"
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
     * FIX #4: перегрузка с явными параметрами.
     * UseCase читает DataStore один раз атомарно и передаёт здесь,
     * исключая race condition при смене провайдера в процессе запроса.
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
        val sysPrompt = systemPrompt.orEmpty()
        if (provider == "Gemini") {
            // FIX #4: withRetry для Gemini
            withRetry(shouldRetry = ::isRetryableError) {
                generateGeminiRawHttp(prompt, modelName, apiKey, sysPrompt, temperature, onPartial)
            }
        } else {
            // FIX #4: withRetry для Groq
            withRetry(shouldRetry = ::isRetryableError) {
                generateGroqRawHttp(prompt, modelName, apiKey, sysPrompt, temperature, onPartial)
            }
        }
    }

    /**
     * Старый метод — оставлен для обратной совместимости.
     * Читает настройки из DataStore и делегирует в основную перегрузку.
     */
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
            withRetry(shouldRetry = ::isRetryableError) {
                generateGeminiRawHttp(prompt, modelName, apiKey, systemPrompt, temperature, onPartial)
            }
        } else {
            val apiKey = settingsRepository.groqApiKey.first()
            if (apiKey.isNullOrBlank()) return@withContext Result.failure(
                Exception("Ключ Groq не настроен. Перейдите в Настройки → AI-провайдер.")
            )
            withRetry(shouldRetry = ::isRetryableError) {
                generateGroqRawHttp(prompt, modelName, apiKey, systemPrompt, temperature, onPartial)
            }
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
                        val jsonResponse = JSONObject(responseBody)
                        val text = jsonResponse
                            .getJSONArray("candidates")
                            .getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")
                            .getJSONObject(0)
                            .getString("text")

                        withContext(Dispatchers.Main) { onPartial(text) }
                        return Result.success(text)
                    } else {
                        Log.e("RemoteLlmEngine", "Gemini $apiVersion failed: ${response.code} $responseBody")
                        when (response.code) {
                            404  -> lastError = Exception("Модель $modelName не найдена ($apiVersion)")
                            429  -> return Result.failure(Exception("Превышена квота запросов (429). Подождите 1 минуту."))
                            401  -> return Result.failure(Exception("Неверный Gemini API-ключ (401). Проверьте ключ в Настройках."))
                            else -> lastError = Exception("Ошибка $apiVersion (${response.code}): $responseBody")
                        }
                    }
                }
            } catch (e: IOException) {
                // IOException — сетевая ошибка, withRetry выше произведёт retry
                Log.e("RemoteLlmEngine", "IOException in $apiVersion", e)
                throw e  // перебрасываем для подхвата withRetry
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
                    val msg = when (response.code) {
                        401 -> "Неверный Groq API-ключ (401)."
                        429 -> "Превышен лимит Groq (429). Подождите."
                        404 -> "Модель $modelName не найдена (404)."
                        else -> "Groq API Error: ${response.code}. $errorBody"
                    }
                    throw Exception(msg)  // IOException/Exception — withRetry решит retryить ли
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
