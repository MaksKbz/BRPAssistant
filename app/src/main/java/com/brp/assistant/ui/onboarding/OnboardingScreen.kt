package com.brp.assistant.ui.onboarding

import android.app.ActivityManager
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * OnboardingScreen — экран первого запуска.
 *
 * Показывается ТОЛЬКО при первом запуске (DataStore: onboarding_completed = false).
 * Закрывает TODO из PR #1: добавить OnboardingScreen в BrpNavGraph.kt.
 *
 * Содержит:
 * 1. Страница приветствия — бренды BRP, оффлайн-концепция
 * 2. Страница возможностей — диагностика, аксессуары, ИИ-эксперт
 * 3. Страница выбора модели ИИ + DeviceCapabilityCheck (проверка RAM)
 *
 * Адаптивность:
 * - Compact (телефон):  одна колонка, иконки 64dp
 * - Medium/Expanded (планшет): две колонки, увеличенные отступы
 */

private data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val iconTint: Color
)

private val pages = listOf(
    OnboardingPage(
        icon      = Icons.Default.TwoWheeler,
        title     = "Добро пожаловать\nв BRP Assistant",
        subtitle  = "Оффлайн-эксперт для Can-Am, Sea-Doo,\nSki-Doo и Lynx. Работает без интернета.",
        iconTint  = Color(0xFF0077CC)
    ),
    OnboardingPage(
        icon      = Icons.Default.Build,
        title     = "Диагностика,\nИнструкции и Аксессуары",
        subtitle  = "Задайте симптом — получите пошаговое\nрешение. База знаний 2026 года.",
        iconTint  = Color(0xFF00AA55)
    ),
    OnboardingPage(
        icon      = Icons.Default.AutoAwesome,
        title     = "ИИ-Эксперт\nна вашем устройстве",
        subtitle  = "Локальная языковая модель работает\nбез передачи данных. Или подключите\nGemini / Groq для расширенных ответов.",
        iconTint  = Color(0xFFEE6600)
    )
)

/**
 * Результат проверки совместимости устройства с локальными LLM.
 * Вынесен в отдельный data class для тестируемости.
 */
data class DeviceCapabilityResult(
    val totalRamGb: Float,
    val isSufficientForLocalLlm: Boolean
) {
    val message: String get() = when {
        isSufficientForLocalLlm -> "✅ ${totalRamGb.let { "%.1f".format(it) }} ГБ RAM — локальные модели поддерживаются"
        else -> "⚠️ ${totalRamGb.let { "%.1f".format(it) }} ГБ RAM — рекомендуется онлайн-режим (Gemini/Groq)"
    }
    val color: Color get() = if (isSufficientForLocalLlm) Color(0xFF00AA55) else Color(0xFFEE6600)
}

private fun checkDeviceCapability(context: Context): DeviceCapabilityResult {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val info = ActivityManager.MemoryInfo()
    am?.getMemoryInfo(info)
    val totalGb = info.totalMem / 1024f / 1024f / 1024f
    return DeviceCapabilityResult(
        totalRamGb = totalGb,
        isSufficientForLocalLlm = totalGb >= 5.5f  // ~6GB порог для 1.5B-модели
    )
)
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun OnboardingScreen(
    widthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
    onComplete: () -> Unit
) {
    val context   = LocalContext.current
    var pageIndex by remember { mutableIntStateOf(0) }
    val isTablet  = widthSizeClass != WindowWidthSizeClass.Compact
    val deviceCap = remember { checkDeviceCapability(context) }

    val horizontalPadding = if (isTablet) 48.dp else 24.dp
    val iconSize          = if (isTablet) 96.dp  else 72.dp

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = horizontalPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(1f))

            // ── Анимированная смена страниц ─────────────────────────────────────
            AnimatedContent(
                targetState = pageIndex,
                transitionSpec = {
                    slideInHorizontally(
                        animationSpec = tween(300),
                        initialOffsetX = { if (targetState > initialState) it else -it }
                    ) togetherWith slideOutHorizontally(
                        animationSpec = tween(300),
                        targetOffsetX = { if (targetState > initialState) -it else it }
                    )
                },
                label = "onboarding_page"
            ) { idx ->
                val page = pages[idx]
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier            = Modifier.fillMaxWidth()
                ) {
                    // Иконка в цветном круге
                    Box(
                        modifier          = Modifier
                            .size(iconSize)
                            .clip(CircleShape)
                            .background(page.iconTint.copy(alpha = 0.12f)),
                        contentAlignment  = Alignment.Center
                    ) {
                        Icon(
                            imageVector        = page.icon,
                            contentDescription = null,
                            tint               = page.iconTint,
                            modifier           = Modifier.size(iconSize * 0.55f)
                        )
                    }

                    Spacer(Modifier.height(32.dp))

                    Text(
                        text       = page.title,
                        style      = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold
                        ),
                        textAlign  = TextAlign.Center,
                        lineHeight = 36.sp
                    )

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text      = page.subtitle,
                        style     = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color     = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // DeviceCapabilityCard — только на последней странице
                    if (idx == pages.lastIndex) {
                        Spacer(Modifier.height(24.dp))
                        DeviceCapabilityCard(deviceCap)
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // ── Индикатор страниц ───────────────────────────────────────────────
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier              = Modifier.padding(bottom = 16.dp)
            ) {
                pages.indices.forEach { i ->
                    Box(
                        modifier = Modifier
                            .size(if (i == pageIndex) 24.dp else 8.dp, 8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (i == pageIndex)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.outlineVariant
                            )
                    )
                }
            }

            // ── Кнопки навигации ────────────────────────────────────────────────
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                // Кнопка «Назад» / пропустить
                if (pageIndex > 0) {
                    OutlinedButton(
                        onClick = { pageIndex-- },
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("Назад")
                    }
                } else {
                    TextButton(
                        onClick  = onComplete,
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("Пропустить", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Кнопка «Далее» / «Начать»
                Button(
                    onClick  = { if (pageIndex < pages.lastIndex) pageIndex++ else onComplete() },
                    modifier = Modifier.height(48.dp)
                ) {
                    Text(if (pageIndex < pages.lastIndex) "Далее" else "Начать")
                    if (pageIndex == pages.lastIndex) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Default.ArrowForward,
                            contentDescription = null,
                            modifier           = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceCapabilityCard(cap: DeviceCapabilityResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(
            containerColor = cap.color.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier          = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector        = if (cap.isSufficientForLocalLlm) Icons.Default.CheckCircle
                                     else Icons.Default.Warning,
                contentDescription = null,
                tint               = cap.color,
                modifier           = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text      = cap.message,
                    style     = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color     = cap.color
                )
                Text(
                    text  = if (cap.isSufficientForLocalLlm)
                        "Рекомендуем: Gemma 3 1B или Qwen2.5 1.5B"
                    else
                        "Используйте Gemini / Groq — бесплатно и быстро",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
