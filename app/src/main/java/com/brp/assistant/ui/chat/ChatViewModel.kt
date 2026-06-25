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

data class ChatState(
    val messages: List<ChatMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val riskLevel: String = "low",
    val requiresEvacuation: Boolean = false,
    val error: String? = null,
    val isModelReady: Boolean = false,
    // Контекст текущего чата — используется для очистки при смене
    val currentVehicleId: String? = null,
    val currentMode: String? = null,
    // Выбранная LLM для этого чата (null = использовать глобальный провайдер)
    val selectedLlmModelId: String? = null,   // офлайн: id модели
    val selectedOnlineProvider: String? = null, // онлайн: "Gemini" / "Groq"
    val availableOfflineModels: List<OfflineModelInfo> = emptyList(),
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
            // FIX #5: unified isModelReady — true if local model loaded OR correct API key present
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

        // Следим за активной офлайн-моделью и провайдером для UI-селектора
        viewModelScope.launch {
            llmEngine.activeModelId.collect { id ->
                _state.update { s ->
                    s.copy(
                        activeOfflineModelId = id,
                        availableOfflineModels = PublicOfflineModelCatalog.models
                            .filter { llmEngine.isModelDownloaded(it) }
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

    /**
     * Вызывается при каждом входе в чат-экран.
     * Если vehicleId или mode изменились относительно текущего контекста —
     * полностью сбрасываем историю сообщений и метаданные.
     */
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

    /** Выбрать офлайн-модель для текущего чата */
    fun selectOfflineLlm(modelId: String?) {
        _state.update { it.copy(selectedLlmModelId = modelId, selectedOnlineProvider = null) }
    }

    /** Выбрать онлайн-провайдера для текущего чата */
    fun selectOnlineLlm(provider: String) {
        _state.update { it.copy(selectedOnlineProvider = provider, selectedLlmModelId = null) }
    }

    /** Сбросить выбор LLM — будет использоваться глобальный провайдер из настроек */
    fun resetLlmSelection() {
        _state.update { it.copy(selectedLlmModelId = null, selectedOnlineProvider = null) }
    }

    fun sendMessage(text: String, mode: String, modelId: String?) {
        val userMsg = ChatMessage(text, MessageRole.USER)
        _state.update { it.copy(messages = it.messages + userMsg, isGenerating = true, error = null) }

        // Если в чате выбрана конкретная LLM — передаём её id, иначе используем переданный modelId
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
