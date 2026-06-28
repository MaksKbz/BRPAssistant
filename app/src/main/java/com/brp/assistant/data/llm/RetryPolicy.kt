package com.brp.assistant.data.llm

import kotlin.math.pow

/**
 * E2 — Политика повторных попыток для LLM-запросов.
 *
 * @param maxAttempts  Максимальное число попыток (включая первую). Default: 3.
 * @param baseDelayMs  Базовая задержка перед первым повтором в мс. Default: 1000.
 * @param factor       Множитель экспоненциального отступа. Default: 2.0.
 *
 * Пример задержек при factor=2.0, baseDelayMs=1000:
 *   attempt 0 (первая ошибка) → ждём 1000 мс
 *   attempt 1 (вторая ошибка) → ждём 2000 мс
 *   attempt 2 (третья ошибка) → бросаем исключение
 */
data class RetryPolicy(
    val maxAttempts: Int = 3,
    val baseDelayMs: Long = 1_000L,
    val factor: Double = 2.0
) {
    /**
     * Вычисляет задержку перед [attempt]-й повторной попыткой (0-based).
     * Формула: baseDelayMs * factor^attempt.
     */
    fun delayFor(attempt: Int): Long =
        (baseDelayMs * factor.pow(attempt.toDouble())).toLong()

    companion object {
        /** Стандартная политика: 3 попытки, 1с → 2с → бросить. */
        val DEFAULT = RetryPolicy(maxAttempts = 3, baseDelayMs = 1_000L, factor = 2.0)

        /** Для быстрых тестов: 2 попытки, 100 мс. */
        val FAST = RetryPolicy(maxAttempts = 2, baseDelayMs = 100L, factor = 1.0)
    }
}
