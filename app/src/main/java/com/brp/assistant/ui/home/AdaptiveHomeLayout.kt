package com.brp.assistant.ui.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Top-level layout switcher for HomeScreen.
 *
 * - Phone (Compact):  single-column Column, safeDrawing bottom padding
 * - Tablet (Expanded / Medium): 2-column LazyVerticalGrid
 *
 * windowWidthSizeClass is provided by MainActivity via calculateWindowSizeClass();
 * it is more reliable than LocalConfiguration.screenWidthDp because it accounts
 * for multi-window, foldables, and system-bar exclusion zones.
 */
@Composable
fun AdaptiveHomeLayout(
    widthSizeClass: WindowWidthSizeClass,
    content: @Composable (columns: Int, itemModifier: Modifier) -> Unit
) {
    val isExpanded = widthSizeClass != WindowWidthSizeClass.Compact

    if (isExpanded) {
        // Tablet / large foldable: 2-column grid
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp)
        ) {
            content(
                /* columns    = */ 2,
                /* itemModifier= */ Modifier.padding(8.dp)
            )
        }
    } else {
        // Phone: single column with bottom nav inset handled by Scaffold
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            content(
                /* columns    = */ 1,
                /* itemModifier= */ Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Horizontal strip of symptom chips for quick diagnosis access.
 * Chips navigate to Chat in "diagnosis" mode with the symptom pre-filled.
 */
@Composable
fun QuickDiagnoseStrip(
    symptoms: List<String>,
    onSymptomTap: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        symptoms.forEach { symptom ->
            SuggestionChip(
                onClick = { onSymptomTap(symptom) },
                label = { Text(symptom, style = MaterialTheme.typography.labelMedium) },
                modifier = Modifier.height(40.dp)  // ensures >= 40dp tap target
            )
        }
    }
}
