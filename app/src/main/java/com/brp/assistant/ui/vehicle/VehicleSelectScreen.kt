package com.brp.assistant.ui.vehicle

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.brp.assistant.data.db.enteties.BrpModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleSelectScreen(
    brands: List<String>,
    categories: Map<String, List<String>>,
    subcategories: Map<String, List<String>>,
    models: List<BrpModel>,
    selectedBrand: String?,
    selectedCategory: String?,
    selectedSubcategory: String?,
    selectedModel: BrpModel?,
    onBrandSelect: (String) -> Unit,
    onCategorySelect: (String) -> Unit,
    onSubcategorySelect: (String) -> Unit,
    onModelSelect: (BrpModel) -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Выберите технику") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Step 1: Brand
            item {
                SectionTitle("1. Бренд")
                VerticalSelector(
                    items = brands,
                    selectedItem = selectedBrand,
                    onSelect = onBrandSelect,
                    labelProvider = { brand ->
                        when(brand) {
                            "can-am-ssv" -> "Can-Am SSV"
                            "can-am-atv" -> "Can-Am ATV"
                            "can-am-3wheel" -> "Can-Am 3-Wheel"
                            "ski-doo" -> "Ski-Doo"
                            "sea-doo" -> "Sea-Doo"
                            "lynx" -> "Lynx"
                            else -> brand.uppercase()
                        }
                    }
                )
            }

            // Step 2: Category
            if (selectedBrand != null) {
                val cats = categories[selectedBrand] ?: emptyList()
                if (cats.isNotEmpty()) {
                    item {
                        SectionTitle("2. Тип")
                        VerticalSelector(
                            items = cats,
                            selectedItem = selectedCategory,
                            onSelect = onCategorySelect,
                            labelProvider = { formatCategory(it) }
                        )
                    }
                }
            }

            // Step 3: Subcategory
            if (selectedCategory != null) {
                val key = "$selectedBrand/$selectedCategory"
                val subs = subcategories[key] ?: emptyList()
                if (subs.isNotEmpty()) {
                    item {
                        SectionTitle("3. Подкатегория")
                        VerticalSelector(
                            items = subs,
                            selectedItem = selectedSubcategory,
                            onSelect = onSubcategorySelect,
                            labelProvider = { formatCategory(it) }
                        )
                    }
                }
            }

            // Step 4: Models
            if (models.isNotEmpty()) {
                item { SectionTitle("4. Модели 2026") }
                items(models) { model ->
                    ModelItem(
                        model = model,
                        isSelected = selectedModel?.id == model.id,
                        onSelect = { onModelSelect(model) }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        enabled = selectedModel != null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text("Подтвердить выбор", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun <T> VerticalSelector(
    items: List<T>,
    selectedItem: T?,
    onSelect: (T) -> Unit,
    labelProvider: (T) -> String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column {
            items.forEachIndexed { index, item ->
                val isSelected = item == selectedItem
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(item) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = labelProvider(item),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    if (isSelected) {
                        Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                if (index < items.size - 1) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun ModelItem(model: BrpModel, isSelected: Boolean, onSelect: () -> Unit) {
    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                             else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 2.dp else 0.dp),
        border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(model.modelName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "${model.engineName ?: ""} ${model.horsepower?.let { "$it hp" } ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isSelected) {
                Icon(Icons.Default.RadioButtonChecked, null, tint = MaterialTheme.colorScheme.primary)
            } else {
                Icon(Icons.Default.RadioButtonUnchecked, null, tint = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

private fun formatCategory(cat: String): String = when(cat.lowercase()) {
    "ssv" -> "Side-by-Side (SSV)"
    "atv" -> "All-Terrain (ATV)"
    "3-wheel" -> "3-Wheel"
    "snowmobile" -> "Снегоходы"
    "pwc" -> "Гидроциклы"
    "sport" -> "Спорт"
    "utility" -> "Утилитарные"
    "touring" -> "Туринг"
    "mountain" -> "Горные"
    "crossover" -> "Кроссовер"
    "fishing" -> "Рыбалка"
    "performance" -> "Производительность"
    "rec-lite" -> "Rec Lite"
    "recreation" -> "Рекреация"
    "trail" -> "Trail"
    "deep-snow" -> "Deep Snow"
    else -> cat.replaceFirstChar { it.uppercase() }
}
