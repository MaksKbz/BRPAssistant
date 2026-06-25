package com.brp.assistant.ui.situations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.sp
import com.brp.assistant.data.db.enteties.BrpModel
import com.brp.assistant.data.db.enteties.KnowledgeCard
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SituationsScreen(
    categories: List<String>,
    nodes: List<String>,
    cards: List<KnowledgeCard>,
    selectedCategory: String?,
    selectedNode: String?,
    selectedVehicle: BrpModel?,
    onCategorySelect: (String) -> Unit,
    onNodeSelect: (String, Boolean) -> Unit,
    onBack: () -> Unit
) {
    var selectedReport by remember(cards) { mutableStateOf<KnowledgeCard?>(null) }
    
    val isElectric = selectedVehicle?.isElectric == 1 || selectedVehicle?.modelName?.lowercase()?.contains("electric") == true

    // Filter categories to only show selected vehicle's brand if selected
    val displayCategories = remember(categories, selectedVehicle) {
        if (selectedVehicle != null) {
            // Special case for electric
            if (isElectric) categories.filter { it.contains("Electric", ignoreCase = true) || it.contains("Universal", ignoreCase = true) }
            else categories.filter { it.contains(selectedVehicle.brand, ignoreCase = true) && !it.contains("Electric", ignoreCase = true) }
        } else categories
    }

    // Effect to auto-select first category if none selected
    LaunchedEffect(displayCategories) {
        if (selectedCategory == null && displayCategories.isNotEmpty()) {
            onCategorySelect(displayCategories.first())
        }
    }

    // Effect to auto-select first node if category changed
    LaunchedEffect(selectedCategory, nodes) {
        if (selectedCategory != null && (selectedNode == null || !nodes.contains(selectedNode)) && nodes.isNotEmpty()) {
            onNodeSelect(nodes.first(), isElectric)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Инструкции и Решения", fontSize = 20.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedReport != null) selectedReport = null
                        else onBack()
                    }) { Icon(Icons.Default.ArrowBack, null) }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            if (selectedReport != null) {
                SituationDetail(selectedReport!!)
            } else {
                if (selectedVehicle != null) {
                    Text("Для вашей техники: ${selectedVehicle.modelName}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (categories.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    ScrollableTabRow(
                        selectedTabIndex = displayCategories.indexOf(selectedCategory).coerceAtLeast(0),
                        edgePadding = 0.dp,
                        containerColor = Color.Transparent,
                        divider = {}
                    ) {
                        displayCategories.forEach { category ->
                            Tab(
                                selected = selectedCategory == category,
                                onClick = { onCategorySelect(category) },
                                text = { Text(category, maxLines = 1, fontSize = 14.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (selectedCategory != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            nodes.forEach { node ->
                                FilterChip(
                                    selected = selectedNode == node,
                                    onClick = { onNodeSelect(node, isElectric) },
                                    label = { Text(node, fontSize = 14.sp) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (cards.isEmpty()) {
                        Text("Нет инструкций для данного узла", fontSize = 14.sp, color = Color.Gray)
                    }

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(cards) { card ->
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable { selectedReport = card },
                                colors = CardDefaults.cardColors(
                                    containerColor = when(card.riskLevel) {
                                        "critical" -> MaterialTheme.colorScheme.errorContainer
                                        "high" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    }
                                )
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(card.symptom, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    Text("Узел: ${card.node}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SituationDetail(card: KnowledgeCard) {
    val context = androidx.compose.ui.platform.LocalContext.current
    
    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text(card.symptom, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (card.requiresEvacuation == 1 || card.riskLevel == "critical" || card.riskLevel == "high") {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("⚠️ ТРЕБУЕТСЯ СЕРВИС", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        Text("Данная проблема требует профессионального вмешательства. Свяжитесь с нами прямо сейчас.", fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = { 
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://wa.me/77014670955"))
                                    context.startActivity(intent)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("WhatsApp", color = Color.White)
                            }
                            OutlinedButton(
                                onClick = {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:+77022149470"))
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Звонок")
                            }
                        }
                    }
                }
            }
        }
        item { DetailSection("Возможные причины", card.causes) }
        item { DetailSection("Шаги по исправлению", card.steps) }
        item { DetailSection("Что можно сделать", card.canDo) }
        item { DetailSection("Что ЗАПРЕЩЕНО делать", card.mustNotDo, isWarning = true) }
        item {
            Text("Полная информация:", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(card.fullText, fontSize = 16.sp)
        }
    }
}

@Composable
fun DetailSection(title: String, jsonArrayStr: String, isWarning: Boolean = false) {
    Column {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (isWarning) MaterialTheme.colorScheme.error else Color.Unspecified)
        val list = try {
            Json.parseToJsonElement(jsonArrayStr).jsonArray.map { it.jsonPrimitive.content }
        } catch (e: Exception) {
            listOf(jsonArrayStr)
        }
        list.forEach { item ->
            Row(modifier = Modifier.padding(vertical = 4.dp)) {
                Text("• ", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(item, fontSize = 16.sp)
            }
        }
    }
}
