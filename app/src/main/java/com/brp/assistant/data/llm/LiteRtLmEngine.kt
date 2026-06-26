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
     * FIX #3: флаг закрытия движка.
     *
     * Проблема: при вызове closeInternal() во время активной стриминговой
     * генерации нативный Engine освобождается, но Flow ещё держит ссылку
     * на объект Conversation — следующий вызов conversation.generateResponse()
     * падает с нативным крашем (SIGSEGV или JNI exception).
     *
     * Решение: @Volatile флаг isClosed проверяется в начале
     * generateResponseStreaming(). При закрытии Flow немедленно получает
     * IllegalStateException вместо нативного краша. После re-инициализации
     * флаг сбрасывается в false.
     */
    @Volatile
    private var isClosed = false

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

                val engineConfig = tryBuildConfig(modelFile, useGpu = true)
                    ?: tryBuildConfig(modelFile, useGpu = false)
                    ?: return@withContext Result.failure(
                        Exception("Не удалось создать конфигурацию LiteRT-LM движка")
                    )

                engine = Engine.create(engineConfig)
                activeModelInfo = model
                isClosed = false   // сбрасываем флаг после успешной инициализации
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
     *
     * FIX #3: проверяем isClosed перед использованием engine.
     * Это предотвращает нативный краш при вызове generate() на
     * уже освобождённом Engine (например, при смене модели во время генерации).
     */
    fun generateResponseStreaming(
        prompt: String,
        systemPrompt: String = "",
        onPartial: (String) -> Unit
    ): Flow<String> = flow {
        // FIX #3: быстрая проверка до обращения к нативному объекту
        if (isClosed) throw IllegalStateException("LiteRtLmEngine был закрыт во время генерации")

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
                // Проверяем флаг на каждом токене — closeInternal() мог быть
                // вызван уже после старта генерации
                if (isClosed) throw IllegalStateException("LiteRtLmEngine закрыт в процессе генерации")
                onPartial(token)
                emit(token)
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Блокирующий вариант генерации — собирает полный ответ.
     *
     * catch (e: Exception) → catch (e: Throwable):
     * OutOfMemoryError во время нативного инференса является Error,
     * а не Exception — ранее не перехватывался, что приводило к крэшу
     * на устройствах с 3 ГБ RAM при запуске Qwen3-1.7B и выше.
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
        } catch (e: Throwable) {
            val userMsg = when (e) {
                is OutOfMemoryError ->
                    "Недостаточно памяти для генерации ответа. " +
                    "Попробуйте более лёгкую модель (Qwen3-0.6B) или перезапустите приложение."
                else -> e.message ?: "Ошибка генерации"
            }
            Log.e(TAG, "generateResponse failed: $userMsg", e)
            Result.failure(RuntimeException(userMsg, e))
        }
    }

    fun isReady(): Boolean = engine != null && activeModelInfo != null && !isClosed

    fun getActiveModelInfo(): OfflineModelInfo? = activeModelInfo

    fun getActivePromptStyle(): PromptStyle =
        activeModelInfo?.promptStyle ?: PromptStyle.CHATML

    fun close() = closeInternal()

    private fun closeInternal() {
        isClosed = true   // выставляем флаг ДО освобождения объекта
        try { engine?.close() } catch (e: Throwable) { Log.w(TAG, "close error", e) }
        engine = null
        activeModelInfo = null
        // isClosed остаётся true до следующего успешного initialize()
    }

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
