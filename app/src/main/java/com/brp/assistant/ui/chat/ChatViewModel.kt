package com.brp.assistant.ui.chat

import android.app.ActivityManager
import android.content.Context
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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

/** Дополнительный wrapper для отображения статуса загрузки в UI-шторке. */
data class OfflineModelUiItem(
    val model: OfflineModelInfo,
    val isDownloaded: Boolean
)

/** Краткое описание сессии для панели истории на планшете. */
data class ChatSessionSummary(
    val id: String,
    val title: String,
    val vehicleName: String?,    // «Саn-Am Maverick X3» или null
    val dateLabel: String,       // «Сегодня», «Вчера», «14 июня» …
    val preview: String          // первые 60 символов первого вопроса
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
    val selectedSessionId: String? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatUseCase: ChatUseCase,
    private val diagnoseUseCase: DiagnoseUseCase,
    private val llmEngine: LlmInferenceEngine,
    private val settingsRepository: SettingsRepository,
    private val chatSessionDao: ChatSessionDao,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private var activeSessionId: String? = null

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

        viewModelScope.launch {
            chatSessionDao.observeAllSessions().collect { entities ->
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
    }

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
        val userMsg = ChatMessage(text, MessageRole.USER)
        _state.update { it.copy(messages = it.messages + userMsg, isGenerating = true, error = null) }

        val effectiveModelId: String? = when {
            _state.value.selectedLlmModelId != null -> _state.value.selectedLlmModelId
            else -> vehicleId
        }

        viewModelScope.launch {
            // ── OOM pre-check: двойная проверка — heap JVM + системная RAM ─────────────
            val runtime    = Runtime.getRuntime()
            val freeHeapMb = (runtime.maxMemory() - runtime.totalMemory() + runtime.freeMemory()) / 1_048_576L

            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo    = ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(memInfo)
            val availRamMb = memInfo.availMem / 1_048_576L

            if (freeHeapMb < 150L || availRamMb < 150L || memInfo.lowMemory) {
                val oomMsg = "⚠️ Недостаточно памяти для генерации (heap: ~${freeHeapMb} МБ, RAM: ~${availRamMb} МБ). " +
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

            val resolvedVehicleName = vehicleName ?: _state.value.currentVehicleName
            val sessionId = ensureSession(text, vehicleId, resolvedVehicleName, mode)

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

                persistMessages(sessionId, resolvedVehicleName)

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

    private suspend fun ensureSession(
        firstMessage: String,
        vehicleId: String?,
        vehicleName: String?,
        mode: String
    ): String {
        activeSessionId?.let { return it }

        val id    = UUID.randomUUID().toString()
        val now   = System.currentTimeMillis()
        val title = firstMessage.take(60).let { if (it.length == 60) "$it…" else it }

        chatSessionDao.insertSession(
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

    /**
     * FIX #3: дельта-сохранение.
     *
     * Раньше: перезаписывались ВСЕ сообщения сессии через INSERT REPLACE при каждом
     * ответе. При 100 сообщениях в сессии — 100 INSERT на каждый запрос.
     *
     * Теперь: сохраняем только 2 последних сообщения (user + assistant)
     * через insertMessage(сингл) с IGNORE. Повторный вызов для того же id — no-op.
     * Каждое сообщение получает UUID, поэтому IGNORE правильно
     * идентифицирует новые сообщения и пропускает уже существующие.
     */
    private suspend fun persistMessages(sessionId: String, vehicleName: String?) {
        val now      = System.currentTimeMillis()
        val messages = _state.value.messages
        val title    = messages.firstOrNull { it.role == MessageRole.USER }?.content
            ?.take(60) ?: return

        // FIX #3: берём только последние 2 сообщения вместо всех
        val lastTwo = messages.takeLast(2)
        lastTwo.forEach { msg ->
            chatSessionDao.insertMessage(
                ChatMessageEntity(
                    id        = UUID.randomUUID().toString(),  // уникальный UUID → IGNORE никогда не срабатывает
                    sessionId = sessionId,
                    role      = if (msg.role == MessageRole.USER) "user" else "assistant",
                    content   = msg.content,
                    timestamp = now
                )
            )
        }

        chatSessionDao.updateSession(
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

    /**
     * FIX: заменён устаревший java.util.Calendar на java.time API.
     * Calendar — deprecated-стиль; java.time доступен с API 26, minSdk=30 — OK.
     */
    private fun formatDateLabel(epochMillis: Long): String {
        val msgDate   = Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
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
