package com.brp.assistant.ui.diagnose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.brp.assistant.data.db.entities.BrpModel
import com.brp.assistant.domain.model.ChatMessage
import com.brp.assistant.domain.model.EngineResultState
import com.brp.assistant.domain.model.FaultCard
import com.brp.assistant.domain.model.FaultSeverity
import com.brp.assistant.ui.components.ChatInputBar
import com.brp.assistant.ui.components.SafetyBanner
import com.brp.assistant.ui.components.SectionHeader
import com.brp.assistant.ui.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnoseScreen(
    selectedVehicle: BrpModel?,
    messages: List<ChatMessage>,
    riskLevel: String,
    requiresEvacuation: Boolean,
    isGenerating: Boolean,
    isModelReady: Boolean,
    commonSymptoms: List<String>,
    onSend: (String) -> Unit,
    onSelectVehicle: () -> Unit,
    onGoToSituations: () -> Unit,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: DiagnoseViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var input by remember { mutableStateOf("") }

    // ── Диалог OOM-предупреждения ─────────────────────────────────────────────
    var showOomDialog by remember { mutableStateOf(false) }
    var oomInfo by remember { mutableStateOf<EngineResultState.OomError?>(null) }

    if (showOomDialog && oomInfo != null) {
        AlertDialog(
            onDismissRequest = { showOomDialog = false },
            icon = { Icon(Icons.Default.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Недостаточно памяти") },
            text = {
                Text(
                    "Устройству не хватает RAM для запуска модели.\n\n" +
                    "Использовано: ${oomInfo!!.usedMb} МБ\n" +
                    "Доступно: ${oomInfo!!.availMb} МБ\n\n" +
                    "Попробуйте закрыть другие приложения или переключиться на более лёгкую модель в Настройках."
                )
            },
            confirmButton = {
                TextButton(onClick = { showOomDialog = false }) { Text("Понятно") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showOomDialog = false
                    onNavigate(Screen.ModelPicker.route)
                }) { Text("Выбрать модель") }
            }
        )
    }

    // Filter symptoms based on selected vehicle brand
    val filteredSymptoms = remember(selectedVehicle) {
        if (selectedVehicle == null) commonSymptoms
        else {
            val brand = selectedVehicle.brand.lowercase()
            commonSymptoms.filter { symptom ->
                val s = symptom.lowercase()
                when {
                    brand.contains("sea-doo") -> !s.contains("cvt") && !s.contains("ремень") && !s.contains("dct") && !s.contains("4wd")
                    brand.contains("can-am")  -> !s.contains("ibr")
                    brand.contains("ski-doo") || brand.contains("lynx") -> !s.contains("ibr") && !s.contains("4wd") && !s.contains("dct")
                    else -> true
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Диагностика ИИ") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
            },
            actions = {
                IconButton(onClick = { onNavigate(Screen.Home.route) }) {
                    Icon(Icons.Default.Home, null)
                }
            }
        )

        if (riskLevel != "low") {
            SafetyBanner(riskLevel, requiresEvacuation, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }

        // ── Баннер EngineResultState.Error ────────────────────────────────────
        when (val es = uiState.engineState) {
            is EngineResultState.Error -> {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                es.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        TextButton(onClick = { viewModel.clearEngineError() }) {
                            Text("Скрыть")
                        }
                    }
                }
            }
            is EngineResultState.OomError -> {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Memory,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Нехватка памяти",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                "Доступно ${es.availMb} МБ RAM. Переключитесь на лёгкую модель.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        TextButton(onClick = { onNavigate(Screen.ModelPicker.route) }) {
                            Text("Сменить")
                        }
                    }
                }
            }
            else -> Unit
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = 8.dp, bottom = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── Пустое состояние: нет сообщений ──────────────────────────────
            if (messages.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        onClick = onGoToSituations
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LibraryBooks, null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Офлайн инструкции", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Поиск решений без интернета", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                // ── FaultCard grid (когда карточки загружены) ────────────────
                if (uiState.filteredCards.isNotEmpty()) {
                    item { SectionHeader("Частые неисправности (${selectedVehicle?.brand ?: "BRP"}):"  ) }
                    items(uiState.filteredCards) { card ->
                        FaultCardItem(card = card, onSend = onSend)
                    }
                } else {
                    item { SectionHeader("Популярные вопросы (${selectedVehicle?.brand ?: "BRP"}):"  ) }
                    items(filteredSymptoms) { symptom ->
                        OutlinedButton(
                            onClick = {
                                // Проверка RAM перед отправкой в LLM
                                val oom = viewModel.checkRamBeforeInference()
                                if (oom != null) {
                                    oomInfo = oom
                                    showOomDialog = true
                                } else {
                                    onSend(symptom)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(symptom, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // ── Сообщения чата ────────────────────────────────────────────────
            items(messages) { msg ->
                val isUser = msg.role == com.brp.assistant.domain.model.MessageRole.USER
                Surface(
                    shape = RoundedCornerShape(
                        topStart = 16.dp, topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd   = if (isUser) 4.dp  else 16.dp
                    ),
                    color = if (isUser) MaterialTheme.colorScheme.primary
                            else        MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        msg.content,
                        modifier = Modifier.padding(12.dp),
                        style    = MaterialTheme.typography.bodyMedium,
                        color    = if (isUser) MaterialTheme.colorScheme.onPrimary
                                   else        MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (isGenerating) {
                item {
                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Анализирую проблему…", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        ChatInputBar(
            value    = input,
            onValueChange = { input = it },
            onSend   = {
                if (input.isNotBlank()) {
                    val oom = viewModel.checkRamBeforeInference()
                    if (oom != null) {
                        oomInfo = oom
                        showOomDialog = true
                    } else {
                        onSend(input)
                        input = ""
                    }
                }
            },
            enabled  = isModelReady && !isGenerating,
            placeholder = "Опишите проблему с техникой…"
        )
    }
}

/**
 * Карточка неисправности из BrpFaultCatalog.
 * Цвет индикатора зависит от severity.
 */
@Composable
private fun FaultCardItem(
    card: FaultCard,
    onSend: (String) -> Unit
) {
    val severityColor = when (card.severity) {
        FaultSeverity.CRITICAL -> MaterialTheme.colorScheme.error
        FaultSeverity.HIGH     -> Color(0xFFFF6D00)  // deep orange
        FaultSeverity.MEDIUM   -> Color(0xFFFFC107)  // amber
        FaultSeverity.LOW      -> MaterialTheme.colorScheme.primary
        FaultSeverity.INFO     -> MaterialTheme.colorScheme.outline
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        onClick   = { onSend(card.symptomQuery) }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Severity dot
            Surface(
                shape  = RoundedCornerShape(50),
                color  = severityColor,
                modifier = Modifier.size(10.dp)
            ) {}
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    card.title,
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (card.shortDescription.isNotBlank()) {
                    Text(
                        card.shortDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}
