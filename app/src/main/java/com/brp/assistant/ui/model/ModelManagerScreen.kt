package com.brp.assistant.ui.model

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brp.assistant.data.llm.OfflineModelInfo
import com.brp.assistant.ui.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagerScreen(
    state: ModelManagerState,
    onDownload: (OfflineModelInfo) -> Unit,
    onActivate: (OfflineModelInfo) -> Unit,
    onDelete: (OfflineModelInfo) -> Unit,
    onAddFromFile: (Uri, String) -> Unit,
    onAddFromUrl: (String, String) -> Unit,
    onUpdateApiKey: (String) -> Unit,
    onUpdateProvider: (String) -> Unit,
    onUpdateModel: (String) -> Unit,
    onUpdateSystemPrompt: (String) -> Unit,
    onUpdateTemperature: (Float) -> Unit,
    onClearError: () -> Unit,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit
) {
    // FIX #1: исправлена опечатка grokApiKey → groqApiKey.
    // Прежнее написание (без 'q') не компилировалось бы при строгой
    // типизации, либо ссылалось на несуществующее поле и возвращало null,
    // из-за чего поле API-ключа всегда оставалось пустым при смене провайдера.
    var apiKeyInput by remember(state.aiProvider) {
        mutableStateOf(if (state.aiProvider == "Gemini") state.geminiApiKey ?: "" else state.groqApiKey ?: "")
    }
    var showProviderMenu by remember { mutableStateOf(false) }
    var showModelMenu by remember { mutableStateOf(false) }
    var systemPromptInput by remember(state.aiSystemPrompt) { mutableStateOf(state.aiSystemPrompt) }
    var promptApplied by remember { mutableStateOf(false) }
    var showTestPrompt by remember { mutableStateOf(false) }

    val providers = listOf("Gemini", "Groq (Cloud)")
    val geminiModels = listOf("gemini-2.0-flash", "gemini-2.0-flash-lite", "gemini-1.5-flash", "gemini-1.5-pro")
    val groqModels = listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant", "mixtral-8x7b-32768")
    val currentModels = if (state.aiProvider == "Gemini") geminiModels else groqModels

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки ИИ", fontSize = 20.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = { onNavigate(Screen.Home.route) }) {
                        Icon(Icons.Default.Home, "Главная")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Подключение", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { showProviderMenu = true }
                    ) {
                        Icon(Icons.Default.SettingsEthernet, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Провайдер", style = MaterialTheme.typography.labelSmall)
                            Text(state.aiProvider, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        Icon(Icons.Default.ArrowDropDown, null)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { showModelMenu = true }
                    ) {
                        Icon(Icons.Default.Psychology, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Облачная модель", style = MaterialTheme.typography.labelSmall)
                            Text(state.aiModelName, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        Icon(Icons.Default.ArrowDropDown, null)
                    }

                    Column {
                        Text("API Ключ", style = MaterialTheme.typography.labelSmall)
                        OutlinedTextField(
                            value = apiKeyInput,
                            onValueChange = { apiKeyInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(fontSize = 14.sp),
                            shape = RoundedCornerShape(12.dp),
                            trailingIcon = {
                                if (state.isValidating) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                } else {
                                    IconButton(onClick = { onUpdateApiKey(apiKeyInput) }) {
                                        Icon(Icons.Default.FlashOn, "Verify", modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        )
                        if (state.validationResult != null) {
                            val isSuccess = state.validationResult == "SUCCESS"
                            Text(
                                text = if (isSuccess) "✅ Ключ проверен и активен" else "❌ Ошибка: ${state.validationResult}",
                                color = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            Text("Локальные модели", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Скачиваются напрямую как публичные файлы без API-ключей и без авторизации. Подходят для полностью офлайн-работы на телефоне.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            state.availableLocalModels.forEach { model ->
                LocalModelCard(
                    model = model,
                    isDownloaded = state.downloadedModels.contains(model.id),
                    isActive = state.activeModelId == model.id,
                    isDownloading = state.downloadingModelId == model.id,
                    downloadProgress = if (state.downloadingModelId == model.id) state.downloadProgress else 0f,
                    // FIX #2: передаём флаги активации в карточку модели.
                    // LocalModelCard теперь блокирует кнопку и показывает
                    // CircularProgressIndicator пока модель загружается в память.
                    isActivating = state.isActivating && state.activatingModelId == model.id,
                    onDownload = { onDownload(model) },
                    onActivate = { onActivate(model) },
                    onDelete = { onDelete(model) }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Тестовый промпт", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Switch(checked = showTestPrompt, onCheckedChange = { showTestPrompt = it })
            }

            if (showTestPrompt) {
                Card(shape = RoundedCornerShape(20.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Системная роль (Промпт)", style = MaterialTheme.typography.labelSmall)
                        OutlinedTextField(
                            value = systemPromptInput,
                            onValueChange = { systemPromptInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(fontSize = 14.sp),
                            minLines = 3,
                            maxLines = 10,
                            shape = RoundedCornerShape(12.dp)
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Button(
                                onClick = {
                                    onUpdateSystemPrompt(systemPromptInput)
                                    promptApplied = true
                                },
                                modifier = Modifier.height(40.dp)
                            ) {
                                Text(if (promptApplied) "✓ Сохранено" else "Применить", fontSize = 14.sp)
                            }
                        }
                        Text("Температура: ${"%.1f".format(state.aiTemperature)}", fontSize = 12.sp)
                        androidx.compose.material3.Slider(
                            value = state.aiTemperature,
                            onValueChange = { onUpdateTemperature(it) },
                            valueRange = 0f..1.5f
                        )
                    }
                }
            }

            Text("Инструменты", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ToolButton(Icons.Default.Chat, "Чат", { onNavigate(Screen.Chat.createRoute("both")) }, Modifier.weight(1f))
                ToolButton(Icons.Default.Build, "Диагностика", { onNavigate(Screen.Diagnose.route) }, Modifier.weight(1f))
            }

            if (state.error != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            state.error,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onClearError) { Text("OK") }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        DropdownMenu(expanded = showProviderMenu, onDismissRequest = { showProviderMenu = false }) {
            providers.forEach { p ->
                DropdownMenuItem(text = { Text(p) }, onClick = { onUpdateProvider(p); showProviderMenu = false })
            }
        }
        DropdownMenu(expanded = showModelMenu, onDismissRequest = { showModelMenu = false }) {
            currentModels.forEach { m ->
                DropdownMenuItem(text = { Text(m) }, onClick = { onUpdateModel(m); showModelMenu = false })
            }
        }
    }
}

@Composable
private fun LocalModelCard(
    model: OfflineModelInfo,
    isDownloaded: Boolean,
    isActive: Boolean,
    isDownloading: Boolean,
    downloadProgress: Float,
    // FIX #2: новый параметр — модель грузится в память (после нажатия «Активировать»)
    isActivating: Boolean,
    onDownload: () -> Unit,
    onActivate: () -> Unit,
    onDelete: () -> Unit
) {
    Card(shape = RoundedCornerShape(18.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Memory, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(model.title, fontWeight = FontWeight.Bold)
                    Text(
                        "${model.approxSizeMb} MB • RAM от ${model.minRamGb} GB • ${model.license}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isActive) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Активна", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                    }
                }
            }

            Text(model.description, style = MaterialTheme.typography.bodySmall)

            if (isDownloading) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinearProgressIndicator(progress = { downloadProgress }, modifier = Modifier.fillMaxWidth())
                    Text("Загрузка: ${(downloadProgress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                }
            }

            // FIX #2: индикатор активации — показывается пока модель читается с диска в RAM.
            // На Helio G85 / eMMC 5.1 это занимает до 30 секунд.
            // Без этого блока UI выглядит «замёрзшим», пользователь жмёт кнопку повторно.
            if (isActivating) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(
                        "Загрузка модели в память…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isDownloaded && !isDownloading) {
                    Button(onClick = onDownload) {
                        Icon(Icons.Default.CloudDownload, null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Скачать")
                    }
                }
                if (isDownloaded && !isActive) {
                    // FIX #2: кнопка заблокирована пока идёт активация этой модели.
                    // enabled=false предотвращает двойной вызов llmEngine.initialize()
                    // и race condition на устройствах с медленным I/O.
                    Button(onClick = onActivate, enabled = !isActivating) {
                        if (isActivating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(Icons.Default.PlayArrow, null)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isActivating) "Загрузка…" else "Активировать")
                    }
                }
                if (isDownloaded) {
                    // FIX #2: запрещаем удаление пока модель активируется.
                    TextButton(onClick = onDelete, enabled = !isActivating) {
                        Icon(Icons.Default.Delete, null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Удалить")
                    }
                }
            }
        }
    }
}

@Composable
fun ToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}
