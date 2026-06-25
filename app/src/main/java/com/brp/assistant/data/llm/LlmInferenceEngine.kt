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
 * Simplified Local LLM engine using MediaPipe.
 */
@Singleton
class LlmInferenceEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    companion object {
        private const val TAG = "LlmInferenceEngine"
        private const val MAX_TOKENS = 1024
        private const val DEFAULT_TEMP = 0.7f
        // FIX #9: базовый порог 10MB; реальный порог берётся из OfflineModelInfo.approxSizeMb
        private const val FALLBACK_MIN_FILE_SIZE_BYTES = 10L * 1024 * 1024
    }

    private var mediaPipeInference: LlmInference? = null

    /**
     * FIX #5: scope хранится в val для возможности отмены через destroy().
     * SupervisorJob позволяет дочерним корутинам падать независимо,
     * не отменяя весь scope.
     */
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

    suspend fun initialize(model: OfflineModelInfo): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                closeInternal()
                val modelFile = getModelFile(model)
                if (!modelFile.exists()) return@withContext Result.failure(Exception("File not found"))

                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelFile.absolutePath)
                    .setMaxTokens(MAX_TOKENS)
                    .setTemperature(DEFAULT_TEMP)
                    .build()

                mediaPipeInference = LlmInference.createFromOptions(context, options)
                activeModelInfo = model
                _isInitialized.value = true
                _activeModelId.value = model.id
                settingsRepository.setActiveModelId(model.id)
                Result.success(Unit)
            } catch (e: Throwable) {
                Log.e(TAG, "Init failed", e)
                _isInitialized.value = false
                Result.failure(e)
            }
        }
    }

    suspend fun generateResponse(prompt: String, onPartial: (String) -> Unit): Result<String> {
        val inference = mediaPipeInference ?: return Result.failure(Exception("Not ready"))
        return withContext(Dispatchers.IO) {
            try {
                val response = inference.generateResponse(prompt)
                withContext(Dispatchers.Main) { onPartial(response) }
                Result.success(response)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    fun isReady(): Boolean = _isInitialized.value && mediaPipeInference != null
    fun getActiveModelId(): String? = activeModelInfo?.id
    fun getActivePromptStyle(): PromptStyle = activeModelInfo?.promptStyle ?: PromptStyle.CHATML

    suspend fun close() = mutex.withLock { closeInternal() }

    /**
     * FIX #5: destroy() отменяет CoroutineScope и освобождает все ресурсы.
     * Вызывать при уничтожении компонента (например, в ViewModel.onCleared())
     * или при смене модели, чтобы избежать утечки памяти через незавершённые
     * корутины, держащие ссылку на context.
     */
    fun destroy() {
        scope.cancel()
        try { mediaPipeInference?.close() } catch (e: Throwable) {}
        mediaPipeInference = null
        _isInitialized.value = false
        activeModelInfo = null
        _activeModelId.value = null
    }

    private fun closeInternal() {
        try { mediaPipeInference?.close() } catch (e: Throwable) {}
        mediaPipeInference = null
        _isInitialized.value = false
        activeModelInfo = null
        _activeModelId.value = null
    }

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
