package com.brp.assistant.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val body: String,
    val cta: String,
    val ctaSecondary: String? = null
)

private val pages = listOf(
    OnboardingPage(
        icon  = Icons.Default.EmojiEvents,
        title = "Добро пожаловать в BRP Assistant",
        body  = "Оффлайн-эксперт для вашей техники Can-Am, Ski-Doo, Sea-Doo и Lynx. " +
               "Диагностика, регламент ТО и ИИ-чат — всё в одном месте.",
        cta   = "Далее"
    ),
    OnboardingPage(
        icon  = Icons.Default.DirectionsCar,
        title = "Выберите свою технику",
        body  = "Укажите бренд, категорию и модель, чтобы получать ответы, адаптированные именно под вашу модель.",
        cta   = "Выбрать технику",
        ctaSecondary = "Пропустить"
    ),
    OnboardingPage(
        icon  = Icons.Default.AutoAwesome,
        title = "Подключите ИИ",
        body  = "Добавьте API-ключ Gemini или загрузите оффлайн-модель для работы без интернета. " +
               "Для оффлайн-режима рекомендуем Gemma 2B Q4 (требуется ≥ 4 ГБ RAM).",
        cta   = "Настроить ИИ",
        ctaSecondary = "Потом"
    )
)

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun OnboardingScreen(
    onVehicleSelect: () -> Unit,
    onModelManager:  () -> Unit,
    onFinish:        () -> Unit
) {
    var pageIndex by remember { mutableIntStateOf(0) }
    val page = pages[pageIndex]

    BackHandler(enabled = pageIndex > 0) {
        pageIndex--
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .padding(24.dp),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            AnimatedContent(
                targetState = pageIndex,
                transitionSpec = {
                    slideInHorizontally { it } + fadeIn() togetherWith
                    slideOutHorizontally { -it } + fadeOut()
                },
                label = "onboarding"
            ) { idx ->
                val p = pages[idx]
                Column(
                    modifier            = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Icon(
                        imageVector = p.icon,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text       = p.title,
                        style      = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign  = TextAlign.Center
                    )
                    Text(
                        text      = p.body,
                        style     = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color     = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Step dots
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        pages.indices.forEach { i ->
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (i == idx)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.size(if (i == idx) 24.dp else 8.dp, 8.dp)
                            ) {}
                        }
                    }

                    // Primary CTA
                    Button(
                        onClick = {
                            when (idx) {
                                0    -> pageIndex++
                                1    -> { onVehicleSelect(); pageIndex++ }
                                else -> { onModelManager(); onFinish() }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(p.cta)
                    }

                    // Secondary skip
                    if (p.ctaSecondary != null) {
                        TextButton(
                            onClick = {
                                if (idx < pages.lastIndex) pageIndex++
                                else onFinish()
                            }
                        ) { Text(p.ctaSecondary) }
                    }
                }
            }
        }
    }
}
