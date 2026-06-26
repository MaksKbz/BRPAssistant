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
 *                             Даёт доступ к Qwen3, Gemma4 и другим новым моделям.
 *
 * Внешний код (ViewModel, UseCase) работает с этим классом напрямую —
 * детали выбора движка полностью скрыты.
 *
 * Защиты от отказов:
 * - RamGuard проверяет доступную RAM до инициализации;
 * - EngineResult.runSafe() перехватывает OutOfMemoryError и Throwable;
 * - Mutex исключает параллельный init/close;
 * - closeInternal() вызывается при любой ошибке инита, чтобы не оставлять движок
 *   в неопределённом состоянии.
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

        private val SUPPORTED_EXTENSIONS = setOf("task", "tflite", "litertlm")
    }

    private var mediaPipeInference: LlmInference? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mutex = Mutex()

    private val _activeModelId = MutableStateFlow<String?>(null)
    val activeModelId: StateFlow<String?> = _activeModelId.asStateFlow()

    private var activeModelInfo: OfflineModelInfo? = null

    private val _isInitialized = MutableStateFlow(false)
    val isInitializedFlow: StateFlow<Boolean> = _isInitialized.asStateFlow()

    /** Последний результат инициализации — для UI (предупреждения о RAM и т.д.) */
    private val _lastEngineResult = MutableStateFlow<EngineResult<Unit>?>(null)
    val lastEngineResult: StateFlow<EngineResult<Unit>?> = _lastEngineResult.asStateFlow()

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

    // ── Инициализация ───────────────────────────────────────────────────

    suspend fun initialize(model: OfflineModelInfo): EngineResult<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            closeInternal()

            val modelFile = getModelFile(model)
            if (!modelFile.exists()) {
                val err = EngineResult.EngineError(
                    cause = Exception("Файл модели не найден: ${modelFile.absolutePath}")
                )
                _lastEngineResult.value = err
                return@withContext err
            }

            val ext = modelFile.extension.lowercase()
            if (ext !in SUPPORTED_EXTENSIONS) {
                val err = EngineResult.EngineError(
                    cause = Exception(
                        "Неподдерживаемый формат: .${ext}. Поддерживаются: .task, .tflite, .litertlm"
                    )
                )
                _lastEngineResult.value = err
                return@withContext err
            }

            // ── Проверка RAM ───────────────────────────────────────────
            val availableMb = RamGuard.availableRamMb(context)
            when (val ramCheck = RamGuard.check(context, model.approxSizeMb)) {
                is RamGuard.RamCheckResult.Critical -> {
                    Log.e(TAG, "RAM check CRITICAL: ${ramCheck.errorMessage}")
                    val err = EngineResult.EngineError(
                        cause = Exception(ramCheck.errorMessage)
                    )
                    _lastEngineResult.value = err
                    return@withContext err
                }
                is RamGuard.RamCheckResult.Low -> {
                    Log.w(TAG, "RAM check LOW: ${ramCheck.warningMessage}")
                    // Не блокируем, но предупреждаем через lastEngineResult
                }
                is RamGuard.RamCheckResult.Ok -> {
                    Log.d(TAG, "RAM check OK: available=${ramCheck.availableMb}MB")
                }
            }

            // ── Роутинг по формату с OOM-защитой ─────────────────────
            val result: EngineResult<Unit> = when (model.format) {
                ModelFormat.LITERTLM -> {
                    Log.i(TAG, "Routing to LiteRtLmEngine: ${model.title}")
                    EngineResult.runSafe(model.approxSizeMb, availableMb) {
                        liteRtLmEngine.initialize(model, modelFile).getOrThrow()
                    }
                }
                ModelFormat.TASK -> {
                    Log.i(TAG, "Routing to MediaPipe: ${model.title}")
                    initMediaPipeSafe(modelFile, model.approxSizeMb, availableMb)
                }
            }

            _lastEngineResult.value = result

            if (result.isSuccess) {
                activeModelInfo = model
                _isInitialized.value = true
                _activeModelId.value = model.id
                settingsRepository.setActiveModelId(model.id)
                Log.i(TAG, "Engine initialized: ${model.title}")
            } else {
                closeInternal()
                Log.e(TAG, "Engine init failed: $result")
            }

            result
        }
    }

    /**
     * Инициализация MediaPipe с полным перехватом OOM и Throwable.
     * [OutOfMemoryError] превращается в [EngineResult.OomError] для UI.
     */
    private fun initMediaPipeSafe(
        modelFile: File,
        modelSizeMb: Int,
        availableMb: Long
    ): EngineResult<Unit> = EngineResult.runSafe(modelSizeMb, availableMb) {
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelFile.absolutePath)
            .setMaxTokens(MAX_TOKENS)
            .setTemperature(DEFAULT_TEMP)
            .build()
        mediaPipeInference = LlmInference.createFromOptions(context, options)
    }

    // ── Генерация ответа ────────────────────────────────────────────────

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
                        val response = inference.generateResponse(prompt)
                        withContext(Dispatchers.Main) { onPartial(response) }
                        Result.success(response)
                    } catch (oom: OutOfMemoryError) {
                        Log.e(TAG, "OOM during inference", oom)
                        Result.failure(Exception(
                            "Нехватка памяти во время генерации ответа. " +
                            "Попробуйте выбрать модель меньшего размера."
                        ))
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                }
            }
        }
    }

    // ── Состояние ───────────────────────────────────────────────────────────

    fun isReady(): Boolean {
        return _isInitialized.value && when (activeModelInfo?.format) {
            ModelFormat.LITERTLM -> liteRtLmEngine.isReady()
            else                 -> mediaPipeInference != null
        }
    }

    fun getActiveModelId(): String? = activeModelInfo?.id
    fun getActivePromptStyle(): PromptStyle = activeModelInfo?.promptStyle ?: PromptStyle.CHATML

    // ── Жизненный цикл ──────────────────────────────────────────────────

    suspend fun close() = mutex.withLock { closeInternal() }

    fun destroy() {
        scope.cancel()
        @Suppress("BlockingMethodInNonBlockingContext")
        runBlocking {
            mutex.withLock { closeInternal() }
        }
        liteRtLmEngine.close()
    }

    private fun closeInternal() {
        try { mediaPipeInference?.close() } catch (e: Throwable) { Log.w(TAG, "Error closing MediaPipe", e) }
        mediaPipeInference = null
        try { liteRtLmEngine.close() } catch (e: Throwable) { Log.w(TAG, "Error closing LiteRtLm", e) }
        _isInitialized.value = false
        activeModelInfo = null
        _activeModelId.value = null
    }

    // ── Файловые утилиты ──────────────────────────────────────────────────

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

    private suspend fun loadCustomModels(): List<OfflineModelInfo> = try {
        val json = settingsRepository.customModelsJson.first() ?: ""
        if (json.isNotEmpty())
            kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                .decodeFromString<List<OfflineModelInfo>>(json)
        else emptyList()
    } catch (e: Exception) { emptyList() }
}
