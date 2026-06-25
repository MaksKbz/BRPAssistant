package com.brp.assistant.data.llm

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Движок вывода для моделей формата .litertlm (LiteRT-LM API от Google).
 *
 * Используется для моделей с [ModelFormat.LITERTLM], в то время как
 * MediaPipe LlmInference обслуживает [ModelFormat.TASK].
 *
 * Поддерживаемые модели (Apache 2.0, без авторизации):
 *   - Qwen3-0.6B  (~600 МБ, 3 ГБ RAM)
 *   - Qwen3-1.7B  (~1.7 ГБ, 4 ГБ RAM)
 *   - Qwen3-4B    (~4 ГБ, 8 ГБ RAM)
 *
 * Документация: https://developers.google.com/edge/litert-lm/android
 * Репозиторий:  https://github.com/google-ai-edge/LiteRT-LM
 */
@Singleton
class LiteRtLmEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "LiteRtLmEngine"
    }

    private var engine: Engine? = null
    private var activeModelInfo: OfflineModelInfo? = null

    /**
     * Инициализирует движок для указанной модели.
     * Пытается запустить на GPU; при неудаче переключается на CPU.
     *
     * @return Result.success если инициализация прошла успешно
     */
    suspend fun initialize(model: OfflineModelInfo, modelFile: File): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                closeInternal()

                if (!modelFile.exists()) {
                    return@withContext Result.failure(
                        Exception("Файл модели не найден: ${modelFile.absolutePath}")
                    )
                }

                if (modelFile.extension.lowercase() != "litertlm") {
                    return@withContext Result.failure(
                        Exception(
                            "LiteRtLmEngine поддерживает только .litertlm файлы.\n" +
                            "Файл: ${modelFile.name}"
                        )
                    )
                }

                // Пробуем GPU-бэкенд, при ошибке — CPU
                val engineConfig = tryBuildConfig(modelFile, useGpu = true)
                    ?: tryBuildConfig(modelFile, useGpu = false)
                    ?: return@withContext Result.failure(
                        Exception("Не удалось создать конфигурацию LiteRT-LM движка")
                    )

                engine = Engine.create(engineConfig)
                activeModelInfo = model
                Log.i(TAG, "LiteRT-LM initialized: ${model.title}")
                Result.success(Unit)

            } catch (e: Throwable) {
                Log.e(TAG, "LiteRT-LM init failed", e)
                closeInternal()
                Result.failure(e)
            }
        }

    /**
     * Генерирует ответ на промпт в виде потока частичных токенов.
     * Для совместимости с существующим MediaPipe-кодом принимает [onPartial] callback.
     *
     * Системный промпт передаётся в [systemPrompt] и встраивается
     * в ConversationConfig — LiteRT-LM обрабатывает его нативно.
     */
    fun generateResponseStreaming(
        prompt: String,
        systemPrompt: String = "",
        onPartial: (String) -> Unit
    ): Flow<String> = flow {
        val eng = engine
            ?: throw IllegalStateException("LiteRtLmEngine не инициализирован")

        val convConfig = if (systemPrompt.isNotBlank()) {
            ConversationConfig.Builder()
                .setSystemPrompt(systemPrompt)
                .build()
        } else {
            ConversationConfig.Builder().build()
        }

        eng.createConversation(convConfig).use { conversation ->
            val responseFlow = conversation.generateResponse(prompt)
            responseFlow.collect { token ->
                onPartial(token)
                emit(token)
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Блокирующий вариант генерации — собирает полный ответ.
     * Используется как fallback там, где Flow не поддерживается.
     */
    suspend fun generateResponse(
        prompt: String,
        systemPrompt: String = "",
        onPartial: (String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val sb = StringBuilder()
            generateResponseStreaming(prompt, systemPrompt, onPartial)
                .collect { token -> sb.append(token) }
            Result.success(sb.toString())
        } catch (e: Exception) {
            Log.e(TAG, "generateResponse failed", e)
            Result.failure(e)
        }
    }

    fun isReady(): Boolean = engine != null && activeModelInfo != null

    fun getActiveModelInfo(): OfflineModelInfo? = activeModelInfo

    fun getActivePromptStyle(): PromptStyle =
        activeModelInfo?.promptStyle ?: PromptStyle.CHATML

    fun close() = closeInternal()

    private fun closeInternal() {
        try { engine?.close() } catch (e: Throwable) { Log.w(TAG, "close error", e) }
        engine = null
        activeModelInfo = null
    }

    // ── Вспомогательные ──────────────────────────────────────────────────────

    private fun tryBuildConfig(modelFile: File, useGpu: Boolean): EngineConfig? {
        return try {
            EngineConfig.Builder()
                .setModelPath(modelFile.absolutePath)
                .setBackend(if (useGpu) Backend.GPU else Backend.CPU)
                .build()
        } catch (e: Throwable) {
            Log.w(TAG, "Config build failed (gpu=$useGpu): ${e.message}")
            null
        }
    }
}
