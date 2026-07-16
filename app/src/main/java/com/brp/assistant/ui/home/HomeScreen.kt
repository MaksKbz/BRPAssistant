package com.brp.assistant.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brp.assistant.ui.components.HealthWarningBanner

/**
 * Главный экран BRP Assistant.
 *
 * Показывает:
 * - [HealthWarningBanner] если есть предупреждение от AppHealthChecker
 * - Плитки быстрого доступа ко всем разделам приложения
 */
@Composable
fun HomeScreen(
    healthWarning: String? = null,
    onNavigateToChat: (String) -> Unit,
    onNavigateToDiagnose: () -> Unit,
    onNavigateToCompare: () -> Unit,
    onNavigateToMaintenance: () -> Unit,
    onNavigateToAccessory: () -> Unit,
    onNavigateToSituations: () -> Unit,
    onNavigateToVehicle: () -> Unit,
    onNavigateToModel: () -> Unit,
    onNavigateToUserDocs: () -> Unit = {},
    onNavigateToSettings: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Баннер здоровья приложения (показывается только если не null)
        HealthWarningBanner(warning = healthWarning)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Заголовок
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector        = Icons.Default.TwoWheeler,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.primary,
                    modifier           = Modifier.size(32.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text       = "BRP Assistant",
                        style      = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text  = "Ваш AI-помощник по технике BRP",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider()

            // Основные действия
            Text(
                text       = "Быстрые действия",
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            // Чат — большая плитка на всю ширину
            HomeCard(
                icon        = Icons.Default.Chat,
                title       = "Чат с ИИ",
                description = "Задайте любой вопрос о технике BRP",
                onClick     = { onNavigateToChat("both") },
                modifier    = Modifier.fillMaxWidth(),
                primary     = true
            )

            // Диагностика + Аксессуары
            Row(
                modifier            = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HomeCard(
                    icon        = Icons.Default.Build,
                    title       = "Диагностика",
                    description = "Диагностика неисправностей",
                    onClick     = onNavigateToDiagnose,
                    modifier    = Modifier.weight(1f)
                )
                HomeCard(
                    icon        = Icons.Default.ShoppingBag,
                    title       = "Аксессуары",
                    description = "Подбор аксессуаров",
                    onClick     = onNavigateToAccessory,
                    modifier    = Modifier.weight(1f)
                )
            }

            // Инструкции + Регламент
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HomeCard(
                    icon        = Icons.Default.MenuBook,
                    title       = "Инструкции",
                    description = "Ситуации и руководства",
                    onClick     = onNavigateToSituations,
                    modifier    = Modifier.weight(1f)
                )
                HomeCard(
                    icon        = Icons.Default.Engineering,
                    title       = "Регламент ТО",
                    description = "График технического обслуживания",
                    onClick     = onNavigateToMaintenance,
                    modifier    = Modifier.weight(1f)
                )
            }

            // Сравнение + Моя техника
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HomeCard(
                    icon        = Icons.Default.CompareArrows,
                    title       = "Сравнение",
                    description = "Сравнить модели BRP",
                    onClick     = onNavigateToCompare,
                    modifier    = Modifier.weight(1f)
                )
                HomeCard(
                    icon        = Icons.Default.DirectionsCar,
                    title       = "Моя техника",
                    description = "Выбрать свою модель",
                    onClick     = onNavigateToVehicle,
                    modifier    = Modifier.weight(1f)
                )
            }

            HorizontalDivider()

            // Настройки ИИ
            Text(
                text       = "Настройки",
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HomeCard(
                    icon        = Icons.Default.Psychology,
                    title       = "Настройки ИИ",
                    description = "Модели, API-ключи",
                    onClick     = onNavigateToModel,
                    modifier    = Modifier.weight(1f)
                )
                HomeCard(
                    icon        = Icons.Default.LibraryBooks,
                    title       = "Моя база",
                    description = "Загрузить документы",
                    onClick     = onNavigateToUserDocs,
                    modifier    = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Плитка-карточка навигации на главном экране.
 */
@Composable
private fun HomeCard(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false
) {
    val containerColor = if (primary)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant

    val contentColor = if (primary)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor   = contentColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (primary) 4.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                modifier           = Modifier.size(if (primary) 32.dp else 24.dp),
                tint               = if (primary)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column {
                Text(
                    text       = title,
                    style      = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = if (primary) 18.sp else 15.sp
                )
                Text(
                    text  = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.7f)
                )
            }
        }
    }
}
