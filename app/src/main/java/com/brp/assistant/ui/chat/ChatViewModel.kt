package com.brp.assistant.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brp.assistant.data.llm.LlmInferenceEngine
import com.brp.assistant.data.llm.OfflineModelInfo
import com.brp.assistant.data.llm.PublicOfflineModelCatalog
import com.brp.assistant.data.repository.SettingsRepository
import com.brp.assistant.domain.model.ChatMessage
import com.brp.assistant.domain.model.MessageRole
import com.brp.assistant.domain.model.RetrievalMode
import com.brp.assistant.domain.usecase.ChatUseCase
import com.brp.assistant.domain.usecase.DiagnoseUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Дополнительный wrapper для отображения статуса загрузки в UI-шторке. */
data class OfflineModelUiItem(
    val model: OfflineModelInfo,
    val isDownloaded: Boolean
)

data class ChatState(
    val messages: List<ChatMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val riskLevel: String = "low",
    val requiresEvacuation: Boolean = false,
    val error: String? = null,
    val isModelReady: Boolean = false,
    val currentVehicleId: String? = null,
    val currentMode: String? = null,
    // Выбранная LLM для этого чата (null = использовать глобальный провайдер)
    val selectedLlmModelId: String? = null,
    val selectedOnlineProvider: String? = null,
    // FIX: храним ВСЕ модели каталога (а не только загруженные)
    val allOfflineModels: List<OfflineModelUiItem> = emptyList(),
    val activeOfflineModelId: String? = null,
    val currentOnlineProvider: String = "Gemini"
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatUseCase: ChatUseCase,
    private val diagnoseUseCase: DiagnoseUseCase,
    private val llmEngine: LlmInferenceEngine,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                llmEngine.activeModelId,
                settingsRepository.geminiApiKey,
                settingsRepository.groqApiKey,
                settingsRepository.aiProvider
            ) { modelId, geminiKey, groqKey, provider ->
                val hasApiKey = if (provider == "Gemini")
                    !geminiKey.isNullOrBlank()
                else
                    !groqKey.isNullOrBlank()
                modelId != null || hasApiKey
            }.collect { ready ->
                _state.update { it.copy(isModelReady = ready) }
            }
        }

        // FIX: показываем ВСЕ модели каталога, с флагом isDownloaded для каждой
        viewModelScope.launch {
            llmEngine.activeModelId.collect { id ->
                val items = PublicOfflineModelCatalog.models.map { model ->
                    OfflineModelUiItem(
                        model = model,
                        isDownloaded = llmEngine.isModelDownloaded(model)
                    )
                }
                _state.update { s ->
                    s.copy(
                        activeOfflineModelId = id,
                        allOfflineModels = items
                    )
                }
            }
        }

        viewModelScope.launch {
            settingsRepository.aiProvider.collect { p ->
                _state.update { it.copy(currentOnlineProvider = p ?: "Gemini") }
            }
        }
    }

    fun clearForChat(vehicleId: String?, mode: String) {
        val current = _state.value
        val contextChanged = current.currentVehicleId != vehicleId || current.currentMode != mode
        if (contextChanged) {
            _state.update {
                it.copy(
                    messages = emptyList(),
                    isGenerating = false,
                    riskLevel = "low",
                    requiresEvacuation = false,
                    error = null,
                    currentVehicleId = vehicleId,
                    currentMode = mode
                )
            }
        }
    }

    fun selectOfflineLlm(modelId: String?) {
        _state.update { it.copy(selectedLlmModelId = modelId, selectedOnlineProvider = null) }
    }

    fun selectOnlineLlm(provider: String) {
        _state.update { it.copy(selectedOnlineProvider = provider, selectedLlmModelId = null) }
    }

    fun resetLlmSelection() {
        _state.update { it.copy(selectedLlmModelId = null, selectedOnlineProvider = null) }
    }

    fun sendMessage(text: String, mode: String, modelId: String?) {
        val userMsg = ChatMessage(text, MessageRole.USER)
        _state.update { it.copy(messages = it.messages + userMsg, isGenerating = true, error = null) }

        val effectiveModelId: String? = when {
            _state.value.selectedLlmModelId != null -> _state.value.selectedLlmModelId
            else -> modelId
        }

        viewModelScope.launch {
            val history = _state.value.messages
            var assistantContent = ""
            val assistantMsg = ChatMessage("", MessageRole.ASSISTANT)
            _state.update { it.copy(messages = it.messages + assistantMsg) }

            if (mode == "diagnosis") {
                diagnoseUseCase(text, effectiveModelId, history) { partial ->
                    assistantContent += partial
                    updateLastMessage(assistantContent)
                }.collect { result ->
                    result.onSuccess { diag ->
                        _state.update {
                            it.copy(
                                isGenerating = false,
                                riskLevel = diag.riskLevel,
                                requiresEvacuation = diag.requiresEvacuation
                            )
                        }
                    }.onFailure { err ->
                        _state.update { it.copy(isGenerating = false, error = err.message) }
                        updateLastMessage("❌ Ошибка: ${err.message ?: "Неизвестная ошибка"}")
                    }
                }
            } else {
                val retrievalMode = when (mode) {
                    "accessory" -> RetrievalMode.ACCESSORY
                    else -> RetrievalMode.BOTH
                }
                val result = chatUseCase(text, retrievalMode, effectiveModelId, history) { partial ->
                    assistantContent += partial
                    updateLastMessage(assistantContent)
                }
                _state.update { it.copy(isGenerating = false) }
                if (result.isFailure) {
                    val errMsg = result.exceptionOrNull()?.message ?: "Неизвестная ошибка"
                    _state.update { it.copy(error = errMsg) }
                    updateLastMessage("❌ Ошибка подключения: $errMsg")
                }
            }
        }
    }

    private fun updateLastMessage(content: String) {
        _state.update { s ->
            val newList = s.messages.toMutableList()
            if (newList.isNotEmpty()) {
                val last = newList.last()
                newList[newList.size - 1] = last.copy(content = content)
            }
            s.copy(messages = newList)
        }
    }

    fun isModelReady(): Boolean = _state.value.isModelReady
}
