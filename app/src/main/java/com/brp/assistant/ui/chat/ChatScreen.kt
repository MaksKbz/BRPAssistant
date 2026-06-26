package com.brp.assistant.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.brp.assistant.domain.model.ChatMessage
import com.brp.assistant.domain.model.MessageRole
import com.brp.assistant.ui.components.ChatInputBar
import com.brp.assistant.ui.components.SafetyBanner
import com.brp.assistant.ui.navigation.Screen
import com.brp.assistant.ui.navigation.rememberWindowSizeClass
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    title: String = "\u0427\u0430\u0442",
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
    onSelectOfflineLlm: (String?) -> Unit,
    onSelectOnlineLlm: (String) -> Unit,
    onResetLlm: () -> Unit,
    onSend: (String) -> Unit,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    error: String? = null,
    onDismissError: () -> Unit = {}
) {
    val windowSizeClass = rememberWindowSizeClass()
    val isExpanded = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded
    if (isExpanded) {
        TwoPaneChatLayout(
            title, messages, riskLevel, requiresEvacuation, isGenerating, isModelReady,
            selectedVehicleName, allOfflineModels, activeOfflineModelId, currentOnlineProvider,
            selectedLlmModelId, selectedOnlineProvider,
            onSelectOfflineLlm, onSelectOnlineLlm, onResetLlm,
            onSend, onNavigate, onBack, error, onDismissError
        )
    } else {
        SinglePaneChatLayout(
            title, messages, riskLevel, requiresEvacuation, isGenerating, isModelReady,
            selectedVehicleName, allOfflineModels, activeOfflineModelId, currentOnlineProvider,
            selectedLlmModelId, selectedOnlineProvider,
            onSelectOfflineLlm, onSelectOnlineLlm, onResetLlm,
            onSend, onNavigate, onBack, error, onDismissError
        )
    }
}

// ───────────────────────────── SINGLE PANE ─────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SinglePaneChatLayout(
    title: String, messages: List<ChatMessage>,
    riskLevel: String, requiresEvacuation: Boolean,
    isGenerating: Boolean, isModelReady: Boolean,
    selectedVehicleName: String?,
    allOfflineModels: List<OfflineModelUiItem>, activeOfflineModelId: String?,
    currentOnlineProvider: String, selectedLlmModelId: String?, selectedOnlineProvider: String?,
    onSelectOfflineLlm: (String?) -> Unit, onSelectOnlineLlm: (String) -> Unit, onResetLlm: () -> Unit,
    onSend: (String) -> Unit, onNavigate: (String) -> Unit, onBack: () -> Unit,
    error: String?, onDismissError: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    var showLlmSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val activeLlmLabel = rememberActiveLlmLabel(
        selectedLlmModelId, selectedOnlineProvider, activeOfflineModelId, allOfflineModels, currentOnlineProvider
    )
    LaunchedEffect(error) {
        if (error != null) {
            val r = snackbarHostState.showSnackbar(error, "OK", duration = SnackbarDuration.Long)
            if (r == SnackbarResult.ActionPerformed) onDismissError()
        }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ChatTopBar(title, selectedVehicleName, activeLlmLabel,
                { showLlmSheet = true }, { onNavigate(Screen.Home.route) }, onBack)
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            if (riskLevel != "low") {
                SafetyBanner(riskLevel, requiresEvacuation,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            MessageList(messages, isGenerating, Modifier.weight(1f))
            ChatInputBar(
                value = input, onValueChange = { input = it },
                onSend = { if (input.isNotBlank()) { onSend(input); input = "" } },
                enabled = isModelReady && !isGenerating,
                placeholder = "\u0417\u0430\u0434\u0430\u0439\u0442\u0435 \u0432\u043e\u043f\u0440\u043e\u0441\u2026"
            )
        }
    }
    if (showLlmSheet) {
        ModalBottomSheet(onDismissRequest = { showLlmSheet = false }) {
            LlmSelectorSheet(
                allOfflineModels, activeOfflineModelId, currentOnlineProvider,
                selectedLlmModelId, selectedOnlineProvider,
                { id -> onSelectOfflineLlm(id); showLlmSheet = false },
                { p  -> onSelectOnlineLlm(p);  showLlmSheet = false },
                { onResetLlm(); showLlmSheet = false },
                { showLlmSheet = false; onNavigate(Screen.Models.route) }
            )
        }
    }
}

// ───────────────────────────── TWO PANE ────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TwoPaneChatLayout(
    title: String, messages: List<ChatMessage>,
    riskLevel: String, requiresEvacuation: Boolean,
    isGenerating: Boolean, isModelReady: Boolean,
    selectedVehicleName: String?,
    allOfflineModels: List<OfflineModelUiItem>, activeOfflineModelId: String?,
    currentOnlineProvider: String, selectedLlmModelId: String?, selectedOnlineProvider: String?,
    onSelectOfflineLlm: (String?) -> Unit, onSelectOnlineLlm: (String) -> Unit, onResetLlm: () -> Unit,
    onSend: (String) -> Unit, onNavigate: (String) -> Unit, onBack: () -> Unit,
    error: String?, onDismissError: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    var showLlmSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val historyListState = rememberLazyListState()
    val activeLlmLabel = rememberActiveLlmLabel(
        selectedLlmModelId, selectedOnlineProvider, activeOfflineModelId, allOfflineModels, currentOnlineProvider
    )
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) historyListState.animateScrollToItem(messages.size - 1)
    }
    LaunchedEffect(error) {
        if (error != null) {
            val r = snackbarHostState.showSnackbar(error, "OK", duration = SnackbarDuration.Long)
            if (r == SnackbarResult.ActionPerformed) onDismissError()
        }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ChatTopBar(title, selectedVehicleName, activeLlmLabel,
                { showLlmSheet = true }, { onNavigate(Screen.Home.route) }, onBack)
        }
    ) { pad ->
        Row(Modifier.fillMaxSize().padding(pad)) {
            // Left pane: message history list
            Surface(Modifier.width(320.dp).fillMaxHeight(), tonalElevation = 2.dp) {
                Column(Modifier.fillMaxSize()) {
                    Text(
                        "\u0418\u0441\u0442\u043e\u0440\u0438\u044f \u0434\u0438\u0430\u043b\u043e\u0433\u0430",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                    HorizontalDivider()
                    if (messages.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("\u041d\u0435\u0442 \u0441\u043e\u043e\u0431\u0449\u0435\u043d\u0438\u0439",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(state = historyListState, Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 4.dp)) {
                            items(messages) { msg -> MessageHistoryRow(msg) }
                        }
                    }
                }
            }
            VerticalDivider()
            // Right pane: active thread + input
            Column(Modifier.weight(1f).fillMaxHeight()) {
                if (riskLevel != "low") {
                    SafetyBanner(riskLevel, requiresEvacuation,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }
                MessageList(messages, isGenerating, Modifier.weight(1f))
                ChatInputBar(
                    value = input, onValueChange = { input = it },
                    onSend = { if (input.isNotBlank()) { onSend(input); input = "" } },
                    enabled = isModelReady && !isGenerating,
                    placeholder = "\u0417\u0430\u0434\u0430\u0439\u0442\u0435 \u0432\u043e\u043f\u0440\u043e\u0441\u2026"
                )
            }
        }
    }
    if (showLlmSheet) {
        ModalBottomSheet(onDismissRequest = { showLlmSheet = false }) {
            LlmSelectorSheet(
                allOfflineModels, activeOfflineModelId, currentOnlineProvider,
                selectedLlmModelId, selectedOnlineProvider,
                { id -> onSelectOfflineLlm(id); showLlmSheet = false },
                { p  -> onSelectOnlineLlm(p);  showLlmSheet = false },
                { onResetLlm(); showLlmSheet = false },
                { showLlmSheet = false; onNavigate(Screen.Models.route) }
            )
        }
    }
}

// ───────────────────────────── SHARED COMPONENTS ───────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    title: String, vehicleName: String?, activeLlmLabel: String,
    onLlmClick: () -> Unit, onHomeClick: () -> Unit, onBack: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (vehicleName != null)
                    Text(vehicleName, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
        actions = {
            TextButton(onClick = onLlmClick, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Icon(Icons.Default.SmartToy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(activeLlmLabel, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
            IconButton(onClick = onHomeClick) { Icon(Icons.Default.Home, null) }
        }
    )
}

@Composable
private fun MessageList(messages: List<ChatMessage>, isGenerating: Boolean, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }
    if (messages.isEmpty() && !isGenerating) { EmptyChatState(modifier); return }
    LazyColumn(
        state = listState, modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(messages) { msg -> MessageBubble(msg) }
        if (isGenerating) item { TypingIndicator() }
    }
}

@Composable
private fun EmptyChatState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.Chat, null, Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            Text("\u0417\u0430\u0434\u0430\u0439\u0442\u0435 \u0432\u043e\u043f\u0440\u043e\u0441 \u043e \u0442\u0435\u0445\u043d\u0438\u043a\u0435 BRP",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("\u0414\u0438\u0430\u0433\u043d\u043e\u0441\u0442\u0438\u043a\u0430, \u0442\u0435\u0445\u043e\u0431\u0441\u043b\u0443\u0436\u0438\u0432\u0430\u043d\u0438\u0435,\n\u043f\u043e\u0434\u0431\u043e\u0440 \u0430\u043a\u0441\u0435\u0441\u0441\u0443\u0430\u0440\u043e\u0432 \u0438 \u043c\u043d\u043e\u0433\u043e\u0435 \u0434\u0440\u0443\u0433\u043e\u0435",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun TypingIndicator() {
    val transition = rememberInfiniteTransition(label = "typing")
    val phase by transition.animateFloat(
        initialValue = 0f, targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "phase"
    )
    Row(Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp)) {
        Surface(
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                val dotColor = MaterialTheme.colorScheme.onSurfaceVariant
                repeat(3) { idx ->
                    val yOffset = (sin((phase + idx * 0.8f).toDouble()) * 4.0).toFloat()
                    Box(Modifier.size(6.dp).offset(y = (-yOffset).dp).clip(CircleShape).background(dotColor))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == MessageRole.USER
    val context = LocalContext.current
    val timeLabel = remember { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()) }
    Column(Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd   = if (isUser) 4.dp  else 16.dp
            ),
            color = if (isUser) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.combinedClickable(
                onClick = {},
                onLongClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("BRP", message.content))
                }
            )
        ) {
            SelectionContainer {
                Text(
                    message.content,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp).widthIn(max = 320.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Text(timeLabel, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
    }
}

@Composable
private fun MessageHistoryRow(message: ChatMessage) {
    val isUser = message.role == MessageRole.USER
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            if (isUser) Icons.Default.Person else Icons.Default.SmartToy, null,
            Modifier.size(16.dp),
            tint = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
        )
        Text(message.content, style = MaterialTheme.typography.bodySmall,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun rememberActiveLlmLabel(
    selectedLlmModelId: String?, selectedOnlineProvider: String?,
    activeOfflineModelId: String?, allOfflineModels: List<OfflineModelUiItem>,
    currentOnlineProvider: String
): String = remember(selectedLlmModelId, selectedOnlineProvider, activeOfflineModelId,
    allOfflineModels, currentOnlineProvider) {
    when {
        selectedLlmModelId != null ->
            allOfflineModels.find { it.model.id == selectedLlmModelId }?.model?.title ?: "\u041e\u0444\u043b\u0430\u0439\u043d"
        selectedOnlineProvider != null -> selectedOnlineProvider
        activeOfflineModelId != null ->
            allOfflineModels.find { it.model.id == activeOfflineModelId }?.model?.title ?: currentOnlineProvider
        else -> currentOnlineProvider
    }
}

// ───────────────────────────── LLM SELECTOR SHEET ──────────────────────

@Composable
private fun LlmSelectorSheet(
    allOfflineModels: List<OfflineModelUiItem>, activeOfflineModelId: String?,
    currentOnlineProvider: String, selectedLlmModelId: String?, selectedOnlineProvider: String?,
    onSelectOffline: (String) -> Unit, onSelectOnline: (String) -> Unit,
    onReset: () -> Unit, onGoToModels: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
        Text("\u0412\u044b\u0431\u043e\u0440 \u043c\u043e\u0434\u0435\u043b\u0438 \u0418\u0418 \u0434\u043b\u044f \u0447\u0430\u0442\u0430",
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 12.dp))
        val isDefault = selectedLlmModelId == null && selectedOnlineProvider == null
        LlmOptionRow("\u041f\u043e \u0443\u043c\u043e\u043b\u0447\u0430\u043d\u0438\u044e ($currentOnlineProvider)",
            "\u0418\u0437 \u043d\u0430\u0441\u0442\u0440\u043e\u0435\u043a \u043f\u0440\u0438\u043b\u043e\u0436\u0435\u043d\u0438\u044f", isDefault, true, onReset)
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("\u041b\u043e\u043a\u0430\u043b\u044c\u043d\u044b\u0435 \u043c\u043e\u0434\u0435\u043b\u0438",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            TextButton(onClick = onGoToModels, contentPadding = PaddingValues(4.dp, 0.dp)) {
                Icon(Icons.Default.Download, null, Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("\u0421\u043a\u0430\u0447\u0430\u0442\u044c", style = MaterialTheme.typography.labelSmall)
            }
        }
        if (allOfflineModels.isEmpty()) {
            Text("\u041d\u0435\u0442 \u043c\u043e\u0434\u0435\u043b\u0435\u0439", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(8.dp))
        } else {
            allOfflineModels.forEach { item ->
                val sub = when {
                    item.model.id == activeOfflineModelId -> "\u0410\u043a\u0442\u0438\u0432\u043d\u0430"
                    item.isDownloaded -> "\u0417\u0430\u0433\u0440\u0443\u0436\u0435\u043d\u0430"
                    else -> "\u041d\u0435 \u0437\u0430\u0433\u0440\u0443\u0436\u0435\u043d\u0430"
                }
                LlmOptionRow(item.model.title, sub,
                    selectedLlmModelId == item.model.id, item.isDownloaded,
                    { onSelectOffline(item.model.id) })
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("\u041e\u043d\u043b\u0430\u0439\u043d-\u043f\u0440\u043e\u0432\u0430\u0439\u0434\u0435\u0440\u044b",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp))
        listOf("Gemini", "Groq").forEach { p ->
            LlmOptionRow(p,
                if (p == currentOnlineProvider) "\u0410\u043a\u0442\u0438\u0432\u0435\u043d" else "\u041e\u043d\u043b\u0430\u0439\u043d",
                selectedOnlineProvider == p, true) { onSelectOnline(p) }
        }
    }
}

@Composable
private fun LlmOptionRow(label: String, subtitle: String, isSelected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val alpha = if (enabled) 1f else 0.4f
    Row(
        Modifier.fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(isSelected, if (enabled) onClick else null, enabled = enabled)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
            Text(subtitle, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
        }
    }
}