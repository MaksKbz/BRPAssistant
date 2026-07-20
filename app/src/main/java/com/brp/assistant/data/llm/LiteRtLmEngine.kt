package com.brp.assistant.data.llm

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
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
 *
 * API (litertlm-android 0.13.x):
 *   - Engine создаётся конструктором Engine(engineConfig) и ТРЕБУЕТ вызова
 *     engine.initialize() перед использованием (тяжёлая операция — на IO-потоке).
 *   - Диалог: engine.createConversation(ConversationConfig) → sendMessageAsync(text),
 *     возвращает Flow<Message>; текст чанка — message.toString().
 *   - systemInstruction имеет тип Contents? → оборачиваем через Contents.of(text).
 *   - Engine.close() бросает IllegalStateException, если движок не инициализирован —
 *     поэтому закрываем только после успешного initialize() (проверка isInitialized()).
 */
@Singleton
class LiteRtLmEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "LiteRtLmEngine"
        // Лимит kv-cache: ВХОД + ВЫХОД суммарно.
        // Системный промпт BRP (~900 токенов) + RAG-контекст + история + ответ
        // требуют минимум ~4000. 4096 — безопасный минимум для всех моделей.
        // Без этого модель падает: "Input token ids are too long. 1254 >= 1024".
        private const val MAX_TOKENS = 4096
    }

    private var engine: Engine? = null
    private var activeModelInfo: OfflineModelInfo? = null

    /**
     * Флаг закрытия движка.
     *
     * Проблема: при вызове closeInternal() во время активной стриминговой
     * генерации нативный Engine освобождается, но Flow ещё держит ссылку
     * на объект Conversation — следующий вызов sendMessageAsync()
     * падает с нативным крашем (SIGSEGV или JNI exception).
     *
     * Решение: @Volatile флаг isClosed проверяется в начале
     * generateResponseStreaming() и на каждом токене. При закрытии Flow
     * немедленно получает IllegalStateException вместо нативного краша.
     * После re-инициализации флаг сбрасывается в false.
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

                val eng = createAndInitEngine(modelFile, useGpu = true)
                    ?: createAndInitEngine(modelFile, useGpu = false)
                    ?: return@withContext Result.failure(
                        Exception("Не удалось инициализировать LiteRT-LM движок для ${model.title}")
                    )

                engine = eng
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
     * Создаёт и инициализирует [Engine] для заданного бэкенда.
     *
     * Engine(engineConfig) не выделяет нативных ресурсов до вызова initialize(),
     * поэтому неуспешная попытка здесь не приводит к утечке нативного handle.
     *
     * @return готовый к работе Engine или null, если конфигурация/инициализация не удалась.
     */
    private fun createAndInitEngine(modelFile: File, useGpu: Boolean): Engine? {
        return try {
            val backend: Backend = if (useGpu) Backend.GPU() else Backend.CPU()
            val config = EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = backend,
                // FIX бесконечной генерации: без maxNumTokens модели входят в цикл
                // повторов и генерируют бесконечно, забивая экран.
                maxNumTokens = MAX_TOKENS
            )
            Engine(config).also { it.initialize() }
        } catch (e: Throwable) {
            Log.w(TAG, "Engine create/init failed (gpu=$useGpu): ${e.message}")
            null
        }
    }

    /**
     * Генерирует ответ на промпт в виде потока частичных токенов.
     *
     * Проверяем isClosed перед использованием engine — это предотвращает
     * нативный краш при вызове на уже освобождённом Engine
     * (например, при смене модели во время генерации).
     */
    fun generateResponseStreaming(
        prompt: String,
        systemPrompt: String = "",
        onPartial: (String) -> Unit
    ): Flow<String> = flow {
        // Быстрая проверка до обращения к нативному объекту
        if (isClosed) throw IllegalStateException("LiteRtLmEngine был закрыт во время генерации")

        val eng = engine
            ?: throw IllegalStateException("LiteRtLmEngine не инициализирован")

        val convConfig = ConversationConfig(
            systemInstruction = if (systemPrompt.isNotBlank()) Contents.of(systemPrompt) else null,
            // SamplerConfig предотвращает повторы (repetition loop):
            // topK=40 — выбираем из 40 лучших токенов
            // topP=0.9 — nucleus sampling
            // temperature=0.7 — умеренная креативность
            samplerConfig = SamplerConfig(
                topK = 40,
                topP = 0.9,
                temperature = 0.7
            )
        )

        eng.createConversation(convConfig).use { conversation ->
            var insideThink = false
            var buffer = ""
            var tokenCount = 0
            
            conversation.sendMessageAsync(prompt).collect { message ->
                tokenCount++
                if (tokenCount > 1500) {
                    Log.w(TAG, "Generation stopped: token limit reached (1500)")
                    return@collect
                }
                if (isClosed) throw IllegalStateException("LiteRtLmEngine закрыт в процессе генерации")
                
                buffer += message.toString()
                var i = 0
                val sb = StringBuilder()
                
                while (i < buffer.length) {
                    if (!insideThink) {
                        val startIdx = buffer.indexOf("<think>", i)
                        if (startIdx != -1) {
                            sb.append(buffer.substring(i, startIdx))
                            insideThink = true
                            i = startIdx + 7
                        } else {
                            // Check if the end of buffer has a prefix of "<think>"
                            var prefixLen = 0
                            for (len in 1..6) {
                                if (buffer.length >= len) {
                                    val suffix = buffer.substring(buffer.length - len)
                                    if ("<think>".startsWith(suffix)) {
                                        prefixLen = len
                                    }
                                }
                            }
                            if (prefixLen > 0) {
                                sb.append(buffer.substring(i, buffer.length - prefixLen))
                                i = buffer.length - prefixLen
                            } else {
                                sb.append(buffer.substring(i))
                                i = buffer.length
                            }
                            break
                        }
                    } else {
                        val endIdx = buffer.indexOf("</think>", i)
                        if (endIdx != -1) {
                            insideThink = false
                            i = endIdx + 8
                        } else {
                            // Check if the end of buffer has a prefix of "</think>"
                            var prefixLen = 0
                            for (len in 1..7) {
                                if (buffer.length >= len) {
                                    val suffix = buffer.substring(buffer.length - len)
                                    if ("</think>".startsWith(suffix)) {
                                        prefixLen = len
                                    }
                                }
                            }
                            if (prefixLen > 0) {
                                i = buffer.length - prefixLen
                            } else {
                                i = buffer.length
                            }
                            break
                        }
                    }
                }
                
                buffer = buffer.substring(i)
                val tokenToEmit = sb.toString()

                if (tokenToEmit.isNotEmpty()) {
                    onPartial(tokenToEmit)
                    emit(tokenToEmit)
                }
            }
            
            if (buffer.isNotEmpty() && !insideThink) {
                onPartial(buffer)
                emit(buffer)
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
        val eng = engine
        engine = null
        activeModelInfo = null
        // Engine.close() бросает IllegalStateException, если движок не инициализирован —
        // закрываем только корректно инициализированный экземпляр.
        if (eng != null && eng.isInitialized()) {
            try {
                eng.close()
            } catch (e: Throwable) {
                Log.w(TAG, "Engine close error", e)
            }
        }
        // isClosed остаётся true до следующего успешного initialize()
    }
}
