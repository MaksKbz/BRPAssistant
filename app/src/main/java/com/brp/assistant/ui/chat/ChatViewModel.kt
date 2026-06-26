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
import com.brp.assistant.domain.DeviceCapabilityProvider
import com.brp.assistant.domain.model.ChatMessage
import com.brp.assistant.domain.model.MessageRole
import com.brp.assistant.domain.model.RetrievalMode
import com.brp.assistant.domain.usecase.ChatUseCase
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
    val selectedSessionId: String? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatUseCase: ChatUseCase,
    private val diagnoseUseCase: DiagnoseUseCase,
    private val llmEngine: LlmInferenceEngine,
    private val settingsRepository: SettingsRepository,
    private val chatSessionDao: ChatSessionDao,
    private val deviceCapability: DeviceCapabilityProvider
) : ViewModel() {

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private var activeSessionId: String? = null

    /**
     * FIX #3: хранит текущий job генерации — при повторном sendMessage
     * предыдущая генерация отменяется, чтобы избежать race condition
     * и мешанины токенов от двух параллельных потоков.
     */
    private var generationJob: Job? = null

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
        // FIX #3: отменяем предыдущую генерацию, если она ещё идёт.
        generationJob?.cancel()

        val userMsg = ChatMessage(content = text, role = MessageRole.USER)
        _state.update { it.copy(messages = it.messages + userMsg, isGenerating = true, error = null) }

        /**
         * FIX #6: убран vehicleId как fallback для effectiveModelId.
         * vehicleId — идентификатор техники (например "can-am-outlander-500"),
         * а не LLM-модели. Его передача в движок вызывала непредсказуемое
         * поведение. Если пользователь не выбрал модель явно — передаём null,
         * и движок сам выбирает текущую активную модель.
         */
        val effectiveModelId: String? = _state.value.selectedLlmModelId

        generationJob = viewModelScope.launch {
            // FIX #5: OOM pre-check — через DeviceCapabilityProvider, без ActivityManager в VM
            val mem = deviceCapability.checkMemory()
            if (!mem.isSafeForGeneration) {
                val oomMsg = "⚠️ Недостаточно памяти (heap: ~${mem.freeHeapMb} МБ, RAM: ~${mem.availRamMb} МБ). " +
                        "Закройте другие приложения и повторите попытку."
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
                // FIX: System.gc() перенесён в Dispatchers.Default —
                // вызов на Main thread вызывал краткий ANR (200-600 мс)
                // на устройствах с Helio G85 / Dimensity 700 / SD 680.
                val oomMsg = "💾 Модель не поместилась в память устройства. " +
                        "Попробуйте более лёгкую модель или перезапустите приложение."
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
     * FIX #1: использует стабильный msg.id из ChatMessage вместо генерации
     * нового UUID. ChatSessionDao.insertMessage использует OnConflictStrategy.IGNORE,
     * поэтому повторный вызов persistMessages для одной сессии безопасен —
     * уже сохранённые сообщения (с тем же id) будут проигнорированы.
     */
    private suspend fun persistMessages(sessionId: String, vehicleName: String?) {
        val now      = System.currentTimeMillis()
        val messages = _state.value.messages
        val title    = messages.firstOrNull { it.role == MessageRole.USER }?.content
            ?.take(60) ?: return
        val lastTwo = messages.takeLast(2)
        lastTwo.forEach { msg ->
            chatSessionDao.insertMessage(
                ChatMessageEntity(
                    id        = msg.id,   // стабильный id из ChatMessage
                    sessionId = sessionId,
                    role      = if (msg.role == MessageRole.USER) "user" else "assistant",
                    content   = msg.content,
                    timestamp = msg.timestamp
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
     * FIX: epochMillis == 0L возвращает "Неизвестно" вместо "1 января 1970".
     * Нулевой epoch появляется при миграции старых сессий или ошибке записи
     * updatedAt в БД — без этой проверки пользователь видел некорректную дату.
     */
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
