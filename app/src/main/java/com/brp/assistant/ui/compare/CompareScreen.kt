package com.brp.assistant.ui.compare

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.brp.assistant.data.db.enteties.BrpModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareScreen(
    allModels: List<BrpModel>,
    selectedModels: List<BrpModel>,
    showLimitToast: Boolean,
    onToggleModel: (BrpModel) -> Unit,
    onClearSelection: () -> Unit,
    onCompareWithAI: () -> Unit,
    onDismissLimitToast: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // D1 — показываем Toast и сразу сбрасываем флаг
    LaunchedEffect(showLimitToast) {
        if (showLimitToast) {
            Toast.makeText(
                context,
                "Максимум 4 модели для сравнения",
                Toast.LENGTH_SHORT
            ).show()
            onDismissLimitToast()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            // D1 — обновляем подпись /3 → /4
            title = { Text("Сравнение моделей (${selectedModels.size}/4)") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
            },
            actions = {
                if (selectedModels.size >= 2) {
                    TextButton(onClick = onCompareWithAI) {
                        Icon(Icons.Default.Chat, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Спросить ИИ")
                    }
                }
            }
        )

        Column(modifier = Modifier.padding(16.dp)) {
            if (selectedModels.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(selectedModels) { model ->
                        InputChip(
                            selected = true,
                            onClick = { onToggleModel(model) },
                            label = { Text(model.modelName, style = MaterialTheme.typography.labelMedium) },
                            trailingIcon = { Icon(Icons.Default.Close, "Убрать", modifier = Modifier.size(16.dp)) }
                        )
                    }
                    item {
                        if (selectedModels.isNotEmpty()) {
                            TextButton(onClick = onClearSelection) { Text("Очистить") }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (selectedModels.size >= 2) {
                ComparisonTable(models = selectedModels)
                Spacer(modifier = Modifier.height(12.dp))
            }

            Text("Выберите модели для сравнения:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(allModels) { model ->
                    val isSelected = selectedModels.any { it.id == model.id }
                    Card(
                        onClick = { onToggleModel(model) },
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = isSelected, onCheckedChange = { onToggleModel(model) })
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    model.modelName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    "${model.engineName ?: ""} ${model.horsepower?.let { "$it HP" } ?: ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComparisonTable(models: List<BrpModel>) {
    Card(shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("Параметр", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                models.forEach { m ->
                    Text(m.modelName, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            val rows = listOf(
                "Двигатель" to models.map { it.engineName ?: "—" },
                "Мощность" to models.map { it.horsepower?.let { "$it л.с." } ?: "—" },
                "Объём" to models.map { it.displacementCc?.let { "${it}cc" } ?: "—" },
                "Трансмиссия" to models.map { it.transmission ?: "—" },
                "Привод" to models.map { it.driveType ?: "—" },
                "Электро" to models.map { if (it.isElectric == 1) "⚡ Да" else "Нет" }
            )

            rows.forEach { (label, values) ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    values.forEach { v ->
                        Text(v, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
