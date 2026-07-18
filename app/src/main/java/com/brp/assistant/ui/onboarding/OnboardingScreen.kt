package com.brp.assistant.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Экран первого запуска. Показывается один раз до [onboardingCompleted = true].
 *
 * Три шага:
 *   1. Приветствие — что такое BRP Assistant
 *   2. Рекомендация модели — на основе RAM устройства
 *   3. Готово — переход на HomeScreen
 *
 * @param totalRamGb  общая RAM устройства в ГБ (0.0 = неизвестно)
 * @param deviceInfo  строка в формате formatDeviceInfo()
 * @param onFinish    колбэк: сохранить флаг + навигация на Home
 */
@Composable
fun OnboardingScreen(
    totalRamGb: Double,
    deviceInfo: String,
    onFinish: () -> Unit
) {
    var step by remember { mutableIntStateOf(0) }

    AnimatedContent(
        targetState = step,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "onboarding_step"
    ) { currentStep ->
        when (currentStep) {
            0 -> StepWelcome(onNext = { step = 1 })
            1 -> StepDeviceCapability(
                totalRamGb = totalRamGb,
                deviceInfo = deviceInfo,
                onNext     = { step = 2 }
            )
            else -> StepReady(onFinish = onFinish)
        }
    }
}

// ───────────────────────────────────────────────────────────────────────
// ШАГ 1: Приветствие
// ───────────────────────────────────────────────────────────────────────
@Composable
private fun StepWelcome(onNext: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector        = Icons.Default.TwoWheeler,
            contentDescription = null,
            tint               = MaterialTheme.colorScheme.primary,
            modifier           = Modifier.size(80.dp)
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text       = "BRP Assistant",
            style      = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            textAlign  = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text      = "Ваш AI-помощник по технике BRP.\nДиагностика, аксессуары, регламент \u2014 всё в одном месте.",
            style     = MaterialTheme.typography.bodyLarge,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(40.dp))
        OnboardingFeatureRow(
            icon  = Icons.Default.Build,
            title = "Диагностика",
            desc  = "Опишите проблему — AI подскажет решение"
        )
        Spacer(Modifier.height(12.dp))
        OnboardingFeatureRow(
            icon  = Icons.Default.ShoppingBag,
            title = "Аксессуары",
            desc  = "Подбор по вашей модели и задачам"
        )
        Spacer(Modifier.height(12.dp))
        OnboardingFeatureRow(
            icon  = Icons.Default.OfflineBolt,
            title = "Оффлайн-режим",
            desc  = "Локальные LLM-модели без интернета"
        )
        Spacer(Modifier.height(48.dp))
        Button(
            onClick  = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Начать", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ───────────────────────────────────────────────────────────────────────
// ШАГ 2: Рекомендация модели на основе RAM
// ───────────────────────────────────────────────────────────────────────
@Composable
private fun StepDeviceCapability(
    totalRamGb: Double,
    deviceInfo: String,
    onNext: () -> Unit
) {
    // Три тиера устройства:
    // < 3 ГБ — только онлайн
    // 3–6 ГБ — Qwen3-0.6B / Gemma-2B
    // ≥ 6 ГБ — Qwen3-1.7B / Gemma-3
    val (tierIcon, tierTitle, tierDesc, tierModel, tierColor) = when {
        totalRamGb < 3.0 -> DeviceTier(
            icon   = Icons.Default.Cloud,
            title  = "Онлайн-режим",
            desc   = "Устройство подходит для работы через Gemini / Groq. Для оффлайн-моделей недостаточно RAM.",
            model  = "Рекомендуем: Gemini / Groq",
            color  = TierColor.ONLINE
        )
        totalRamGb < 6.0 -> DeviceTier(
            icon   = Icons.Default.OfflineBolt,
            title  = "Лёгкое устройство",
            desc   = "Подходят компактные оффлайн-модели.",
            model  = "Рекомендуем: Qwen3-0.6B или Gemma-2B",
            color  = TierColor.LITE
        )
        else -> DeviceTier(
            icon   = Icons.Default.Memory,
            title  = "Мощное устройство",
            desc   = "Подходят крупные оффлайн-модели для высокого качества ответов.",
            model  = "Рекомендуем: Qwen3-1.7B или Gemma-3",
            color  = TierColor.PRO
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text       = "Ваше устройство",
            style      = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign  = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text      = deviceInfo,
            style     = MaterialTheme.typography.bodySmall,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))

        val cardColor = when (tierColor) {
            TierColor.ONLINE -> MaterialTheme.colorScheme.secondaryContainer
            TierColor.LITE   -> MaterialTheme.colorScheme.tertiaryContainer
            TierColor.PRO    -> MaterialTheme.colorScheme.primaryContainer
        }
        val onCardColor = when (tierColor) {
            TierColor.ONLINE -> MaterialTheme.colorScheme.onSecondaryContainer
            TierColor.LITE   -> MaterialTheme.colorScheme.onTertiaryContainer
            TierColor.PRO    -> MaterialTheme.colorScheme.onPrimaryContainer
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(20.dp),
            colors   = CardDefaults.cardColors(
                containerColor = cardColor,
                contentColor   = onCardColor
            )
        ) {
            Column(
                modifier            = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector        = tierIcon,
                    contentDescription = null,
                    modifier           = Modifier.size(48.dp),
                    tint               = onCardColor
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text       = tierTitle,
                    style      = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign  = TextAlign.Center,
                    color      = onCardColor
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text      = tierDesc,
                    style     = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color     = onCardColor.copy(alpha = 0.85f)
                )
                Spacer(Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = onCardColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text     = tierModel,
                        style    = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color    = onCardColor,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text      = "Модель можно загрузить позже в «Настройках ИИ».",
            style     = MaterialTheme.typography.bodySmall,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(48.dp))
        Button(
            onClick  = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Далее", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ───────────────────────────────────────────────────────────────────────
// ШАГ 3: Готово
// ───────────────────────────────────────────────────────────────────────
@Composable
private fun StepReady(onFinish: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector        = Icons.Default.CheckCircle,
            contentDescription = null,
            tint               = MaterialTheme.colorScheme.primary,
            modifier           = Modifier.size(80.dp)
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text       = "Всё готово!",
            style      = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign  = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text      = "Выберите свою технику BRP на главном экране\nчтобы получать персонализированные ответы.",
            style     = MaterialTheme.typography.bodyLarge,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(48.dp))
        Button(
            onClick  = onFinish,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Перейти на главную", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ───────────────────────────────────────────────────────────────────────
// ВСПОМОГАТЕЛЬНЫЕ
// ───────────────────────────────────────────────────────────────────────
@Composable
private fun OnboardingFeatureRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    desc: String
) {
    Row(
        modifier            = Modifier.fillMaxWidth(),
        verticalAlignment   = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            tint               = MaterialTheme.colorScheme.primary,
            modifier           = Modifier.size(28.dp)
        )
        Column {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(text = desc,  style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private data class DeviceTier(
    val icon:  androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val desc:  String,
    val model: String,
    val color: TierColor
)

private enum class TierColor { ONLINE, LITE, PRO }
