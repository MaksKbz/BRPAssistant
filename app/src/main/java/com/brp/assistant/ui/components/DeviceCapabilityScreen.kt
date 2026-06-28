package com.brp.assistant.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.brp.assistant.domain.DeviceCapabilityProvider

/**
 * FIX #6: экран предупреждения при первом запуске локальной модели
 * на устройствах с недостаточным объёмом RAM.
 *
 * Показывается через BrpNavGraph когда:
 * - Пользователь пытается загрузить/активировать локальную LLM
 * - DeviceCapabilityProvider.hasEnoughMemoryForLocalLlm() == false
 *
 * Не блокирует принудительно — пользователь может продолжить на свой риск
 * (кнопка "Всё равно продолжить"), но предупреждён о возможных вылетах.
 */
@Composable
fun DeviceCapabilityScreen(
    memoryStatus: DeviceCapabilityProvider.MemoryStatus,
    deviceInfo: String,
    onContinueAnyway: () -> Unit,
    onChooseOnlineModel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalRamGb = "%.1f".format(memoryStatus.totalRamMb / 1024.0)
    val isLowRam   = !memoryStatus.isSafeForGeneration

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(24.dp))

        Icon(
            imageVector        = if (isLowRam) Icons.Default.Warning else Icons.Default.Memory,
            contentDescription = null,
            tint               = if (isLowRam)
                MaterialTheme.colorScheme.error
            else
                MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )

        Text(
            text       = if (isLowRam) "Недостаточно RAM для локальной модели"
                         else "Проверка совместимости",
            style      = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign  = TextAlign.Center
        )

        // ── Карточка с характеристиками устройства ──────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors   = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Ваше устройство",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    deviceInfo,
                    style = MaterialTheme.typography.bodyMedium
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                RamRow(
                    label   = "Всего RAM",
                    value   = "$totalRamGb ГБ",
                    isOk    = !isLowRam
                )
                RamRow(
                    label   = "Доступно сейчас",
                    value   = "${memoryStatus.availRamMb} МБ",
                    isOk    = memoryStatus.availRamMb >= DeviceCapabilityProvider.MIN_AVAIL_RAM_MB
                )
                RamRow(
                    label   = "Рекомендуется",
                    value   = "≥ 6 ГБ RAM",
                    isOk    = true
                )
            }
        }

        // ── Объяснение ───────────────────────────────────────────────────
        if (isLowRam) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = "Локальные LLM-модели (1.5B–7B параметров) требуют минимум 4–6 ГБ RAM. " +
                           "На вашем устройстве (${totalRamGb} ГБ) модель может не загрузиться " +
                           "или вызвать принудительное завершение приложения.\n\n" +
                           "Рекомендуем использовать онлайн-режим (Gemini / Groq) — " +
                           "он работает на любом устройстве с интернетом.",
                    style    = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                    color    = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Кнопки действий ─────────────────────────────────────────────
        Button(
            onClick  = onChooseOnlineModel,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Использовать онлайн-режим (рекомендуется)")
        }

        OutlinedButton(
            onClick  = onContinueAnyway,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (isLowRam)
                    "Всё равно продолжить (риск вылета)"
                else
                    "Продолжить"
            )
        }
    }
}

@Composable
private fun RamRow(label: String, value: String, isOk: Boolean) {
    Row(
        modifier            = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment   = Alignment.CenterVertically
    ) {
        Text(
            text  = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text  = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = if (isOk)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.error
        )
    }
}
