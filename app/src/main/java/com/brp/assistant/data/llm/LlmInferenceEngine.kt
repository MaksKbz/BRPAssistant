package com.brp.assistant.data.llm

import android.content.Context
import android.util.Log
import com.brp.assistant.data.repository.SettingsRepository
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Единая точка входа для локального LLM-вывода.
 *
 * Роутит запросы между двумя движками по полю [OfflineModelInfo.format]:
 *
 *   [ModelFormat.TASK]      → MediaPipe LlmInference API
 *                             Файлы: *.task, *.tflite
 *
 *   [ModelFormat.LITERTLM]  → LiteRtLmEngine
 *                             Файлы: *.litertlm
 *                             NPU, Tool Use, Qwen3, Gemma4
 */
@Singleton
class LlmInferenceEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val liteRtLmEngine: LiteRtLmEngine
) {
    companion object {
        private const val TAG = "LlmInferenceEngine"
        private const val MAX_TOKENS = 1024
        private const val DEFAULT_TEMP = 0.7f
        private const val FALLBACK_MIN_FILE_SIZE_BYTES = 10L * 1024 * 1024

        /** FIX #1: таймаут MediaPipe — 120 секунд, константа для удобной настройки */
        private const val GENERATION_TIMEOUT_MS = 120_000L

        private val SUPPORTED_EXTENSIONS = setOf("task", "tflite", "litertlm")
    }

    // ── MediaPipe движок (TASK) ──────────────────────────────────────────────
    private var mediaPipeInference: LlmInference? = null

    /**
     * FIX HIGH-1: guard-флаг против двойного close().
     *
     * Проблема: closeInternal() вызывается каждый раз внутри initialize() перед
     * созданием нового движка. При этом liteRtLmEngine.close() вызывался даже если
     * LiteRT-движок никогда не был активен — это no-op в норме, но при изменении
     * порядка вызовов (например, destroy() → initialize()) мог привести к двойному
     * закрытию нативного ресурса и крэшу на JNI-уровне (SIGABRT).
     *
     * Решение: флаг isEngineClosed, который:
     * - выставляется в true при каждом closeInternal()
     * - проверяется перед liteRtLmEngine.close() — пропускаем повторный вызов
     * - сбрасывается в false при успешной инициализации нового движка
     */
    @Volatile private var isEngineClosed: Boolean = true

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mutex = Mutex()

    private val _activeModelId = MutableStateFlow<String?>(null)
    val activeModelId: StateFlow<String?> = _activeModelId.asStateFlow()

    private var activeModelInfo: OfflineModelInfo? = null

    private val _isInitialized = MutableStateFlow(false)
    val isInitializedFlow: StateFlow<Boolean> = _isInitialized.asStateFlow()

    /**
     * FIX HIGH-2: публичный поток ошибок загрузки кастомных моделей.
     *
     * Проблема: loadCustomModels() поглощал все Exception молча (catch { emptyList() }).
     * Пользователь не узнавал, что его кастомная модель не загрузилась из-за
     * невалидного JSON — приложение просто показывало пустой список без объяснений.
     *
     * Решение: при ошибке парсинга — сообщение пишется в _customModelError.
     * ModelManagerViewModel подписывается на customModelError и показывает
     * Snackbar/Banner с конкретным текстом. clearCustomModelError() вызывается
     * после того как пользователь увидел ошибку.
     */
    private val _customModelError = MutableStateFlow<String?>(null)
    val customModelError: StateFlow<String?> = _customModelError.asStateFlow()

    fun clearCustomModelError() { _customModelError.value = null }

    init {
        scope.launch {
            val savedId = settingsRepository.activeModelId.first() ?: return@launch
            val customModels = loadCustomModels()
            val model = customModels.find { it.id == savedId }
                ?: PublicOfflineModelCatalog.getById(savedId)
                ?: return@launch
            if (isModelDownloaded(model)) {
                initialize(model)
            }
        }
    }

    // ── Инициализация ────────────────────────────────────────────────────────

    suspend fun initialize(model: OfflineModelInfo): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                closeInternal()
                val modelFile = getModelFile(model)

                if (!modelFile.exists()) {
                    return@withContext Result.failure(
                        Exception("Файл модели не найден: ${modelFile.absolutePath}")
                    )
                }

                val ext = modelFile.extension.lowercase()
                if (ext !in SUPPORTED_EXTENSIONS) {
                    return@withContext Result.failure(
                        Exception(
                            "Неподдерживаемый формат модели: .${ext}\n" +
                            "Поддерживаются: .task, .tflite, .litertlm"
                        )
                    )
                }

                val result = when (model.format) {
                    ModelFormat.LITERTLM -> {
                        Log.i(TAG, "Routing to LiteRtLmEngine: ${model.title}")
                        liteRtLmEngine.initialize(model, modelFile)
                    }
                    ModelFormat.TASK -> {
                        Log.i(TAG, "Routing to MediaPipe: ${model.title}")
                        initMediaPipe(modelFile)
                    }
                }

                if (result.isSuccess) {
                    activeModelInfo = model
                    _isInitialized.value = true
                    _activeModelId.value = model.id
                    // FIX HIGH-1: сбрасываем флаг — движок успешно открыт
                    isEngineClosed = false
                    settingsRepository.setActiveModelId(model.id)
                } else {
                    _isInitialized.value = false
                }
                result

            } catch (e: Throwable) {
                Log.e(TAG, "Init failed", e)
                _isInitialized.value = false
                Result.failure(e)
            }
        }
    }

    private fun initMediaPipe(modelFile: File): Result<Unit> {
        return try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(MAX_TOKENS)
                .setTemperature(DEFAULT_TEMP)
                .build()
            mediaPipeInference = LlmInference.createFromOptions(context, options)
            Result.success(Unit)
        } catch (e: Throwable) {
            Log.e(TAG, "MediaPipe init failed", e)
            Result.failure(e)
        }
    }

    // ── Генерация ответа ─────────────────────────────────────────────────────

    /**
     * FIX #1 (из PR #2): withTimeout(GENERATION_TIMEOUT_MS) + isActive-check.
     * FIX #catch-throwable: catch (e: Throwable) перехватывает OutOfMemoryError.
     */
    suspend fun generateResponse(
        prompt: String,
        onPartial: (String) -> Unit,
        systemPrompt: String = ""
    ): Result<String> {
        return when (activeModelInfo?.format) {
            ModelFormat.LITERTLM -> {
                liteRtLmEngine.generateResponse(prompt, systemPrompt, onPartial)
            }
            ModelFormat.TASK, null -> {
                val inference = mediaPipeInference
                    ?: return Result.failure(Exception("MediaPipe не инициализирован"))
                withContext(Dispatchers.IO) {
                    try {
                        val response = withTimeout(GENERATION_TIMEOUT_MS) {
                            inference.generateResponse(prompt)
                        }
                        if (!isActive) return@withContext Result.failure(
                            CancellationException("Генерация отменена")
                        )
                        withContext(Dispatchers.Main) { onPartial(response) }
                        Result.success(response)
                    } catch (e: TimeoutCancellationException) {
                        val msg = "⏱ Превышено время ожидания ответа (${GENERATION_TIMEOUT_MS / 1000}с). " +
                            "Попробуйте более короткий вопрос или лёгкую модель."
                        Log.w(TAG, "MediaPipe generation timed out after ${GENERATION_TIMEOUT_MS}ms")
                        Result.failure(RuntimeException(msg, e))
                    } catch (e: Throwable) {
                        val userMsg = when (e) {
                            is OutOfMemoryError ->
                                "Недостаточно памяти для генерации ответа. " +
                                "Попробуйте более лёгкую модель или освободите RAM."
                            else -> e.message ?: "Ошибка генерации"
                        }
                        Log.e(TAG, "generateResponse failed: $userMsg", e)
                        Result.failure(RuntimeException(userMsg, e))
                    }
                }
            }
        }
    }

    // ── Состояние ────────────────────────────────────────────────────────────

    fun isReady(): Boolean {
        return _isInitialized.value && when (activeModelInfo?.format) {
            ModelFormat.LITERTLM -> liteRtLmEngine.isReady()
            else                 -> mediaPipeInference != null
        }
    }

    fun getActiveModelId(): String? = activeModelInfo?.id

    fun getActivePromptStyle(): PromptStyle =
        activeModelInfo?.promptStyle ?: PromptStyle.CHATML

    // ── Жизненный цикл ───────────────────────────────────────────────────────

    suspend fun close() = mutex.withLock { closeInternal() }

    fun destroy() {
        scope.cancel()
        @Suppress("BlockingMethodInNonBlockingContext")
        runBlocking {
            mutex.withLock { closeInternal() }
        }
    }

    /**
     * FIX HIGH-1: guard против двойного close().
     *
     * Раньше: каждый вызов closeInternal() безусловно звал liteRtLmEngine.close(),
     * даже если LiteRT никогда не был активен или уже был закрыт.
     * Это могло вызвать двойное освобождение нативного ресурса (SIGABRT в JNI).
     *
     * Теперь: isEngineClosed == true → пропускаем liteRtLmEngine.close(),
     * только mediaPipeInference?.close() всегда безопасен (null-safe).
     * После закрытия выставляем isEngineClosed = true для следующего цикла.
     */
    private fun closeInternal() {
        try { mediaPipeInference?.close() } catch (e: Throwable) {
            Log.w(TAG, "mediaPipeInference.close() threw: ${e.message}")
        }
        mediaPipeInference = null

        // Закрываем LiteRT только если он был открыт (guard против double-close)
        if (!isEngineClosed) {
            try { liteRtLmEngine.close() } catch (e: Throwable) {
                Log.w(TAG, "liteRtLmEngine.close() threw: ${e.message}")
            }
        }
        isEngineClosed = true

        _isInitialized.value = false
        activeModelInfo = null
        _activeModelId.value = null
    }

    // ── Файловые утилиты ─────────────────────────────────────────────────────

    fun getModelFile(model: OfflineModelInfo): File {
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        val modelDir = File(baseDir, "models/${model.id}")
        return File(modelDir, model.filename)
    }

    fun isModelDownloaded(model: OfflineModelInfo): Boolean {
        val file = getModelFile(model)
        val minSizeBytes = maxOf(
            FALLBACK_MIN_FILE_SIZE_BYTES,
            (model.approxSizeMb * 1024L * 1024L * 0.8).toLong()
        )
        return file.exists() && file.length() >= minSizeBytes
    }

    suspend fun deleteModel(model: OfflineModelInfo): Boolean = mutex.withLock {
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        val modelDir = File(baseDir, "models/${model.id}")
        if (model.id == activeModelInfo?.id) closeInternal()
        modelDir.deleteRecursively()
    }

    fun getDownloadedModels(): List<OfflineModelInfo> {
        return PublicOfflineModelCatalog.models.filter { isModelDownloaded(it) }
    }

    /**
     * FIX HIGH-2: ошибки парсинга JSON кастомных моделей теперь поверхностны.
     *
     * Раньше: catch (e: Exception) { emptyList() } — ошибка терялась, пользователь
     * видел пустой список без объяснений.
     *
     * Теперь: ошибка пишется в _customModelError с понятным сообщением.
     * UI-слой (ModelManagerViewModel) подписывается на customModelError и
     * показывает Snackbar/Banner с конкретным текстом проблемы.
     */
    private suspend fun loadCustomModels(): List<OfflineModelInfo> = try {
        val json = settingsRepository.customModelsJson.first() ?: ""
        if (json.isNotEmpty())
            kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                .decodeFromString<List<OfflineModelInfo>>(json)
        else emptyList()
    } catch (e: Exception) {
        val msg = "Не удалось загрузить кастомные модели: ${e.message ?: "ошибка парсинга JSON"}.\n" +
                  "Проверьте формат файла модели или добавьте модель заново."
        Log.e(TAG, "loadCustomModels failed", e)
        _customModelError.value = msg
        emptyList()
    }
}
