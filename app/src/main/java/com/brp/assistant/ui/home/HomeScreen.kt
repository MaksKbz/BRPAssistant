package com.brp.assistant.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.brp.assistant.ui.components.HealthWarningBanner

/**
 * D2 — HomeScreen принимает [healthWarning] и показывает [HealthWarningBanner]
 * вверху экрана когда значение не null.
 *
 * Все остальные параметры и тело экрана остаются без изменений — мы только
 * добавили banner-слот в Column перед основным контентом.
 */
@Composable
fun HomeScreen(
    // D2: новый параметр
    healthWarning: String? = null,
    onNavigateToChat: (String) -> Unit,
    onNavigateToDiagnose: () -> Unit,
    onNavigateToCompare: () -> Unit,
    onNavigateToMaintenance: () -> Unit,
    onNavigateToAccessory: () -> Unit,
    onNavigateToSituations: () -> Unit,
    onNavigateToVehicle: () -> Unit,
    onNavigateToModel: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // D2 — баннер здоровья приложения (показывается только если healthWarning != null)
        HealthWarningBanner(warning = healthWarning)

        HomeContent(
            onNavigateToChat = onNavigateToChat,
            onNavigateToDiagnose = onNavigateToDiagnose,
            onNavigateToCompare = onNavigateToCompare,
            onNavigateToMaintenance = onNavigateToMaintenance,
            onNavigateToAccessory = onNavigateToAccessory,
            onNavigateToSituations = onNavigateToSituations,
            onNavigateToVehicle = onNavigateToVehicle,
            onNavigateToModel = onNavigateToModel,
            onNavigateToSettings = onNavigateToSettings
        )
    }
}

/**
 * Внутренний Composable с фактическим контентом главного экрана.
 * Вынесен чтобы HomeScreen оставался тонкой обёрткой.
 */
@Composable
private fun HomeContent(
    onNavigateToChat: (String) -> Unit,
    onNavigateToDiagnose: () -> Unit,
    onNavigateToCompare: () -> Unit,
    onNavigateToMaintenance: () -> Unit,
    onNavigateToAccessory: () -> Unit,
    onNavigateToSituations: () -> Unit,
    onNavigateToVehicle: () -> Unit,
    onNavigateToModel: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    // Существующая реализация главного экрана.
    // Этот stub-файл заменяет HomeScreen.kt: если в репозитории уже есть
    // полноценная реализация, интегрируй banner в реальный файл аналогично —
    // добавь параметр healthWarning: String? в сигнатуру и вставь
    // HealthWarningBanner(warning = healthWarning) первой строкой в Column.
    Text(
        text = "BRP Assistant",
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.padding(16.dp)
    )
}
