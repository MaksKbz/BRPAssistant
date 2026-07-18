package com.brp.assistant.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Экран онбординга — показывается только при первом запуске.
 * Сигнатура соответствует вызову в BrpNavGraph.kt:
 *   OnboardingScreen(totalRamGb, deviceInfo, onFinish)
 *
 * @param totalRamGb  Общий объём RAM устройства в ГБ (из DeviceCapabilityProvider).
 * @param deviceInfo  Строка с кратким описанием устройства (RAM, API, ABI).
 * @param onFinish    Коллбэк: пользователь завершил онбординг (вызывает completeOnboarding()).
 */
@Composable
fun OnboardingScreen(
    totalRamGb: Double,
    deviceInfo: String,
    onFinish: () -> Unit
) {
    // >= 4 GB считаем достаточным для локальной LLM
    val recommendLocalModel = totalRamGb >= 4.0

    var step by remember { mutableStateOf(0) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (step) {
                0 -> OnboardingStep1 { step = 1 }
                1 -> OnboardingStep2(
                    deviceInfo = deviceInfo,
                    recommendLocalModel = recommendLocalModel
                ) { step = 2 }
                2 -> OnboardingStep3(onFinish = onFinish)
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Dot indicator
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) { index ->
                    val isActive = index == step
                    Surface(
                        modifier = Modifier
                            .height(8.dp)
                            .width(if (isActive) 24.dp else 8.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = if (isActive)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.outlineVariant
                    ) {}
                }
            }
        }
    }
}

@Composable
private fun OnboardingStep1(onNext: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "🏍️",
            fontSize = 56.sp
        )
        Text(
            text = "BRP Assistant",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Ваш AI-помощник по технике BRP",
            fontSize = 17.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Диагностика, советы, регламент — всё в одном приложении.
Работает с локальными и онлайн AI-моделями.",
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Далее")
        }
    }
}

@Composable
private fun OnboardingStep2(
    deviceInfo: String,
    recommendLocalModel: Boolean,
    onNext: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Ваше устройство",
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Text(
                text = deviceInfo,
                modifier = Modifier.padding(16.dp),
                fontSize = 13.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (recommendLocalModel)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Text(
                text = if (recommendLocalModel)
                    "✅ Рекомендуем локальную модель — работает офлайн и бесплатно"
                else
                    "☁️ Рекомендуем онлайн-модель (Gemini / Groq) — на вашем устройстве недостаточно памяти для локальной модели",
                modifier = Modifier.padding(16.dp),
                fontSize = 14.sp,
                color = if (recommendLocalModel)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onErrorContainer
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Далее")
        }
    }
}

@Composable
private fun OnboardingStep3(onFinish: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "🎉",
            fontSize = 56.sp
        )
        Text(
            text = "Готовы к работе!",
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Выберите свою технику BRP в разделе «Моя техника», чтобы получать точные советы и диагностику.",
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Начать")
        }
    }
}
