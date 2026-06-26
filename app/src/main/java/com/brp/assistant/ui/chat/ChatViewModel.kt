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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class OfflineModelUiItem(
    val model: OfflineModelInfo,
    val isDownloaded: Boolean
)

data class ChatState(
    val messages: List<ChatMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val riskLevel: String = "low",
    val requiresEvacuation: Boolean = false,
    /** Non-null = show Snackbar, reset via dismissError() */
    val error: String? = null,
    val isModelReady: Boolean = false,
    val currentVehicleId: String? = null,
    val currentMode: String? = null,
    val selectedLlmModelId: String? = null,
    val selectedOnlineProvider: String? = null,
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

    companion object {
        private const val MAX_RETRIES   = 3
        private const val BASE_DELAY_MS = 1_000L
    }

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
                val hasKey = if (provider == "Gemini") !geminiKey.isNullOrBlank()
                             else !groqKey.isNullOrBlank()
                modelId != null || hasKey
            }.collect { ready -> _state.update { it.copy(isModelReady = ready) } }
        }
        viewModelScope.launch {
            llmEngine.activeModelId.collect { id ->
                val items = PublicOfflineModelCatalog.models.map { m ->
                    OfflineModelUiItem(m, llmEngine.isModelDownloaded(m))
                }
                _state.update { it.copy(activeOfflineModelId = id, allOfflineModels = items) }
            }
        }
        viewModelScope.launch {
            settingsRepository.aiProvider.collect { p ->
                _state.update { it.copy(currentOnlineProvider = p ?: "Gemini") }
            }
        }
    }

    fun clearForChat(vehicleId: String?, mode: String) {
        val cur = _state.value
        if (cur.currentVehicleId != vehicleId || cur.currentMode != mode) {
            _state.update {
                it.copy(
                    messages = emptyList(), isGenerating = false,
                    riskLevel = "low", requiresEvacuation = false,
                    error = null, currentVehicleId = vehicleId, currentMode = mode
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
    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    fun sendMessage(text: String, mode: String, vehicleId: String?) {
        val userMsg = ChatMessage(text, MessageRole.USER)
        _state.update { it.copy(messages = it.messages + userMsg, isGenerating = true, error = null) }
        val effectiveVehicleId = _state.value.selectedLlmModelId ?: vehicleId

        viewModelScope.launch {
            val history = _state.value.messages
            _state.update { it.copy(messages = it.messages + ChatMessage("", MessageRole.ASSISTANT)) }

            val result = retryWithBackoff(MAX_RETRIES) {
                var content = ""
                if (mode == "diagnosis") {
                    var diagResult: Result<com.brp.assistant.domain.model.DiagnoseResult>? = null
                    diagnoseUseCase(text, effectiveVehicleId, history) { partial ->
                        content += partial; updateLastMessage(content)
                    }.collect { r -> diagResult = r }
                    @Suppress("UNCHECKED_CAST")
                    (diagResult ?: Result.failure(Exception("\u041d\u0435\u0442 \u043e\u0442\u0432\u0435\u0442\u0430"))) as Result<Any>
                } else {
                    val rm = if (mode == "accessory") RetrievalMode.ACCESSORY else RetrievalMode.BOTH
                    @Suppress("UNCHECKED_CAST")
                    chatUseCase(text, rm, effectiveVehicleId, history) { partial ->
                        content += partial; updateLastMessage(content)
                    } as Result<Any>
                }
            }

            result.fold(
                onSuccess = { value ->
                    if (value is com.brp.assistant.domain.model.DiagnoseResult) {
                        _state.update {
                            it.copy(isGenerating = false,
                                riskLevel = value.riskLevel,
                                requiresEvacuation = value.requiresEvacuation)
                        }
                    } else {
                        _state.update { it.copy(isGenerating = false) }
                    }
                },
                onFailure = { err ->
                    val msg = buildUserFriendlyError(err)
                    _state.update { it.copy(isGenerating = false, error = msg) }
                    updateLastMessage("\u274c $msg")
                }
            )
        }
    }

    private suspend fun <T> retryWithBackoff(
        maxAttempts: Int,
        block: suspend () -> Result<T>
    ): Result<T> {
        var last: Result<T> = Result.failure(Exception("\u041d\u0435 \u0431\u044b\u043b\u043e \u043f\u043e\u043f\u044b\u0442\u043e\u043a"))
        repeat(maxAttempts) { attempt ->
            last = try { block() } catch (e: Exception) { Result.failure(e) }
            if (last.isSuccess) return last
            if (!currentCoroutineContext().isActive) return last
            delay(BASE_DELAY_MS * (1L shl attempt))   // 1s, 2s, 4s
        }
        return last
    }

    private fun buildUserFriendlyError(err: Throwable): String = when {
        err.message?.contains("OutOfMemory", ignoreCase = true) == true ||
        err is OutOfMemoryError ->
            "\u041d\u0435\u0445\u0432\u0430\u0442\u043a\u0430 \u043f\u0430\u043c\u044f\u0442\u0438. \u0417\u0430\u043a\u0440\u043e\u0439\u0442\u0435 \u0434\u0440\u0443\u0433\u0438\u0435 \u043f\u0440\u0438\u043b\u043e\u0436\u0435\u043d\u0438\u044f \u0438 \u043f\u043e\u0432\u0442\u043e\u0440\u0438\u0442\u0435."
        err.message?.contains("timeout", ignoreCase = true) == true ->
            "\u0412\u0440\u0435\u043c\u044f \u043e\u0436\u0438\u0434\u0430\u043d\u0438\u044f \u0438\u0441\u0442\u0435\u043a\u043b\u043e. \u041f\u0440\u043e\u0432\u0435\u0440\u044c\u0442\u0435 \u0438\u043d\u0442\u0435\u0440\u043d\u0435\u0442-\u0441\u043e\u0435\u0434\u0438\u043d\u0435\u043d\u0438\u0435."
        err.message?.contains("Unable to resolve host", ignoreCase = true) == true ||
        err.message?.contains("No address", ignoreCase = true) == true ->
            "\u041d\u0435\u0442 \u0441\u043e\u0435\u0434\u0438\u043d\u0435\u043d\u0438\u044f. \u0418\u0441\u043f\u043e\u043b\u044c\u0437\u0443\u0439\u0442\u0435 \u043e\u0444\u043b\u0430\u0439\u043d-\u043c\u043e\u0434\u0435\u043b\u044c."
        err.message?.contains("401", ignoreCase = true) == true ||
        err.message?.contains("403", ignoreCase = true) == true ->
            "\u041e\u0448\u0438\u0431\u043a\u0430 \u0430\u0432\u0442\u043e\u0440\u0438\u0437\u0430\u0446\u0438\u0438. \u041f\u0440\u043e\u0432\u0435\u0440\u044c\u0442\u0435 API-\u043a\u043b\u044e\u0447 \u0432 \u043d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0430\u0445."
        err.message?.contains("429", ignoreCase = true) == true ->
            "\u041f\u0440\u0435\u0432\u044b\u0448\u0435\u043d \u043b\u0438\u043c\u0438\u0442 \u0437\u0430\u043f\u0440\u043e\u0441\u043e\u0432. \u041f\u043e\u0434\u043e\u0436\u0434\u0438\u0442\u0435 \u043c\u0438\u043d\u0443\u0442\u0443 \u0438 \u043f\u043e\u0432\u0442\u043e\u0440\u0438\u0442\u0435."
        else -> err.message ?: "\u041d\u0435\u0438\u0437\u0432\u0435\u0441\u0442\u043d\u0430\u044f \u043e\u0448\u0438\u0431\u043a\u0430"
    }

    private fun updateLastMessage(content: String) {
        _state.update { s ->
            val list = s.messages.toMutableList()
            if (list.isNotEmpty()) list[list.size - 1] = list.last().copy(content = content)
            s.copy(messages = list)
        }
    }

    fun isModelReady(): Boolean = _state.value.isModelReady
}