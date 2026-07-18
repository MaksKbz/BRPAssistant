package com.brp.assistant.domain.usecase

import com.brp.assistant.data.llm.OfflineModelInfo
import com.brp.assistant.domain.DeviceCapabilityProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Данные результата оценки безопасности модели для текущего устройства.
 *
 * @param isSafe         true — модель скорее всего будет работать стабильно.
 * @param warningMessage текст предупреждения для показа в UI, null — предупреждений нет.
 */
data class ModelRecommendation(
    val isSafe: Boolean,
    val warningMessage: String?
)

/**
 * Использует [DeviceCapabilityProvider.isSafeForModel] для оценки перед загрузкой.
 * Если модель небезопасна — возвращает [ModelRecommendation] с предупреждением,
 * которое ModelManagerViewModel показывает пользователю перед началом загрузки.
 * Скачивание НЕ блокируется полностью — пользователь может продолжить.
 */
@Singleton
class RecommendLlmModeUseCase @Inject constructor(
    private val deviceCapabilityProvider: DeviceCapabilityProvider
) {
    fun evaluate(model: OfflineModelInfo): ModelRecommendation {
        val safe = deviceCapabilityProvider.isSafeForModel(model)
        val warning = if (!safe) {
            "⚠️ Модель ${model.title} может работать нестабильно на вашем устройстве " +
            "(мало RAM). Рекомендуем выбрать модель меньшего размера или использовать онлайн-провайдера."
        } else null
        return ModelRecommendation(isSafe = safe, warningMessage = warning)
    }
}
