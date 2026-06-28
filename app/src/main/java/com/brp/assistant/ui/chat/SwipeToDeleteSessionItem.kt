package com.brp.assistant.ui.chat

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.brp.assistant.data.db.entities.ChatSessionEntity

/**
 * D3 — Элемент истории чатов со свайп-жестом «влево = удалить».
 *
 * Оборачивает произвольный [content] в [SwipeToDismissBox]:
 * • Свайп вправо → фон жёлтый (предупреждение), не активен.
 * • Свайп влево  → фон красный (удаление), вызывает [onDelete].
 *
 * Использование в компактной боковой панели:
 * ```kotlin
 * items(sessions) { session ->
 *     SwipeToDeleteSessionItem(
 *         session = session,
 *         onDelete = { vm.deleteSession(session.id) }
 *     ) {
 *         SessionRow(session = session, onClick = { ... })
 *     }
 * }
 * ```
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDeleteSessionItem(
    session: ChatSessionEntity,
    onDelete: (ChatSessionEntity) -> Unit,
    content: @Composable RowScope.() -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete(session)
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val color by animateColorAsState(
                targetValue = when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                    else -> Color.Transparent
                },
                label = "swipe_bg"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(end = 16.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Удалить",
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        },
        content = content
    )
}
