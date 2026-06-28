package com.brp.assistant.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Non-blocking баннер для отображения проблем:
 *  — нет активной модели (офлайн + нет скачанных)
 *  — нет интернета
 *  — предупреждения от Health Check
 *
 * Отображается вверху экрана, закрывается кнопкой ×.
 */
@Composable
fun OfflineModeBanner(
    message: String,
    isWarning: Boolean = true,
    onDismiss: () -> Unit,
    onActionClick: (() -> Unit)? = null,
    actionLabel: String? = null,
    modifier: Modifier = Modifier
) {
    val containerColor = if (isWarning)
        MaterialTheme.colorScheme.errorContainer
    else
        MaterialTheme.colorScheme.tertiaryContainer
    val contentColor = if (isWarning)
        MaterialTheme.colorScheme.onErrorContainer
    else
        MaterialTheme.colorScheme.onTertiaryContainer
    val icon = if (isWarning) Icons.Filled.Warning else Icons.Filled.CloudOff

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = containerColor,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor,
                modifier = Modifier.weight(1f)
            )
            if (onActionClick != null && actionLabel != null) {
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = onActionClick,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = contentColor
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(actionLabel, color = contentColor, style = MaterialTheme.typography.labelSmall)
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Закрыть",
                    tint = contentColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Обёртка с анимированным появлением/исчезновением.
 */
@Composable
fun AnimatedOfflineModeBanner(
    visible: Boolean,
    message: String,
    isWarning: Boolean = true,
    onDismiss: () -> Unit,
    onActionClick: (() -> Unit)? = null,
    actionLabel: String? = null
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        OfflineModeBanner(
            message = message,
            isWarning = isWarning,
            onDismiss = onDismiss,
            onActionClick = onActionClick,
            actionLabel = actionLabel
        )
    }
}
