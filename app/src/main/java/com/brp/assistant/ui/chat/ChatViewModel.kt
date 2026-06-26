package com.brp.assistant.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brp.assistant.data.db.ChatSessionDao
import com.brp.assistant.data.db.entities.ChatMessageEntity
import com.brp.assistant.data.db.entities.ChatSessionEntity
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
import java.util.UUID
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
    val allOfflineModels: List<OfflineModelUiItem> = emptyList(),
    val activeOfflineModelId: String? = null,
    val currentOnlineProvider: String = "Gemini",
    // ── История сессий (для планшетной панели) ──────────────────────────────
    val sessionHistory: List<ChatSessionSummary> = emptyList(),
    val selectedSessionId: String? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatUseCase: ChatUseCase,
    private val diagnoseUseCase: DiagnoseUseCase,
    private val llmEngine: LlmInferenceEngine,
    private val settingsRepository: SettingsRepository,
    private val chatSessionDao: ChatSessionDao
) : ViewModel() {

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    // ID текущей активной сессии (может быть null пока не отправлено первое сообщение)
    private var activeSessionId: String? = null

    init {
        // ── Готовность модели ────────────────────────────────────────────────
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

        // ── Список офлайн-моделей ────────────────────────────────────────────
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

        // ── Провайдер ────────────────────────────────────────────────────────
        viewModelScope.launch {
            settingsRepository.aiProvider.collect { p ->
                _state.update { it.copy(currentOnlineProvider = p ?: "Gemini") }
            }
        }

        // ── Наблюдаем за историей сессий из Room (Flow — реактивно) ─────────
        viewModelScope.launch {
            chatSessionDao.observeAllSessions().collect { entities ->
                val summaries = entities.map { entity ->
                    ChatSessionSummary(
                        id        = entity.id,
                        title     = entity.title,
                        dateLabel = formatDateLabel(entity.updatedAt),
                        preview   = entity.title.take(60)
                    )
                }
                _state.update { it.copy(sessionHistory = summaries) }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Публичные методы управления сессиями
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Загрузить историю сообщений существующей сессии.
     * Вызывается из ChatHistoryPanel при нажатии на элемент списка.
     */
    fun loadSession(sessionId: String) {
        viewModelScope.launch {
            val entities = chatSessionDao.getMessages(sessionId)
            val session  = chatSessionDao.getSession(sessionId) ?: return@launch
            val messages = entities.map { e ->
                ChatMessage(
                    content = e.content,
                    role    = if (e.role == "user") MessageRole.USER else MessageRole.ASSISTANT
                )
            }
            activeSessionId = sessionId
            _state.update {
                it.copy(
                    messages         = messages,
                    selectedSessionId = sessionId,
                    currentVehicleId = session.vehicleId,
                    currentMode      = session.mode,
                    isGenerating     = false,
                    riskLevel        = "low",
                    requiresEvacuation = false,
                    error            = null
                )
            }
        }
    }

    /**
     * Начать новый пустой чат (сброс активной сессии, очистка экрана).
     * ID сессии будет создан при первом sendMessage.
     */
    fun startNewChat(vehicleId: String?, mode: String) {
        activeSessionId = null
        _state.update {
            it.copy(
                messages           = emptyList(),
                selectedSessionId  = null,
                isGenerating       = false,
                riskLevel          = "low",
                requiresEvacuation = false,
                error              = null,
                currentVehicleId   = vehicleId,
                currentMode        = mode
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Существующие методы (без изменений в логике, только расширены)
    // ─────────────────────────────────────────────────────────────────────────

    fun clearForChat(vehicleId: String?, mode: String) {
        val current = _state.value
        val contextChanged = current.currentVehicleId != vehicleId || current.currentMode != mode
        if (contextChanged) {
            activeSessionId = null
            _state.update {
                it.copy(
                    messages           = emptyList(),
                    selectedSessionId  = null,
                    isGenerating       = false,
                    riskLevel          = "low",
                    requiresEvacuation = false,
                    error              = null,
                    currentVehicleId   = vehicleId,
                    currentMode        = mode
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

    fun sendMessage(text: String, mode: String, vehicleId: String?) {
        val userMsg = ChatMessage(text, MessageRole.USER)
        _state.update { it.copy(messages = it.messages + userMsg, isGenerating = true, error = null) }

        val effectiveModelId: String? = when {
            _state.value.selectedLlmModelId != null -> _state.value.selectedLlmModelId
            else -> vehicleId
        }

        viewModelScope.launch {
            // ── OOM pre-check ────────────────────────────────────────────────
            val runtime   = Runtime.getRuntime()
            val freeMemMb = (runtime.maxMemory() - runtime.totalMemory() + runtime.freeMemory()) / 1_048_576L
            if (freeMemMb < 256L) {
                val oomMsg = "⚠️ Недостаточно памяти для генерации (~${freeMemMb} МБ свободно). " +
                        "Закройте другие приложения и повторите попытку."
                _state.update {
                    it.copy(
                        isGenerating = false,
                        error        = oomMsg,
                        messages     = it.messages + ChatMessage(oomMsg, MessageRole.ASSISTANT)
                    )
                }
                return@launch
            }

            // ── Создаём сессию при первом сообщении ─────────────────────────
            val sessionId = ensureSession(text, vehicleId, mode)

            val history = _state.value.messages
            var assistantContent = ""
            val assistantMsg = ChatMessage("", MessageRole.ASSISTANT)
            _state.update { it.copy(messages = it.messages + assistantMsg) }

            try {
                if (mode == "diagnosis") {
                    diagnoseUseCase(text, effectiveModelId, history) { partial ->
                        assistantContent += partial
                        updateLastMessage(assistantContent)
                    }.collect { result ->
                        result.onSuccess { diag ->
                            _state.update {
                                it.copy(
                                    isGenerating       = false,
                                    riskLevel          = diag.riskLevel,
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
                        else        -> RetrievalMode.BOTH
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

                // ── Сохраняем сообщения в Room после завершения генерации ────
                persistMessages(sessionId)

            } catch (oom: OutOfMemoryError) {
                val oomMsg = "💾 Модель не поместилась в память устройства. " +
                        "Попробуйте более лёгкую модель или перезапустите приложение."
                _state.update { it.copy(isGenerating = false, error = oomMsg) }
                updateLastMessage(oomMsg)
                System.gc()
            } catch (e: Exception) {
                val errMsg = e.message ?: "Неизвестная ошибка выполнения"
                _state.update { it.copy(isGenerating = false, error = errMsg) }
                updateLastMessage("❌ Критическая ошибка: $errMsg")
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Приватные вспомогательные методы
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Гарантирует существование активной сессии.
     * При первом вызове создаёт новую запись в Room и устанавливает selectedSessionId.
     * При последующих вызовах возвращает уже существующий ID.
     */
    private suspend fun ensureSession(firstMessage: String, vehicleId: String?, mode: String): String {
        activeSessionId?.let { return it }

        val id    = UUID.randomUUID().toString()
        val now   = System.currentTimeMillis()
        val title = firstMessage.take(60).let { if (it.length == 60) "$it…" else it }

        chatSessionDao.insertSession(
            ChatSessionEntity(
                id        = id,
                title     = title,
                vehicleId = vehicleId,
                mode      = mode,
                createdAt = now,
                updatedAt = now
            )
        )
        activeSessionId = id
        _state.update { it.copy(selectedSessionId = id) }
        return id
    }

    /**
     * Записывает все текущие сообщения экрана в Room.
     * Вызывается после каждого завершённого цикла sendMessage.
     * Использует REPLACE → идемпотентно при повторных вызовах.
     */
    private suspend fun persistMessages(sessionId: String) {
        val now      = System.currentTimeMillis()
        val messages = _state.value.messages
        val title    = messages.firstOrNull { it.role == MessageRole.USER }?.content
            ?.take(60) ?: return

        val entities = messages.mapIndexed { index, msg ->
            ChatMessageEntity(
                id        = "${sessionId}_$index",
                sessionId = sessionId,
                role      = if (msg.role == MessageRole.USER) "user" else "assistant",
                content   = msg.content,
                timestamp = now + index  // монотонный timestamp для сортировки
            )
        }
        chatSessionDao.insertMessages(entities)
        chatSessionDao.updateSession(
            id        = sessionId,
            title     = title,
            updatedAt = now
        )
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

    /**
     * Форматирует epoch millis в читаемый label для sticky-заголовков истории.
     * «Сегодня» / «Вчера» / «14 июня» / «14 июня 2025»
     */
    private fun formatDateLabel(epochMillis: Long): String {
        val cal   = java.util.Calendar.getInstance()
        val today = cal.clone() as java.util.Calendar
        cal.timeInMillis = epochMillis

        val todayYear  = today.get(java.util.Calendar.YEAR)
        val todayDay   = today.get(java.util.Calendar.DAY_OF_YEAR)
        val msgYear    = cal.get(java.util.Calendar.YEAR)
        val msgDay     = cal.get(java.util.Calendar.DAY_OF_YEAR)

        val yesterday  = today.clone() as java.util.Calendar
        yesterday.add(java.util.Calendar.DAY_OF_YEAR, -1)

        return when {
            msgYear == todayYear && msgDay == todayDay ->
                "Сегодня"
            msgYear == todayYear && msgDay == yesterday.get(java.util.Calendar.DAY_OF_YEAR) ->
                "Вчера"
            msgYear == todayYear -> {
                val months = listOf(
                    "января","февраля","марта","апреля","мая","июня",
                    "июля","августа","сентября","октября","ноября","декабря"
                )
                "${cal.get(java.util.Calendar.DAY_OF_MONTH)} ${months[cal.get(java.util.Calendar.MONTH)]}"
            }
            else -> {
                val months = listOf(
                    "января","февраля","марта","апреля","мая","июня",
                    "июля","августа","сентября","октября","ноября","декабря"
                )
                "${cal.get(java.util.Calendar.DAY_OF_MONTH)} ${months[cal.get(java.util.Calendar.MONTH)]} ${cal.get(java.util.Calendar.YEAR)}"
            }
        }
    }

    fun isModelReady(): Boolean = _state.value.isModelReady
}
