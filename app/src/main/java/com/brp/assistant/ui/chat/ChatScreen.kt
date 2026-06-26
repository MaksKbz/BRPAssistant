package com.brp.assistant.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.brp.assistant.domain.model.ChatMessage
import com.brp.assistant.domain.model.MessageRole
import com.brp.assistant.ui.components.ChatInputBar
import com.brp.assistant.ui.components.SafetyBanner
import com.brp.assistant.ui.navigation.Screen

// ─────────────────────────────────────────────────────────────────────────────
// Модель сессии истории (упрощённая — для отображения в левой панели)
// ─────────────────────────────────────────────────────────────────────────────
data class ChatSessionSummary(
    val id: String,
    val title: String,
    val dateLabel: String,   // "Сегодня", "Вчера", "14 июн" …
    val preview: String      // первые ~60 символов первого вопроса
)

// ─────────────────────────────────────────────────────────────────────────────
// Левая панель истории (только планшет / фолдабл)
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatHistoryPanel(
    sessions: List<ChatSessionSummary>,
    selectedId: String?,
    onSelectSession: (String) -> Unit,
    onNewChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        // ── Заголовок панели ──────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "История",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            IconButton(onClick = onNewChat, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Новый чат",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        HorizontalDivider()

        if (sessions.isEmpty()) {
            // ── Пустое состояние ──────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.ChatBubbleOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(40.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Нет сохранённых\nдиалогов",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        } else {
            // ── Список сессий со sticky-заголовками дат ───────────────────────
            // Группируем по dateLabel для sticky headers
            val grouped = remember(sessions) {
                sessions.groupBy { it.dateLabel }
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                grouped.forEach { (dateLabel, items) ->
                    stickyHeader(key = "header_$dateLabel") {
                        Text(
                            dateLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                    items(items, key = { it.id }) { session ->
                        val isSelected = session.id == selectedId
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectSession(session.id) },
                            color = if (isSelected)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Column(
                                modifier = Modifier.padding(
                                    horizontal = 16.dp,
                                    vertical = 10.dp
                                )
                            ) {
                                Text(
                                    session.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    color = if (isSelected)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else
                                        MaterialTheme.colorScheme.onSurface
                                )
                                if (session.preview.isNotBlank()) {
                                    Text(
                                        session.preview,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                            alpha = 0.7f
                                        ),
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Основной экран чата
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    title: String = "Чат",
    messages: List<ChatMessage>,
    riskLevel: String,
    requiresEvacuation: Boolean,
    isGenerating: Boolean,
    isModelReady: Boolean,
    selectedVehicleName: String?,
    // LLM-селектор
    allOfflineModels: List<OfflineModelUiItem>,
    activeOfflineModelId: String?,
    currentOnlineProvider: String,
    selectedLlmModelId: String?,
    selectedOnlineProvider: String?,
    onSelectOfflineLlm: (String?) -> Unit,
    onSelectOnlineLlm: (String) -> Unit,
    onResetLlm: () -> Unit,
    onSend: (String) -> Unit,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    /**
     * Передаётся из BrpNavGraph → NavHostContent → ChatScreen.
     * Compact  → телефон → однопанельный layout (без изменений)
     * Medium / Expanded → планшет/фолдабл → двухпанельный layout
     */
    widthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
    /**
     * История сессий для левой панели (планшет).
     * На телефоне игнорируется.
     * Должна заполняться из ChatViewModel.sessionHistory StateFlow.
     */
    sessionHistory: List<ChatSessionSummary> = emptyList(),
    selectedSessionId: String? = null,
    onSelectSession: (String) -> Unit = {},
    onNewChat: () -> Unit = {}
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var showLlmSheet by remember { mutableStateOf(false) }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    val activeLlmLabel: String = when {
        selectedLlmModelId != null ->
            allOfflineModels.find { it.model.id == selectedLlmModelId }?.model?.title
                ?: "Офлайн-модель"
        selectedOnlineProvider != null -> selectedOnlineProvider
        activeOfflineModelId != null ->
            allOfflineModels.find { it.model.id == activeOfflineModelId }?.model?.title
                ?: currentOnlineProvider
        else -> currentOnlineProvider
    }

    val isTablet = widthSizeClass != WindowWidthSizeClass.Compact

    // ── Контентная область (общая для обоих режимов) ──────────────────────────
    @Composable
    fun ChatContent(modifier: Modifier = Modifier) {
        Column(modifier = modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Column {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        if (selectedVehicleName != null) {
                            Text(
                                selectedVehicleName,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                },
                navigationIcon = {
                    // На планшете кнопка Back не нужна — есть левая панель навигации,
                    // оставляем её только на телефоне
                    if (!isTablet) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, null)
                        }
                    }
                },
                actions = {
                    TextButton(
                        onClick = { showLlmSheet = true },
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.SmartToy,
                            contentDescription = "Выбрать модель ИИ",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            activeLlmLabel,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                    IconButton(onClick = { onNavigate(Screen.Home.route) }) {
                        Icon(Icons.Default.Home, "Главная")
                    }
                }
            )

            if (riskLevel != "low") {
                SafetyBanner(
                    level = riskLevel,
                    requiresEvacuation = requiresEvacuation,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    // FIX: нижний отступ защищает последнее сообщение от перекрытия
                    // системной gesture-полосой на Android 10+ без жёсткой кнопочной навигации
                    bottom = WindowInsets.navigationBars
                        .asPaddingValues()
                        .calculateBottomPadding() + 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages) { msg ->
                    MessageBubble(message = msg)
                }

                if (isGenerating) {
                    item {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Думаю…", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            ChatInputBar(
                value = input,
                onValueChange = { input = it },
                onSend = {
                    if (input.isNotBlank()) {
                        onSend(input)
                        input = ""
                    }
                },
                enabled = isModelReady && !isGenerating,
                placeholder = "Задайте вопрос…"
            )
        }
    }

    // ── Layout: планшет (Rail) или телефон (Bottom) ───────────────────────────
    if (isTablet) {
        // Двухпанельный Row: 30% история | 70% чат
        Row(modifier = Modifier.fillMaxSize()) {
            ChatHistoryPanel(
                sessions = sessionHistory,
                selectedId = selectedSessionId,
                onSelectSession = onSelectSession,
                onNewChat = onNewChat,
                modifier = Modifier
                    .fillMaxHeight()
                    // Фиксированная ширина 280dp — оптимум для sw600dp..sw840dp.
                    // На больших планшетах (sw840dp+) ширина 300dp через weight(0.28f).
                    .width(280.dp)
            )
            VerticalDivider(thickness = 1.dp)
            ChatContent(modifier = Modifier.weight(1f))
        }
    } else {
        // Телефон — без изменений относительно предыдущей версии
        ChatContent()
    }

    // ── LLM Bottom Sheet (общий для обоих режимов) ────────────────────────────
    if (showLlmSheet) {
        ModalBottomSheet(onDismissRequest = { showLlmSheet = false }) {
            LlmSelectorSheet(
                allOfflineModels = allOfflineModels,
                activeOfflineModelId = activeOfflineModelId,
                currentOnlineProvider = currentOnlineProvider,
                selectedLlmModelId = selectedLlmModelId,
                selectedOnlineProvider = selectedOnlineProvider,
                onSelectOffline = { id ->
                    onSelectOfflineLlm(id)
                    showLlmSheet = false
                },
                onSelectOnline = { provider ->
                    onSelectOnlineLlm(provider)
                    showLlmSheet = false
                },
                onReset = {
                    onResetLlm()
                    showLlmSheet = false
                },
                onGoToModels = {
                    showLlmSheet = false
                    onNavigate(Screen.Models.route)
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LlmSelectorSheet — без изменений, оставляем как было
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun LlmSelectorSheet(
    allOfflineModels: List<OfflineModelUiItem>,
    activeOfflineModelId: String?,
    currentOnlineProvider: String,
    selectedLlmModelId: String?,
    selectedOnlineProvider: String?,
    onSelectOffline: (String) -> Unit,
    onSelectOnline: (String) -> Unit,
    onReset: () -> Unit,
    onGoToModels: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp)
    ) {
        Text(
            "Выбор модели ИИ для чата",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        val isDefault = selectedLlmModelId == null && selectedOnlineProvider == null
        LlmOptionRow(
            label = "По умолчанию ($currentOnlineProvider)",
            subtitle = "Из настроек приложения",
            isSelected = isDefault,
            enabled = true,
            onClick = onReset
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Локальные модели (Офлайн)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            TextButton(
                onClick = onGoToModels,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
            ) {
                Icon(Icons.Default.Download, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Скачать", style = MaterialTheme.typography.labelSmall)
            }
        }

        if (allOfflineModels.isEmpty()) {
            Text(
                "Нет доступных моделей",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
            )
        } else {
            allOfflineModels.forEach { item ->
                val subtitle = when {
                    item.model.id == activeOfflineModelId -> "Активна"
                    item.isDownloaded -> "Загружена"
                    else -> "Не загружена — перейдите в раздел Модели"
                }
                LlmOptionRow(
                    label = item.model.title,
                    subtitle = subtitle,
                    isSelected = selectedLlmModelId == item.model.id,
                    enabled = item.isDownloaded,
                    onClick = { onSelectOffline(item.model.id) }
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text(
            "Онлайн-провайдеры",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        listOf("Gemini", "Groq").forEach { provider ->
            LlmOptionRow(
                label = provider,
                subtitle = if (provider == currentOnlineProvider) "Активен в настройках" else "Онлайн",
                isSelected = selectedOnlineProvider == provider,
                enabled = true,
                onClick = { onSelectOnline(provider) }
            )
        }
    }
}

@Composable
private fun LlmOptionRow(
    label: String,
    subtitle: String,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val contentAlpha = if (enabled) 1f else 0.4f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = isSelected, onClick = if (enabled) onClick else null, enabled = enabled)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MessageBubble — добавлен copy-to-clipboard по долгому нажатию
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == MessageRole.USER
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    // Сбрасываем метку "Скопировано" через 2 секунды
    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(2000)
            copied = false
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = if (isUser) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.combinedClickable(
                onClick = {},
                onLongClick = {
                    clipboard.setText(AnnotatedString(message.content))
                    copied = true
                }
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface
                )
                if (copied) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Скопировано",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isUser)
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
