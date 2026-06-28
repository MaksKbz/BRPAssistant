@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.brp.assistant.ui.chat

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.brp.assistant.domain.model.ChatMessage
import com.brp.assistant.domain.model.MessageRole
import com.brp.assistant.ui.components.ChatInputBar
import com.brp.assistant.ui.components.OfflineModeBanner
import com.brp.assistant.ui.components.SafetyBanner
import com.brp.assistant.ui.navigation.Screen

// ─────────────────────────────────────────────────────────────────────────────
// #16 — HealthWarningBanner
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun HealthWarningBanner(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                message,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Закрыть",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// #16 — Левая панель истории (планшет / фолдабл) с поиском и swipe-delete
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ChatHistoryPanel(
    sessions: List<ChatSessionSummary>,
    selectedId: String?,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSelectSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onNewChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        // Заголовок + кнопка нового чата
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

        // #16 — Поле поиска
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            placeholder = {
                Text(
                    "Поиск…",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { onSearchQueryChange("") },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = "Очистить",
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            shape = RoundedCornerShape(8.dp)
        )

        HorizontalDivider(modifier = Modifier.padding(top = 4.dp))

        if (sessions.isEmpty()) {
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
                    if (searchQuery.isNotEmpty()) "Ничего не найдено"
                    else "Нет сохранённых\nдиалогов",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        } else {
            val grouped = remember(sessions.map { it.id + it.dateLabel }) {
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

                        // #16 — Swipe-to-delete
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.EndToStart) {
                                    onDeleteSession(session.id)
                                    true
                                } else false
                            }
                        )
                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = false,
                            enableDismissFromEndToStart = true,
                            backgroundContent = {
                                val bgColor by animateColorAsState(
                                    targetValue = if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart)
                                        MaterialTheme.colorScheme.errorContainer
                                    else Color.Transparent,
                                    label = "swipe_bg"
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(bgColor)
                                        .padding(end = 16.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Удалить",
                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        ) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clickable { onSelectSession(session.id) },
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.Center
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
                                    if (session.vehicleName != null) {
                                        Text(
                                            session.vehicleName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            maxLines = 1
                                        )
                                    }
                                    if (session.preview.isNotBlank()) {
                                        Text(
                                            session.preview,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            maxLines = 1
                                        )
                                    }
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
    allOfflineModels: List<OfflineModelUiItem>,
    activeOfflineModelId: String?,
    currentOnlineProvider: String,
    selectedLlmModelId: String?,
    selectedOnlineProvider: String?,
    /** #2 — true когда выбран онлайн-провайдер но API-ключ не задан */
    hasOnlineKeyMissing: Boolean = false,
    /** #12 — предупреждение от AppHealthChecker; null = всё хорошо */
    healthWarning: String? = null,
    /** #13 — текущий запрос поиска по истории */
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    onSelectOfflineLlm: (String?) -> Unit,
    onSelectOnlineLlm: (String) -> Unit,
    onResetLlm: () -> Unit,
    onSend: (String) -> Unit,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    widthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
    /** #13 — уже отфильтрованный список (filteredSessions из ViewModel) */
    sessionHistory: List<ChatSessionSummary> = emptyList(),
    selectedSessionId: String? = null,
    onSelectSession: (String) -> Unit = {},
    onNewChat: () -> Unit = {},
    /** #14 — удаление сессии свайпом */
    onDeleteSession: (String) -> Unit = {}
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var showLlmSheet by remember { mutableStateOf(false) }
    // #12 — локальный dismiss для healthWarning (не сбрасывает ViewModel)
    var healthDismissed by remember(healthWarning) { mutableStateOf(false) }

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

    val isTablet = widthSizeClass == WindowWidthSizeClass.Medium
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val imeVisible = WindowInsets.isImeVisible
    val hideTopBar = isLandscape && !isTablet && imeVisible

    @Composable
    fun ChatContent(modifier: Modifier = Modifier) {
        Column(modifier = modifier.fillMaxSize()) {
            if (!hideTopBar) {
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
            }

            // #12 — HealthWarningBanner
            if (healthWarning != null && !healthDismissed) {
                HealthWarningBanner(
                    message = healthWarning,
                    onDismiss = { healthDismissed = true },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }

            // #2 — OfflineModeBanner
            if (hasOnlineKeyMissing && selectedOnlineProvider != null) {
                OfflineModeBanner(
                    providerName = selectedOnlineProvider,
                    onGoToSettings = { onNavigate(Screen.ModelManager.route) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

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

            Box(modifier = Modifier.imePadding()) {
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
    }

    if (isTablet) {
        Row(modifier = Modifier.fillMaxSize()) {
            ChatHistoryPanel(
                sessions = sessionHistory,
                selectedId = selectedSessionId,
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                onSelectSession = onSelectSession,
                onDeleteSession = onDeleteSession,
                onNewChat = onNewChat,
                modifier = Modifier
                    .fillMaxHeight()
                    .width(280.dp)
            )
            VerticalDivider(thickness = 1.dp)
            ChatContent(modifier = Modifier.weight(1f))
        }
    } else {
        ChatContent()
    }

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
                    onNavigate(Screen.ModelManager.route)
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LlmSelectorSheet
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
// MessageBubble
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == MessageRole.USER
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

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
