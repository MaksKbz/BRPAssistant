package com.brp.assistant.ui.chat

import androidx.lifecycle.SavedStateHandle
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
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
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

private const val KEY_SESSION_ID = "saved_session_id"
private const val KEY_MODE       = "saved_mode"

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
    // Прогресс скачивания модели прямо из чата
    val chatDownloadModelId: String? = null,
    val chatDownloadProgress: Float = 0f,
    val chatDownloadError: String? = null,
    val allOfflineModels: List<OfflineModelUiItem> = emptyList(),
    val activeOfflineModelId: String? = null,
    val currentOnlineProvider: String = "Gemini",
    val sessionHistory: List<ChatSessionSummary> = emptyList(),
    val selectedSessionId: String? = null,
    val hasOnlineKeyMissing: Boolean = false,
    val healthWarning: String? = null,
    val searchQuery: String = "",
    val pendingDownloadWarning: String? = null,
    val pendingModelToDownload: com.brp.assistant.data.llm.OfflineModelInfo? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatUseCase: ChatUseCase,
    private val diagnoseUseCase: DiagnoseUseCase,
    private val llmEngine: LlmInferenceEngine,
    private val settingsRepository: SettingsRepository,
    private val chatRepo: ChatSessionRepository,
    private val summaryUseCase: ConversationSummaryUseCase,
    private val healthChecker: AppHealthChecker,
    private val deviceCapability: DeviceCapabilityProvider,
    private val downloader: com.brp.assistant.data.llm.download.PublicHuggingFaceModelDownloader,
    private val recommendLlmModeUseCase: com.brp.assistant.domain.usecase.RecommendLlmModeUseCase,
    /**
     * A3 — SavedStateHandle позволяет пережить гибель процесса Android (OOM-kill).
     * selectedSessionId и currentMode восстанавливаются автоматически.
     */
    private val savedState: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    /**
     * A3 — activeSessionId восстанавливается из SavedStateHandle после process death.
     * При каждом изменении сохраняем обратно.
     */
    private var activeSessionId: String?
        get()      = savedState[KEY_SESSION_ID]
        set(value) { savedState[KEY_SESSION_ID] = value }

    private var savedMode: String?
        get()      = savedState[KEY_MODE]
        set(value) { savedState[KEY_MODE] = value }

    private var generationJob: Job? = null

    init {
        // A3 — если процесс убит и пересоздан, восстанавливаем сессию
        val restoredSessionId = savedState.get<String>(KEY_SESSION_ID)
        if (restoredSessionId != null) {
            viewModelScope.launch { loadSession(restoredSessionId) }
        }

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

        // Глобальный выбор онлайна: синхронизируем selectedOnlineProvider
        // из DataStore, чтобы он применялся ко ВСЕМ чатам.
        viewModelScope.launch {
            settingsRepository.chatForceOnline.collect { provider ->
                _state.update { it.copy(selectedOnlineProvider = provider) }
            }
        }

        viewModelScope.launch {
            llmEngine.activeModelId.collect { id ->
                val items = PublicOfflineModelCatalog.models.map { model ->
                    OfflineModelUiItem(
                        model        = model,
                        isDownloaded = llmEngine.isModelDownloaded(model)
                    )
                }
                _state.update { s -> s.copy(activeOfflineModelId = id, allOfflineModels = items) }
            }
        }

        viewModelScope.launch {
            settingsRepository.aiProvider.collect { p ->
                _state.update { it.copy(currentOnlineProvider = p ?: "Gemini") }
            }
        }

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

        viewModelScope.launch {
            healthChecker.status.collect { status ->
                val warning = when {
                    status.isLowDisk -> "⚠️ Мало места на диске. Освободите память для стабильной работы."
                    status.isDbError -> "❌ Ошибка БД. Перезапустите приложение."
                    else             -> null
                }
                _state.update { it.copy(healthWarning = warning) }
            }
        }
    }

    // ── Поиск по истории ──────────────────────────────────────────────

    fun updateSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

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
            scope          = viewModelScope,
            started        = SharingStarted.WhileSubscribed(5_000),
            initialValue   = emptyList()
        )

    // ── Управление сессиями ──────────────────────────────────────────

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            chatRepo.deleteSession(sessionId)
            if (activeSessionId == sessionId) {
                activeSessionId = null
                _state.update {
                    it.copy(messages = emptyList(), selectedSessionId = null,
                        isGenerating = false, error = null)
                }
            }
        }
    }

    fun deleteAllSessions() {
        viewModelScope.launch {
            chatRepo.deleteAll()
            activeSessionId = null
            _state.update {
                it.copy(messages = emptyList(), selectedSessionId = null,
                    isGenerating = false, error = null)
            }
        }
    }

    fun duplicateSession(sessionId: String) {
        viewModelScope.launch {
            val original = chatRepo.getSession(sessionId) ?: return@launch
            val messages = chatRepo.getMessages(sessionId)
            val newId  = UUID.randomUUID().toString()
            val now    = System.currentTimeMillis()
            chatRepo.upsert(
                original.copy(id = newId, title = "Копия: ${original.title}",
                    createdAt = now, updatedAt = now)
            )
            messages.forEach { msg ->
                chatRepo.insertMessage(msg.copy(id = UUID.randomUUID().toString(), sessionId = newId))
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
                    role    = if (e.role == "user") MessageRole.USER else MessageRole.ASSISTANT,
                    sources = parseSources(e.sources)
                )
            }
            // A3 — сохраняем в SavedStateHandle
            activeSessionId = sessionId
            savedMode = session.mode
            _state.update {
                it.copy(
                    messages           = messages,
                    selectedSessionId  = sessionId,
                    currentVehicleId   = session.vehicleId,
                    currentVehicleName = session.vehicleName,
                    currentMode        = session.mode,
                    isGenerating       = false,
                    riskLevel          = "low",
                    requiresEvacuation = false,
                    error              = null
                )
            }
        }
    }

    fun startNewChat(vehicleId: String?, vehicleName: String?, mode: String) {
        // A3 — сбрасываем SavedStateHandle при явном создании нового чата
        activeSessionId = null
        savedMode = mode
        _state.update {
            it.copy(
                messages           = emptyList(),
                selectedSessionId  = null,
                isGenerating       = false,
                riskLevel          = "low",
                requiresEvacuation = false,
                error              = null,
                currentVehicleId   = vehicleId,
                currentVehicleName = vehicleName,
                currentMode        = mode
            )
        }
    }

    fun clearForChat(vehicleId: String?, vehicleName: String?, mode: String) {
        val current = _state.value
        val contextChanged = current.currentVehicleId != vehicleId || current.currentMode != mode
        if (contextChanged) {
            activeSessionId = null
            savedMode = mode
            _state.update {
                it.copy(
                    messages           = emptyList(),
                    selectedSessionId  = null,
                    isGenerating       = false,
                    riskLevel          = "low",
                    requiresEvacuation = false,
                    error              = null,
                    currentVehicleId   = vehicleId,
                    currentVehicleName = vehicleName,
                    currentMode        = mode
                )
            }
        }
    }

    fun selectOfflineLlm(modelId: String?) {
        if (modelId == null) {
            viewModelScope.launch { settingsRepository.setChatForceOnline(null) }
            _state.update { it.copy(selectedLlmModelId = null, selectedOnlineProvider = null) }
            return
        }

        val model = com.brp.assistant.data.llm.PublicOfflineModelCatalog.getById(modelId)
            ?: run {
                _state.update { it.copy(error = "Модель не найдена") }
                return
            }

        // Сбрасываем онлайн
        viewModelScope.launch { settingsRepository.setChatForceOnline(null) }
        _state.update { it.copy(selectedLlmModelId = modelId, selectedOnlineProvider = null) }

        if (llmEngine.isModelDownloaded(model)) {
            // Модель уже скачана → активируем автоматически
            activateFromChat(model)
        } else {
            // Модель не скачана → скачиваем, потом активируем
            downloadFromChat(model)
        }
    }

    private fun refreshOfflineModels() {
        val items = com.brp.assistant.data.llm.PublicOfflineModelCatalog.models.map { model ->
            OfflineModelUiItem(
                model = model,
                isDownloaded = llmEngine.isModelDownloaded(model)
            )
        }
        _state.update { s -> s.copy(allOfflineModels = items) }
    }

    private fun activateFromChat(model: com.brp.assistant.data.llm.OfflineModelInfo) {
        viewModelScope.launch {
            _state.update { it.copy(error = "⏳ Активирую ${model.title}...") }
            val result = llmEngine.initialize(model)
            if (result.isSuccess) {
                _state.update { it.copy(error = null, isModelReady = true) }
            } else {
                _state.update {
                    it.copy(error = "Не удалось активировать: ${result.exceptionOrNull()?.message}")
                }
            }
        }
    }

    private fun downloadFromChat(model: com.brp.assistant.data.llm.OfflineModelInfo) {
        // P0: проверка безопасности модели перед скачиванием из чата (как в ModelManager)
        val recommendation = recommendLlmModeUseCase.evaluate(model)
        if (!recommendation.isSafe) {
            _state.update {
                it.copy(
                    pendingDownloadWarning = recommendation.warningMessage,
                    pendingModelToDownload = model
                )
            }
            return
        }
        startChatDownload(model)
    }

    private fun startChatDownload(model: com.brp.assistant.data.llm.OfflineModelInfo) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    chatDownloadModelId = model.id,
                    chatDownloadProgress = 0f,
                    chatDownloadError = null,
                    pendingDownloadWarning = null,
                    pendingModelToDownload = null,
                    error = "⬇️ Скачиваю ${model.title}..."
                )
            }
            try {
                downloader.downloadModel(model).collect { state ->
                    when (state) {
                        is com.brp.assistant.data.llm.download.ModelDownloadState.Progress -> {
                            val progress = (state.percent ?: 0).toFloat() / 100f
                            _state.update { it.copy(chatDownloadProgress = progress) }
                        }
                        is com.brp.assistant.data.llm.download.ModelDownloadState.Success -> {
                            _state.update {
                                it.copy(chatDownloadModelId = null, chatDownloadProgress = 0f)
                            }
                            // Обновляем список моделей с актуальным статусом загрузки
                            refreshOfflineModels()
                            // Сразу активируем после скачивания
                            activateFromChat(model)
                        }
                        is com.brp.assistant.data.llm.download.ModelDownloadState.Error -> {
                            _state.update {
                                it.copy(
                                    chatDownloadModelId = null,
                                    chatDownloadProgress = 0f,
                                    chatDownloadError = state.message,
                                    error = state.message
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(chatDownloadModelId = null, error = "Ошибка: ${e.message}")
                }
            }
        }
    }

    fun confirmUnsafeModelDownload() {
        val model = _state.value.pendingModelToDownload ?: return
        _state.update { it.copy(pendingDownloadWarning = null, pendingModelToDownload = null) }
        startChatDownload(model)
    }

    fun dismissUnsafeModelDownload() {
        _state.update { it.copy(pendingDownloadWarning = null, pendingModelToDownload = null) }
    }

    fun selectOnlineLlm(provider: String) {
        viewModelScope.launch {
            // Глобально выбираем онлайн — применяется ко ВСЕМ чатам
            settingsRepository.setChatForceOnline(provider)
        }
        _state.update { it.copy(selectedOnlineProvider = provider, selectedLlmModelId = null) }
    }

    fun resetLlmSelection() {
        viewModelScope.launch {
            settingsRepository.setChatForceOnline(null)
        }
        _state.update { it.copy(selectedLlmModelId = null, selectedOnlineProvider = null) }
    }

    fun sendMessage(text: String, mode: String, vehicleId: String?, vehicleName: String? = null) {
        generationJob?.cancel()

        val userMsg = ChatMessage(content = text, role = MessageRole.USER)
        _state.update { it.copy(messages = it.messages + userMsg, isGenerating = true, error = null) }

        // FIX: передаём РЕАЛЬНЫЙ vehicleId (выбранная техника BRP), а не
        // selectedLlmModelId (ID языковой модели), как было раньше.
        // Раньше в use case попадал ID LLM-модели как vehicleId → техника
        // не передавалась в промпт, и модель спрашивала «что за техника».
        val effectiveVehicleId = vehicleId ?: _state.value.currentVehicleId

        generationJob = viewModelScope.launch {
            // Глобальный выбор модели: если в DataStore выбран онлайн — forceRemote.
            val forceRemote = _state.value.selectedOnlineProvider != null
                || settingsRepository.chatForceOnline.first() != null

            // P0: проверка памяти только для локального пути, чтобы не блокировать Gemini/Groq
            if (!forceRemote) {
                val mem = deviceCapability.checkMemory()
                if (!mem.isSafeForGeneration) {
                    val oomMsg = "⚠️ Недостаточно памяти (heap: ~${mem.freeHeapMb} МБ, RAM: ~${mem.availRamMb} МБ). " +
                            "Закройте другие приложения и повторите."
                    _state.update {
                        it.copy(isGenerating = false, error = oomMsg,
                            messages = it.messages + ChatMessage(content = oomMsg, role = MessageRole.ASSISTANT))
                    }
                    return@launch
                }
            }

            val resolvedVehicleName = vehicleName ?: _state.value.currentVehicleName
            val sessionId = ensureSession(text, vehicleId, resolvedVehicleName, mode)

            val history = _state.value.messages
            var assistantContent = ""
            var assistantSources: List<String> = emptyList()
            var assistantRisk: String = "low"
            var assistantEvac: Boolean = false
            val assistantMsg = ChatMessage(content = "", role = MessageRole.ASSISTANT)
            _state.update { it.copy(messages = it.messages + assistantMsg) }

            try {
                // Троттлинг стриминговых обновлений UI: не чаще чем каждые 40 мс.
                // Каждый onPartial дёргал MutableStateFlow.update → recompose LazyColumn
                // на каждый токен (20-50 раз/с), что тормозило UI на слабых девайсах.
                var lastUpdate = 0L
                var pendingContent = ""
                val throttledPartial: (String) -> Unit = { text ->
                    assistantContent += text
                    pendingContent = assistantContent
                    val now = System.currentTimeMillis()
                    if (now - lastUpdate >= 40L) {
                        updateLastMessage(pendingContent)
                        lastUpdate = now
                    }
                }

                if (mode == "diagnosis") {
                    diagnoseUseCase(text, effectiveVehicleId, history, throttledPartial, forceRemote = forceRemote).collect { result ->
                        // Проталкиваем финальное состояние после завершения стрима
                        if (pendingContent != assistantContent || pendingContent.isNotEmpty()) {
                            updateLastMessage(assistantContent)
                        }
                        result.onSuccess { diag ->
                            assistantContent = diag.message
                            assistantSources = diag.sources
                            assistantRisk = diag.riskLevel
                            assistantEvac = diag.requiresEvacuation
                            updateLastMessage(assistantContent, assistantSources)
                            _state.update {
                                it.copy(isGenerating = false,
                                    riskLevel = diag.riskLevel,
                                    requiresEvacuation = diag.requiresEvacuation)
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
                    val result = chatUseCase(text, retrievalMode, effectiveVehicleId, history, throttledPartial, forceRemote = forceRemote)
                    if (pendingContent != assistantContent) updateLastMessage(assistantContent)
                    _state.update { it.copy(isGenerating = false) }
                    if (result.isFailure) {
                        val errMsg = result.exceptionOrNull()?.message ?: "Неизвестная ошибка"
                        _state.update { it.copy(error = errMsg) }
                        updateLastMessage("❌ Ошибка подключения: $errMsg")
                    } else {
                        updateLastMessage(assistantContent, assistantSources)
                    }
                }

                persistMessages(sessionId, resolvedVehicleName)

            } catch (oom: OutOfMemoryError) {
                val oomMsg = "💾 Модель не поместилась в память. Попробуйте легкую модель или перезапустите приложение."
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

    private suspend fun ensureSession(
        firstMessage: String,
        vehicleId: String?,
        vehicleName: String?,
        mode: String
    ): String {
        activeSessionId?.let { return it }
        val id  = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
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
        // A3 — сохраняем в SavedStateHandle сразу после создания
        activeSessionId = id
        savedMode       = mode
        _state.update { it.copy(selectedSessionId = id) }
        return id
    }

    private suspend fun persistMessages(sessionId: String, vehicleName: String?) {
        val now      = System.currentTimeMillis()
        val messages = _state.value.messages
        val title    = summaryUseCase(messages)
        val lastTwo  = messages.takeLast(2)
        lastTwo.forEach { msg ->
            chatRepo.insertMessage(
                ChatMessageEntity(
                    id        = msg.id,
                    sessionId = sessionId,
                    role      = if (msg.role == MessageRole.USER) "user" else "assistant",
                    content   = msg.content,
                    timestamp = msg.timestamp,
                    sources   = if (msg.sources.isNotEmpty()) serializeSources(msg.sources) else null
                )
            )
        }
        chatRepo.updateMeta(id = sessionId, title = title, vehicleName = vehicleName, updatedAt = now)
    }

    private val sourcesJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private fun serializeSources(sources: List<String>): String =
        sourcesJson.encodeToString(ListSerializer(String.serializer()), sources)
    private fun parseSources(raw: String?): List<String> = runCatching {
        if (raw.isNullOrBlank()) emptyList()
        else sourcesJson.decodeFromString(ListSerializer(String.serializer()), raw)
    }.getOrDefault(emptyList())

    private fun updateLastMessage(content: String, sources: List<String> = emptyList()) {
        _state.update { s ->
            val newList = s.messages.toMutableList()
            if (newList.isNotEmpty()) {
                val last = newList.last()
                newList[newList.size - 1] = last.copy(
                    content = content,
                    sources = if (sources.isNotEmpty()) sources else last.sources
                )
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
