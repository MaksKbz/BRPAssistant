package com.brp.assistant.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

/**
 * Поле ввода сообщения с кнопкой отправки.
 * Используется в ChatScreen и DiagnosticScreen.
 */
@Composable
fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
    placeholder: String = "Введите сообщение…",
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyMedium) },
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(onSend = { if (value.isNotBlank() && enabled) onSend() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = onSend,
                enabled = enabled && value.isNotBlank(),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Default.Send, contentDescription = "Отправить")
            }
        }
    }
}

/**
 * #2 — Offline Mode Banner.
 *
 * Показывается в ChatScreen когда выбран онлайн-провайдер,
 * но API-ключ не настроен — приложение упадёт в ошибку при отправке.
 *
 * Дизайн: предупреждающий Card с иконкой WifiOff и ссылкой на настройки.
 * Не блокирует UI — пользователь может закрыть или перейти в настройки.
 *
 * @param providerName  Название провайдера («Gemini» / «Groq»).
 * @param onGoToSettings Переход в экран настроек AI-провайдера.
 * @param modifier      Стандартный Modifier.
 */
@Composable
fun OfflineModeBanner(
    providerName: String,
    onGoToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.WifiOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Ключ $providerName не настроен",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = "Онлайн-запросы будут завершаться ошибкой.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                )
            }
            TextButton(
                onClick = onGoToSettings,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    "Настройки",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * #4 — Safety Banner.
 *
 * Отображается в ChatScreen когда riskLevel != "low" (диагностический режим).
 * Цветовое кодирование:
 *   high   → errorContainer (красный)
 *   medium → tertiaryContainer (жёлтый/оранжевый)
 *   иначе  → secondaryContainer (нейтральный)
 *
 * Если requiresEvacuation = true — выводится иконка Warning и жирный
 * текст «Требуется эвакуация!» вместо стандартного уровня риска.
 *
 * @param level              Строковый уровень риска: "high", "medium", "low".
 * @param requiresEvacuation True если ситуация требует немедленной эвакуации.
 * @param modifier           Стандартный Modifier.
 */
@Composable
fun SafetyBanner(
    level: String,
    requiresEvacuation: Boolean,
    modifier: Modifier = Modifier
) {
    val containerColor = when (level) {
        "high"   -> MaterialTheme.colorScheme.errorContainer
        "medium" -> MaterialTheme.colorScheme.tertiaryContainer
        else     -> MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = when (level) {
        "high"   -> MaterialTheme.colorScheme.onErrorContainer
        "medium" -> MaterialTheme.colorScheme.onTertiaryContainer
        else     -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    val icon = if (requiresEvacuation) Icons.Default.Warning else Icons.Default.Info
    val label = when {
        requiresEvacuation -> "⚠️ Требуется эвакуация!"
        level == "high"    -> "Высокий уровень риска"
        level == "medium"  -> "Средний уровень риска"
        else               -> "Уровень риска: $level"
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (requiresEvacuation) FontWeight.Bold else FontWeight.Medium,
                color = contentColor
            )
        }
    }
}

/**
 * Простой заголовок секции внутри LazyColumn.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
