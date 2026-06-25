package com.brp.assistant.ui.accessory

import androidx.compose.foundation.background
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
import com.brp.assistant.data.db.enteties.Accessory
import com.brp.assistant.data.db.enteties.BrpModel
import com.brp.assistant.data.accessory.AccessoryData
import com.brp.assistant.domain.model.ChatMessage
import com.brp.assistant.ui.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessoryShopScreen(
    selectedVehicle: BrpModel?,
    messages: List<ChatMessage>,
    isGenerating: Boolean,
    isModelReady: Boolean,
    popularCategories: List<String>,
    suggestedAccessories: List<Accessory>,
    onSend: (String) -> Unit,
    onCategorySelect: (String) -> Unit,
    onSelectVehicle: () -> Unit,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit
) {
    val displayCategories = if (selectedVehicle != null) {
        listOf("Хранение LinQ", "Защита", "Комфорт", "Освещение", "Аудио")
    } else popularCategories

    val offlineAccessories = remember(selectedVehicle) {
        if (selectedVehicle != null) {
            AccessoryData.predefinedAccessories.filter { it.brand.contains(selectedVehicle.brand, ignoreCase = true) }
        } else AccessoryData.predefinedAccessories
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Аксессуары BRP") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                },
                actions = {
                    IconButton(onClick = { onNavigate(Screen.Home.route) }) {
                        Icon(Icons.Default.Home, "Главная")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (selectedVehicle != null) {
                item {
                    Text("Для вашей техники: ${selectedVehicle.modelName}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
            }

            item {
                Text("Категории (Офлайн)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
            }

            items(displayCategories) { category ->
                val catAccs = offlineAccessories.filter { it.category == category }
                if (catAccs.isNotEmpty()) {
                    Text(category, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 8.dp))
                    catAccs.forEach { acc ->
                        AccessoryCard(acc)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    onClick = { onSend("Помоги подобрать аксессуары") }
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Нужна помощь ИИ?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Обсудите подбор с экспертом онлайн", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccessoryCard(accessory: Accessory) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(accessory.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (accessory.isNew2026 == 1) {
                    Surface(color = MaterialTheme.colorScheme.error, shape = RoundedCornerShape(4.dp)) {
                        Text("NEW", modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Text("SKU: ${accessory.sku}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(accessory.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
