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
 *                             Зависимость: com.google.mediapipe:tasks-genai
 *
 *   [ModelFormat.LITERTLM]  → LiteRtLmEngine
 *                             Файлы: *.litertlm
 *                             Зависимость: com.google.ai.edge.litertlm:litertlm-android
 *                             Даёт доступ к Qwen3, Gemma4 и другим новым моделям
 *                             с поддержкой NPU, Tool Use, мультимодальности.
 *
 * Внешний код (ViewModel, UseCase) работает с этим классом напрямую —
 * детали выбора движка полностью скрыты.
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

        /** Все поддерживаемые расширения — оба движка */
        private val SUPPORTED_EXTENSIONS = setOf("task", "tflite", "litertlm")
    }

    // ── MediaPipe движок (TASK) ──────────────────────────────────────────────
    private var mediaPipeInference: LlmInference? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mutex = Mutex()

    private val _activeModelId = MutableStateFlow<String?>(null)
    val activeModelId: StateFlow<String?> = _activeModelId.asStateFlow()

    private var activeModelInfo: OfflineModelInfo? = null

    private val _isInitialized = MutableStateFlow(false)
    val isInitializedFlow: StateFlow<Boolean> = _isInitialized.asStateFlow()

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

                // ── Роутинг по формату ─────────────────────────────────
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

    // ── Генерация ответа ────────────────────────────────────────────────

    /**
     * Генерирует ответ, прозрачно делегируя активному движку.
     * Системный промпт передаётся в LiteRT-LM нативно;
     * MediaPipe получает его как часть уже построенного [prompt].
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
                        val response = inference.generateResponse(prompt)
                        withContext(Dispatchers.Main) { onPartial(response) }
                        Result.success(response)
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

    fun getActivePromptStyle(): PromptStyle =
        activeModelInfo?.promptStyle ?: PromptStyle.CHATML

    // ── Жизненный цикл ──────────────────────────────────────────────────

    suspend fun close() = mutex.withLock { closeInternal() }

    /**
     * FIX #3: раньше destroy() вызывал closeInternal() напрямую, без mutex,
     * что приводило к race condition если одновременно выполнялся initialize().
     * Теперь: scope.cancel() прекращает все корутины, затем
     * runBlocking блокирует поток владельца до получения mutex.
     */
    fun destroy() {
        scope.cancel()
        @Suppress("BlockingMethodInNonBlockingContext")
        runBlocking {
            mutex.withLock { closeInternal() }
        }
        liteRtLmEngine.close()
    }

    private fun closeInternal() {
        try { mediaPipeInference?.close() } catch (e: Throwable) {}
        mediaPipeInference = null
        liteRtLmEngine.close()
        _isInitialized.value = false
        activeModelInfo = null
        _activeModelId.value = null
    }

    // ── Файловые утилиты ──────────────────────────────────────────────────

    /**
     * FIX #3: унифицирован путь модели.
     * Раньше: getExternalFilesDir(null) использовался здесь,
     * а PublicHuggingFaceModelDownloader также сохранял в getExternalFilesDir(null).
     * Оба класса теперь единообразно используют getExternalFilesDir(null) ?: filesDir.
     */
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
