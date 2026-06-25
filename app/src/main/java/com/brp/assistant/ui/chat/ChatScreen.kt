package com.brp.assistant.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.brp.assistant.domain.model.ChatMessage
import com.brp.assistant.domain.model.MessageRole
import com.brp.assistant.ui.components.ChatInputBar
import com.brp.assistant.ui.components.SafetyBanner
import com.brp.assistant.ui.navigation.Screen

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
    onBack: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var showLlmSheet by remember { mutableStateOf(false) }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val activeLlmLabel: String = when {
        selectedLlmModelId != null -> {
            allOfflineModels.find { it.model.id == selectedLlmModelId }?.model?.title
                ?: "Офлайн-модель"
        }
        selectedOnlineProvider != null -> selectedOnlineProvider
        activeOfflineModelId != null -> {
            allOfflineModels.find { it.model.id == activeOfflineModelId }?.model?.title
                ?: currentOnlineProvider
        }
        else -> currentOnlineProvider
    }

    Column(modifier = Modifier.fillMaxSize()) {
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
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
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
            contentPadding = PaddingValues(16.dp),
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

        // Сброс — глобальные настройки
        val isDefault = selectedLlmModelId == null && selectedOnlineProvider == null
        LlmOptionRow(
            label = "По умолчанию ($currentOnlineProvider)",
            subtitle = "Из настроек приложения",
            isSelected = isDefault,
            enabled = true,
            onClick = onReset
        )

        // ===== Локальные модели =====
        Divider(modifier = Modifier.padding(vertical = 8.dp))
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
                Icon(
                    Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("Скачать", style = MaterialTheme.typography.labelSmall)
            }
        }

        if (allOfflineModels.isEmpty()) {
            // Каталог пуст — такого быть не должно с PublicOfflineModelCatalog
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

        // ===== Онлайн-провайдеры =====
        Divider(modifier = Modifier.padding(vertical = 8.dp))
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
            .then(
                if (enabled) Modifier.clickable(onClick = onClick)
                else Modifier  // не кликабельный, если не загружено
            )
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = if (enabled) onClick else null,
            enabled = enabled
        )
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

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == MessageRole.USER
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
            else MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                message.content,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isUser) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
