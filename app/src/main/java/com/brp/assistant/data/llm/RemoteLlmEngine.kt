package com.brp.assistant.data.llm

import android.util.Log
import com.brp.assistant.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteLlmEngine @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

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
                    client.newCall(request).execute().use { response ->
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
                    client.newCall(request).execute().use { response ->
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

    suspend fun generateResponse(
        prompt: String,
        onPartial: (String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        val provider = settingsRepository.aiProvider.first() ?: "Gemini"
        val modelName = settingsRepository.aiModelName.first()
            ?: if (provider == "Gemini") "gemini-1.5-flash" else "llama-3.3-70b-versatile"
        val systemPrompt = settingsRepository.aiSystemPrompt.first()
        val temperature = settingsRepository.aiTemperature.first()

        if (provider == "Gemini") {
            generateGeminiRawHttp(prompt, modelName, systemPrompt, temperature, onPartial)
        } else {
            generateGroqRawHttp(prompt, modelName, systemPrompt, temperature, onPartial)
        }
    }

    private suspend fun generateGeminiRawHttp(
        prompt: String,
        modelName: String,
        systemPrompt: String,
        temperature: Float,
        onPartial: (String) -> Unit
    ): Result<String> {
        val apiKey = settingsRepository.geminiApiKey.first()
        if (apiKey.isNullOrBlank()) return Result.failure(Exception("Ключ Gemini не настроен. Перейдите в Настройки → AI-провайдер."))

        val attempts = if (modelName.contains("2.0") || modelName.contains("lite")) {
            listOf("v1beta")
        } else {
            listOf("v1beta", "v1")
        }

        var lastError: Exception? = null

        for (apiVersion in attempts) {
            try {
                // FIX #10: убран ?key=$apiKey из URL — ключ передаётся только через заголовок
                // (передача ключа в URL попадает в логи серверов и прокси)
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
                    // FIX #10: только заголовок, без дублирования ключа в URL
                    .addHeader("x-goog-api-key", apiKey)
                    .post(jsonBody.toString().toRequestBody(jsonMediaType))
                    .build()

                client.newCall(request).execute().use { response ->
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
        systemPrompt: String,
        temperature: Float,
        onPartial: (String) -> Unit
    ): Result<String> {
        // FIX #4: groqApiKey (переименовано с grokApiKey)
        val apiKey = settingsRepository.groqApiKey.first()
        if (apiKey.isNullOrBlank()) return Result.failure(Exception("Ключ Groq не настроен. Перейдите в Настройки → AI-провайдер."))

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

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: ""
                    val msg = when (response.code) {
                        401 -> "Неверный Groq API-ключ (401)."
                        429 -> "Превышен лимит Groq (429). Подождите."
                        404 -> "Модель $modelName не найдена (404)."
                        else -> "Groq API Error: ${response.code}. $errorBody"
                    }
                    throw Exception(msg)
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
        } catch (e: Exception) {
            Log.e("RemoteLlmEngine", "Groq HTTP error", e)
            Result.failure(e)
        }
    }
}
