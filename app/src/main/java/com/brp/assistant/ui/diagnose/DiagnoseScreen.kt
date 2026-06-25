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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.brp.assistant.data.db.enteties.BrpModel
import com.brp.assistant.domain.model.ChatMessage
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
    onBack: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    
    // Filter symptoms based on selected vehicle brand
    val filteredSymptoms = remember(selectedVehicle) {
        if (selectedVehicle == null) commonSymptoms
        else {
            val brand = selectedVehicle.brand.lowercase()
            commonSymptoms.filter { symptom ->
                val s = symptom.lowercase()
                when {
                    brand.contains("sea-doo") -> !s.contains("cvt") && !s.contains("ремень") && !s.contains("dct") && !s.contains("4wd")
                    brand.contains("can-am") -> !s.contains("ibr")
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

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
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

                item { SectionHeader("Популярные вопросы (${selectedVehicle?.brand ?: "BRP"}):") }

                items(filteredSymptoms) { symptom ->
                    OutlinedButton(
                        onClick = { onSend(symptom) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(symptom, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            items(messages) { msg ->
                val isUser = msg.role == com.brp.assistant.domain.model.MessageRole.USER
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
                        msg.content,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isUser) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface
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
            value = input, onValueChange = { input = it },
            onSend = { if (input.isNotBlank()) { onSend(input); input = "" } },
            enabled = isModelReady && !isGenerating,
            placeholder = "Опишите проблему с техникой…"
        )
    }
}
