package com.brp.assistant.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brp.assistant.data.db.entities.ChatMessageEntity
import com.brp.assistant.data.db.entities.ChatSessionEntity
import com.brp.assistant.data.llm.LlmInferenceEngine
import com.brp.assistant.data.llm.OfflineModelInfo
import com.brp.assistant.data.llm.PublicOfflineModelCatalog
import com.brp.assistant.data.repository.ChatSessionRepository
import com.brp.assistant.data.repository.SettingsRepository
import com.brp.assistant.domain.AppHealthChecker
import com.brp.assistant.domain.DeviceCapabilityProvider
import com.brp.assistant.domain.model.ChatMessage
import com.brp.assistant.domain.model.MessageRole
import com.brp.assistant.domain.model.RetrievalMode
import com.brp.assistant.domain.usecase.ChatUseCase
import com.brp.assistant.domain.usecase.ConversationSummaryUseCase
import com.brp.assistant.domain.usecase.DiagnoseUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

data class OfflineModelUiItem(
    val model: OfflineModelInfo,
    val isDownloaded: Boolean
)

data class ChatSessionSummary(
    val id: String,
    val title: String,
    val vehicleName: String?,
    val dateLabel: String,
    val preview: String
)

data class ChatState(
    val messages: List<ChatMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val riskLevel: String = "low",
    val requiresEvacuation: Boolean = false,
    val error: String? = null,
    val isModelReady: Boolean = false,
    val currentVehicleId: String? = null,
    val currentVehicleName: String? = null,
    val currentMode: String? = null,
    val selectedLlmModelId: String? = null,
    val selectedOnlineProvider: String? = null,
    val allOfflineModels: List<OfflineModelUiItem> = emptyList(),
    val activeOfflineModelId: String? = null,
    val currentOnlineProvider: String = "Gemini",
    val sessionHistory: List<ChatSessionSummary> = emptyList(),
    val selectedSessionId: String? = null,
    /**
     * #2 — true когда активен онлайн-провайдер, но API-ключ не задан.
     */
    val hasOnlineKeyMissing: Boolean = false,
    /**
     * #12 — Предупреждение от AppHealthChecker:
     *   null  = всё хорошо
     *   строка = текст предупреждения для показа в HealthWarningBanner
     */
    val healthWarning: String? = null,
    /**
     * #13 — Текущий запрос поиска по истории чатов.
     * Пустая строка = поиск отключён, показывается полный список sessionHistory.
     */
    val searchQuery: String = ""
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatUseCase: ChatUseCase,
    private val diagnoseUseCase: DiagnoseUseCase,
    private val llmEngine: LlmInferenceEngine,
    private val settingsRepository: SettingsRepository,
    /**
     * #11 — Заменяет прямое использование ChatSessionDao.
     * Все вызовы к БД теперь через репозиторий с изоляцией ошибок.
     */
    private val chatRepo: ChatSessionRepository,
    /**
     * #9 / #11 — Генерация title сессии без API-вызова.
     */
    private val summaryUseCase: ConversationSummaryUseCase,
    /**
     * #12 — Для подписки на AppHealthChecker.status.
     */
    private val healthChecker: AppHealthChecker,
    private val deviceCapability: DeviceCapabilityProvider
) : ViewModel() {

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private var activeSessionId: String? = null

    /** FIX #3: хранит текущий job генерации — предыдущая отменяется. */
    private var generationJob: Job? = null

    init {
        // isModelReady
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

        // allOfflineModels + activeOfflineModelId
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

        // currentOnlineProvider
        viewModelScope.launch {
            settingsRepository.aiProvider.collect { p ->
                _state.update { it.copy(currentOnlineProvider = p ?: "Gemini") }
            }
        }

        // #11 — sessionHistory через репозиторий
        viewModelScope.launch {
            chatRepo.observeAll().collect { entities ->
                val summaries = entities.map { entity ->
                    ChatSessionSummary(
                        id          = entity.id,
                        title       = entity.title,
                        vehicleName = entity.vehicleName,
                        dateLabel   = formatDateLabel(entity.updatedAt),
                        preview     = entity.title.take(60)
                    )
                }
                _state.update { it.copy(sessionHistory = summaries) }
            }
        }

        // #2 — hasOnlineKeyMissing
        viewModelScope.launch {
            combine(
                settingsRepository.aiProvider,
                settingsRepository.geminiApiKey,
                settingsRepository.groqApiKey,
                llmEngine.activeModelId,
                _state.map { it.selectedLlmModelId to it.selectedOnlineProvider }
            ) { defaultProvider, geminiKey, groqKey, activeOfflineId, (selectedOfflineId, selectedOnline) ->
                if (activeOfflineId != null || selectedOfflineId != null) return@combine false
                val effectiveProvider = selectedOnline ?: defaultProvider ?: "Gemini"
                if (effectiveProvider == "Gemini") geminiKey.isNullOrBlank()
                else groqKey.isNullOrBlank()
            }.collect { missing ->
                _state.update { it.copy(hasOnlineKeyMissing = missing) }
            }
        }

        // #12 — healthWarning из AppHealthChecker
        viewModelScope.launch {
            healthChecker.status.collect { status ->
                val warning = when {
                    status.isLowDisk    -> "⚠️ Мало места на диске. Освободите память для стабильной работы."
                    status.isDbError    -> "❌ Ошибка БД. Перезапустите приложение."
                    else                -> null
                }
                _state.update { it.copy(healthWarning = warning) }
            }
        }
    }

    // ── #13 Поиск по истории ───────────────────────────────────────────

    /**
     * #13 — Обновляет запрос поиска.
     *
     * UI вызывает через debounce на TextField.onValueChange:
     * ```kotlin
     * TextField(onValueChange = { vm.updateSearchQuery(it) })
     * ```
     * LazyColumn фильтрует state.filteredSessions (см. ниже).
     */
    fun updateSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    /**
     * #13 — Отфильтрованный список сессий для UI.
     *
     * Клиентская фильтрация по title + vehicleName.
     * Достаточно до ~200 сессий; при росте > 200 переключить
     * на repo.search(query) через Flow с debounce.
     */
    val filteredSessions: StateFlow<List<ChatSessionSummary>> = _state
        .map { s ->
            val q = s.searchQuery.trim().lowercase()
            if (q.isEmpty()) s.sessionHistory
            else s.sessionHistory.filter { session ->
                session.title.lowercase().contains(q) ||
                session.vehicleName?.lowercase()?.contains(q) == true
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    // ── #14 Управление сессиями ──────────────────────────────────────

    /**
     * #14 — Удалить одну сессию.
     * Если удаляется активная сессия — очищаем экран.
     */
    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            chatRepo.deleteSession(sessionId)
            if (activeSessionId == sessionId) {
                activeSessionId = null
                _state.update {
                    it.copy(
                        messages           = emptyList(),
                        selectedSessionId  = null,
                        isGenerating       = false,
                        error              = null
                    )
                }
            }
        }
    }

    /**
     * #14 — Удалить все сессии и очистить экран.
     */
    fun deleteAllSessions() {
        viewModelScope.launch {
            chatRepo.deleteAll()
            activeSessionId = null
            _state.update {
                it.copy(
                    messages          = emptyList(),
                    selectedSessionId = null,
                    isGenerating      = false,
                    error             = null
                )
            }
        }
    }

    /**
     * #14 — Дублировать сессию: копирует сессию и все её сообщения с новым UUID.
     * Титул новой сессии: "Копия: <оригинальный title>".
     */
    fun duplicateSession(sessionId: String) {
        viewModelScope.launch {
            val original = chatRepo.getSession(sessionId) ?: return@launch
            val messages = chatRepo.getMessages(sessionId)
            val newId  = UUID.randomUUID().toString()
            val now    = System.currentTimeMillis()
            chatRepo.upsert(
                original.copy(
                    id        = newId,
                    title     = "Копия: ${original.title}",
                    createdAt = now,
                    updatedAt = now
                )
            )
            messages.forEach { msg ->
                chatRepo.insertMessage(
                    msg.copy(id = UUID.randomUUID().toString(), sessionId = newId)
                )
            }
        }
    }

    fun loadSession(sessionId: String) {
        viewModelScope.launch {
            val entities = chatRepo.getMessages(sessionId)
            val session  = chatRepo.getSession(sessionId) ?: return@launch
            val messages = entities.map { e ->
                ChatMessage(
                    id      = e.id,
                    content = e.content,
                    role    = if (e.role == "user") MessageRole.USER else MessageRole.ASSISTANT
                )
            }
            activeSessionId = sessionId
            _state.update {
                it.copy(
                    messages            = messages,
                    selectedSessionId   = sessionId,
                    currentVehicleId    = session.vehicleId,
                    currentVehicleName  = session.vehicleName,
                    currentMode         = session.mode,
                    isGenerating        = false,
                    riskLevel           = "low",
                    requiresEvacuation  = false,
                    error               = null
                )
            }
        }
    }

    fun startNewChat(vehicleId: String?, vehicleName: String?, mode: String) {
        activeSessionId = null
        _state.update {
            it.copy(
                messages            = emptyList(),
                selectedSessionId   = null,
                isGenerating        = false,
                riskLevel           = "low",
                requiresEvacuation  = false,
                error               = null,
                currentVehicleId    = vehicleId,
                currentVehicleName  = vehicleName,
                currentMode         = mode
            )
        }
    }

    fun clearForChat(vehicleId: String?, vehicleName: String?, mode: String) {
        val current = _state.value
        val contextChanged = current.currentVehicleId != vehicleId || current.currentMode != mode
        if (contextChanged) {
            activeSessionId = null
            _state.update {
                it.copy(
                    messages            = emptyList(),
                    selectedSessionId   = null,
                    isGenerating        = false,
                    riskLevel           = "low",
                    requiresEvacuation  = false,
                    error               = null,
                    currentVehicleId    = vehicleId,
                    currentVehicleName  = vehicleName,
                    currentMode         = mode
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

    fun sendMessage(text: String, mode: String, vehicleId: String?, vehicleName: String? = null) {
        generationJob?.cancel()

        val userMsg = ChatMessage(content = text, role = MessageRole.USER)
        _state.update { it.copy(messages = it.messages + userMsg, isGenerating = true, error = null) }

        val effectiveModelId: String? = _state.value.selectedLlmModelId

        generationJob = viewModelScope.launch {
            val mem = deviceCapability.checkMemory()
            if (!mem.isSafeForGeneration) {
                val oomMsg = "⚠️ Недостаточно памяти (heap: ~${mem.freeHeapMb} МБ, RAM: ~${mem.availRamMb} МБ). " +
                        "Закройте другие приложения и повторите."
                _state.update {
                    it.copy(
                        isGenerating = false,
                        error        = oomMsg,
                        messages     = it.messages + ChatMessage(content = oomMsg, role = MessageRole.ASSISTANT)
                    )
                }
                return@launch
            }

            val resolvedVehicleName = vehicleName ?: _state.value.currentVehicleName
            val sessionId = ensureSession(text, vehicleId, resolvedVehicleName, mode)

            val history = _state.value.messages
            var assistantContent = ""
            val assistantMsg = ChatMessage(content = "", role = MessageRole.ASSISTANT)
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

                persistMessages(sessionId, resolvedVehicleName)

            } catch (oom: OutOfMemoryError) {
                val oomMsg = "💾 Модель не поместилась в память. " +
                        "Попробуйте легкую модель или перезапустите приложение."
                _state.update { it.copy(isGenerating = false, error = oomMsg) }
                updateLastMessage(oomMsg)
                withContext(Dispatchers.Default) { System.gc() }
            } catch (e: Exception) {
                val errMsg = e.message ?: "Неизвестная ошибка выполнения"
                _state.update { it.copy(isGenerating = false, error = errMsg) }
                updateLastMessage("❌ Критическая ошибка: $errMsg")
            }
        }
    }

    // #11 — ensureSession через репозиторий + ConversationSummaryUseCase
    private suspend fun ensureSession(
        firstMessage: String,
        vehicleId: String?,
        vehicleName: String?,
        mode: String
    ): String {
        activeSessionId?.let { return it }
        val id  = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        // #9/#11: используем ConversationSummaryUseCase вместо take(60)
        val title = summaryUseCase(
            listOf(ChatMessage(content = firstMessage, role = MessageRole.USER))
        )
        chatRepo.upsert(
            ChatSessionEntity(
                id          = id,
                title       = title,
                vehicleId   = vehicleId,
                vehicleName = vehicleName,
                mode        = mode,
                createdAt   = now,
                updatedAt   = now
            )
        )
        activeSessionId = id
        _state.update { it.copy(selectedSessionId = id) }
        return id
    }

    // #11 — persistMessages через репозиторий
    private suspend fun persistMessages(sessionId: String, vehicleName: String?) {
        val now      = System.currentTimeMillis()
        val messages = _state.value.messages
        // #9/#11: титул через summaryUseCase — обрезает по слову, добавляет …
        val title = summaryUseCase(messages)
        val lastTwo = messages.takeLast(2)
        lastTwo.forEach { msg ->
            chatRepo.insertMessage(
                ChatMessageEntity(
                    id        = msg.id,
                    sessionId = sessionId,
                    role      = if (msg.role == MessageRole.USER) "user" else "assistant",
                    content   = msg.content,
                    timestamp = msg.timestamp
                )
            )
        }
        chatRepo.updateMeta(
            id          = sessionId,
            title       = title,
            vehicleName = vehicleName,
            updatedAt   = now
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

    private fun formatDateLabel(epochMillis: Long): String {
        if (epochMillis == 0L) return "Неизвестно"
        val msgDate   = Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault()).toLocalDate()
        val today     = LocalDate.now()
        val yesterday = today.minusDays(1)
        return when (msgDate) {
            today     -> "Сегодня"
            yesterday -> "Вчера"
            else -> {
                val pattern = if (msgDate.year == today.year) "d MMMM" else "d MMMM yyyy"
                msgDate.format(DateTimeFormatter.ofPattern(pattern, Locale("ru")))
            }
        }
    }

    fun isModelReady(): Boolean = _state.value.isModelReady
}
