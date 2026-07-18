package com.brp.assistant.ui.model

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brp.assistant.data.llm.CustomModelManager
import com.brp.assistant.data.llm.LlmInferenceEngine
import com.brp.assistant.data.llm.OfflineModelInfo
import com.brp.assistant.data.llm.PublicOfflineModelCatalog
import com.brp.assistant.data.llm.RemoteLlmEngine
import com.brp.assistant.data.llm.download.ModelDownloadState
import com.brp.assistant.data.llm.download.PublicHuggingFaceModelDownloader
import com.brp.assistant.domain.usecase.RecommendLlmModeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ModelManagerState(
    val downloadedModels: Set<String> = emptySet(),
    val customModels: List<OfflineModelInfo> = emptyList(),
    val availableLocalModels: List<OfflineModelInfo> = PublicOfflineModelCatalog.models,
    val activeModelId: String? = null,
    val downloadingModelId: String? = null,
    val downloadProgress: Float = 0f,
    val isActivating: Boolean = false,
    val activatingModelId: String? = null,
    val error: String? = null,
    val geminiApiKey: String? = null,
    val groqApiKey: String? = null,
    val aiProvider: String = "Gemini",
    val aiModelName: String = "gemini-1.5-flash",
    val aiSystemPrompt: String = "",
    val aiTemperature: Float = 0.7f,
    val isValidating: Boolean = false,
    val validationResult: String? = null,
    val pendingWarning: String? = null,
    val pendingModelToDownload: OfflineModelInfo? = null
)

@HiltViewModel
class ModelManagerViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val llmEngine: LlmInferenceEngine,
    private val downloader: PublicHuggingFaceModelDownloader,
    private val customModelManager: CustomModelManager,
    private val remoteLlm: RemoteLlmEngine,
    private val settingsRepository: com.brp.assistant.data.repository.SettingsRepository,
    private val recommendLlmModeUseCase: RecommendLlmModeUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(ModelManagerState())
    val state: StateFlow<ModelManagerState> = _state.asStateFlow()

    private var downloadJob: Job? = null
    private var activateJob: Job? = null
    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        refreshModels()
        viewModelScope.launch {
            llmEngine.activeModelId.collect { id ->
                _state.update { it.copy(activeModelId = id) }
            }
        }
        viewModelScope.launch {
            settingsRepository.geminiApiKey.collect { key ->
                _state.update { it.copy(geminiApiKey = key) }
            }
        }
        viewModelScope.launch {
            settingsRepository.groqApiKey.collect { key ->
                _state.update { it.copy(groqApiKey = key) }
            }
        }
        viewModelScope.launch {
            settingsRepository.aiProvider.collect { p ->
                _state.update { it.copy(aiProvider = p ?: "Gemini") }
            }
        }
        viewModelScope.launch {
            settingsRepository.aiModelName.collect { m ->
                _state.update { it.copy(aiModelName = m ?: "gemini-1.5-flash") }
            }
        }
        viewModelScope.launch {
            settingsRepository.aiSystemPrompt.collect { p ->
                _state.update { it.copy(aiSystemPrompt = p) }
            }
        }
        viewModelScope.launch {
            settingsRepository.aiTemperature.collect { t ->
                _state.update { it.copy(aiTemperature = t) }
            }
        }
    }

    fun refreshModels() {
        viewModelScope.launch {
            val custom = customModelManager.getCustomModels()
            val allCatalog = PublicOfflineModelCatalog.models + custom
            val downloaded = allCatalog.filter { llmEngine.isModelDownloaded(it) }.map { it.id }.toSet()
            val active = llmEngine.getActiveModelId()
            _state.update {
                it.copy(
                    downloadedModels = downloaded,
                    activeModelId = active,
                    customModels = custom,
                    availableLocalModels = PublicOfflineModelCatalog.models
                )
            }
        }
    }

    fun downloadModel(model: OfflineModelInfo) {
        if (_state.value.downloadingModelId != null) return

        val recommendation = recommendLlmModeUseCase.evaluate(model)
        if (!recommendation.isSafe) {
            _state.update {
                it.copy(
                    pendingWarning = recommendation.warningMessage,
                    pendingModelToDownload = model
                )
            }
            return
        }

        startDownload(model)
    }

    fun confirmUnsafeDownload() {
        val model = _state.value.pendingModelToDownload ?: return
        _state.update {
            it.copy(
                pendingWarning = null,
                pendingModelToDownload = null
            )
        }
        startDownload(model)
    }

    fun dismissPendingWarning() {
        _state.update {
            it.copy(
                pendingWarning = null,
                pendingModelToDownload = null
            )
        }
    }

    private fun startDownload(model: OfflineModelInfo) {
        if (_state.value.downloadingModelId != null) return
        downloadJob?.cancel()
        downloadJob = downloadScope.launch {
            val powerManager = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
            val wakeLock = powerManager.newWakeLock(
                android.os.PowerManager.PARTIAL_WAKE_LOCK,
                "BRPAssistant::ModelDownload"
            ).apply { acquire(30 * 60 * 1000L) }

            try {
                _state.update {
                    it.copy(
                        downloadingModelId = model.id,
                        error = null,
                        downloadProgress = 0f,
                        pendingWarning = null,
                        pendingModelToDownload = null
                    )
                }
                downloader.downloadModel(model).collect { downloadState ->
                    when (downloadState) {
                        is ModelDownloadState.Progress -> {
                            val progress = (downloadState.percent ?: 0).toFloat() / 100f
                            _state.update { it.copy(downloadProgress = progress) }
                        }
                        is ModelDownloadState.Success -> {
                            _state.update { it.copy(downloadingModelId = null, downloadProgress = 0f) }
                            kotlinx.coroutines.delay(500)
                            refreshModels()
                        }
                        is ModelDownloadState.Error -> {
                            _state.update { it.copy(downloadingModelId = null, error = downloadState.message) }
                        }
                    }
                }
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
            }
        }
    }

    fun activateModel(model: OfflineModelInfo) {
        if (activateJob?.isActive == true) return
        activateJob = viewModelScope.launch {
            _state.update { it.copy(isActivating = true, activatingModelId = model.id, error = null) }
            val result = llmEngine.initialize(model)
            if (result.isSuccess) {
                _state.update { it.copy(isActivating = false, activatingModelId = null) }
                refreshModels()
            } else {
                _state.update {
                    it.copy(
                        isActivating = false,
                        activatingModelId = null,
                        error = result.exceptionOrNull()?.message ?: "Ошибка активации"
                    )
                }
            }
        }
    }

    fun deleteModel(model: OfflineModelInfo) {
        viewModelScope.launch {
            if (llmEngine.deleteModel(model).getOrDefault(false)) {
                if (model.isCustom) customModelManager.removeCustomModel(model.id)
                refreshModels()
            }
        }
    }

    fun addCustomModelFromFile(uri: Uri, fileName: String) {
        viewModelScope.launch {
            val result = customModelManager.importFile(uri, fileName)
            if (result.isSuccess) refreshModels()
            else _state.update { it.copy(error = "Ошибка импорта: ${result.exceptionOrNull()?.message}") }
        }
    }

    fun addCustomModelFromUrl(title: String, url: String) {
        viewModelScope.launch {
            try {
                customModelManager.addExternalUrl(title, url)
                refreshModels()
            } catch (e: Exception) {
                _state.update { it.copy(error = "Ошибка добавления URL: ${e.message}") }
            }
        }
    }

    fun updateApiKey(key: String) {
        val trimmedKey = key.trim()
        viewModelScope.launch {
            _state.update { it.copy(isValidating = true, validationResult = null) }
            val result = remoteLlm.validateKey(_state.value.aiProvider, trimmedKey, _state.value.aiModelName)
            if (result.isSuccess) {
                if (_state.value.aiProvider == "Gemini") settingsRepository.setGeminiApiKey(trimmedKey)
                else settingsRepository.setGroqApiKey(trimmedKey)
                _state.update { it.copy(isValidating = false, validationResult = "SUCCESS") }
            } else {
                _state.update { it.copy(isValidating = false, validationResult = result.exceptionOrNull()?.message ?: "ERROR") }
            }
        }
    }

    fun updateAiProvider(provider: String) {
        viewModelScope.launch {
            settingsRepository.setAiProvider(provider)
            val defaultModel = if (provider == "Gemini") "gemini-1.5-flash" else "llama-3.3-70b-versatile"
            settingsRepository.setAiModelName(defaultModel)
            _state.update { it.copy(validationResult = null) }
        }
    }

    fun updateAiModel(model: String) {
        viewModelScope.launch { settingsRepository.setAiModelName(model) }
    }

    fun updateSystemPrompt(prompt: String) {
        viewModelScope.launch { settingsRepository.setAiSystemPrompt(prompt) }
    }

    fun updateTemperature(temp: Float) {
        viewModelScope.launch { settingsRepository.setAiTemperature(temp) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
