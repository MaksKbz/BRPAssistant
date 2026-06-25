package com.brp.assistant.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brp.assistant.data.llm.LlmInferenceEngine
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
    val isModelReady: Boolean = false
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatUseCase: ChatUseCase,
    private val diagnoseUseCase: DiagnoseUseCase,
    private val llmEngine: LlmInferenceEngine,
    private val settingsRepository: com.brp.assistant.data.repository.SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // FIX #5: unified isModelReady — true if local model loaded OR correct API key present
            combine(
                llmEngine.activeModelId,
                settingsRepository.geminiApiKey,
                settingsRepository.groqApiKey,  // FIX: was grokApiKey (compilation error)
                settingsRepository.aiProvider
            ) { modelId, geminiKey, groqKey, provider ->
                val hasApiKey = if (provider == "Gemini")
                    !geminiKey.isNullOrBlank()
                else
                    !groqKey.isNullOrBlank()
                val isReady = modelId != null || hasApiKey
                android.util.Log.d(
                    "ChatViewModel",
                    "isModelReady=$isReady (provider=$provider, hasKey=$hasApiKey, localModel=$modelId)"
                )
                isReady
            }.collect { ready ->
                _state.update { it.copy(isModelReady = ready) }
            }
        }
    }

    fun sendMessage(text: String, mode: String, modelId: String?) {
        val userMsg = ChatMessage(text, MessageRole.USER)
        _state.update { it.copy(messages = it.messages + userMsg, isGenerating = true, error = null) }

        viewModelScope.launch {
            val history = _state.value.messages
            var assistantContent = ""
            val assistantMsg = ChatMessage("", MessageRole.ASSISTANT)
            _state.update { it.copy(messages = it.messages + assistantMsg) }

            if (mode == "diagnosis") {
                diagnoseUseCase(text, modelId, history) { partial ->
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
                val result = chatUseCase(text, retrievalMode, modelId, history) { partial ->
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

    // FIX #5: isModelReady now relies on StateFlow, not direct llmEngine call
    fun isModelReady(): Boolean = _state.value.isModelReady
}
