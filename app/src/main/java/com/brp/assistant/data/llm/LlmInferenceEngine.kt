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
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    companion object {
        private const val TAG = "LlmInferenceEngine"
        private const val MAX_TOKENS = 1024
        private const val DEFAULT_TEMP = 0.7f
        private const val MIN_FILE_SIZE_BYTES = 50L * 1024 * 1024 
    }

    private var mediaPipeInference: LlmInference? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mutex = Mutex()

    private val _activeModelId = MutableStateFlow<String?>(null)
    val activeModelId: StateFlow<String?> = _activeModelId.asStateFlow()

    private var activeModelInfo: OfflineModelInfo? = null
    private var isInitialized = false

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
                isInitialized = true
                _activeModelId.value = model.id
                settingsRepository.setActiveModelId(model.id)
                Result.success(Unit)
            } catch (e: Throwable) {
                Log.e(TAG, "Init failed", e)
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

    fun isReady(): Boolean = isInitialized && mediaPipeInference != null
    fun getActiveModelId(): String? = activeModelInfo?.id
    fun getActivePromptStyle(): PromptStyle = activeModelInfo?.promptStyle ?: PromptStyle.CHATML

    suspend fun close() = mutex.withLock { closeInternal() }

    private fun closeInternal() {
        try { mediaPipeInference?.close() } catch (e: Throwable) {}
        mediaPipeInference = null
        isInitialized = false
        activeModelInfo = null
        _activeModelId.value = null
    }

    fun getModelFile(model: OfflineModelInfo): File {
        val baseDir  = context.getExternalFilesDir(null) ?: context.filesDir
        val modelDir = File(baseDir, "models/${model.id}")
        return File(modelDir, model.filename)
    }

    fun isModelDownloaded(model: OfflineModelInfo): Boolean {
        val file = getModelFile(model)
        return file.exists() && file.length() >= MIN_FILE_SIZE_BYTES
    }

    suspend fun deleteModel(model: OfflineModelInfo): Boolean = mutex.withLock {
        val baseDir  = context.getExternalFilesDir(null) ?: context.filesDir
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
