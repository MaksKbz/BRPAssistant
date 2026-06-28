package com.brp.assistant.data.llm

import android.util.Log
import kotlinx.coroutines.delay
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.random.Random

/**
 * #7 — Standalone RetryPolicy с экспоненциальным backoff и jitter.
 *
 * Выделен из RemoteLlmEngine.withRetry() в отдельный класс чтобы:
 *   1. Тестировать политику повторов в изоляции (unit-test без OkHttp).
 *   2. Переиспользовать в будущих сетевых клиентах (RAG, download).
 *   3. Явно задокументировать параметры через конструктор.
 *
 * Параметры по умолчанию:
 *   maxAttempts  = 3    — первый запрос + 2 повтора
 *   baseDelayMs  = 1000 — 1с → 2с → 4с (удваивается)
 *   maxDelayMs   = 8000 — потолок задержки
 *   jitterFactor = 0.1  — ±10% случайный разброс (избегает thundering herd)
 *
 * Retryable ошибки: только [IOException] (включая [Http429Exception]).
 * 4xx-ошибки конфигурации (401, 404) НЕ ретраятся — они кидаются как
 * обычный Exception, который isRetryable() возвращает false.
 *
 * Использование:
 * ```kotlin
 * val policy = RetryPolicy()
 * val result = policy.execute { callApi() }
 * ```
 */
@Singleton
class RetryPolicy @Inject constructor() {

    companion object {
        private const val TAG = "RetryPolicy"
        const val DEFAULT_MAX_ATTEMPTS = 3
        const val DEFAULT_BASE_DELAY_MS = 1_000L
        const val DEFAULT_MAX_DELAY_MS = 8_000L
        const val DEFAULT_JITTER_FACTOR = 0.1
    }

    /**
     * Выполняет [block] с повторами согласно политике.
     *
     * @param maxAttempts    Максимальное число попыток (включая первую).
     * @param baseDelayMs    Базовая задержка перед первым повтором (мс).
     * @param maxDelayMs     Максимальная задержка (потолок exponential backoff).
     * @param jitterFactor   Доля случайного отклонения (0.0–1.0).
     * @param isRetryable    Предикат: стоит ли повторять при данной ошибке.
     * @param block          Блок, возвращающий Result<T>.
     * @return               Последний [Result] после всех попыток.
     */
    suspend fun <T> execute(
        maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
        baseDelayMs: Long = DEFAULT_BASE_DELAY_MS,
        maxDelayMs: Long = DEFAULT_MAX_DELAY_MS,
        jitterFactor: Double = DEFAULT_JITTER_FACTOR,
        isRetryable: (Throwable) -> Boolean = ::defaultIsRetryable,
        block: suspend () -> Result<T>
    ): Result<T> {
        var lastResult: Result<T> = Result.failure(IllegalStateException("No attempts made"))

        for (attempt in 0 until maxAttempts) {
            if (attempt > 0) {
                val exponential = baseDelayMs * (1L shl (attempt - 1))      // 1с, 2с, 4с...
                val capped = min(exponential, maxDelayMs)                    // потолок 8с
                val jitter = (capped * jitterFactor * (Random.nextDouble() * 2 - 1)).toLong()
                val delay = (capped + jitter).coerceAtLeast(0L)
                Log.d(TAG, "Attempt ${attempt + 1}/$maxAttempts — backoff ${delay}ms")
                delay(delay)
            }

            lastResult = block()

            if (lastResult.isSuccess) return lastResult

            val ex = lastResult.exceptionOrNull() ?: return lastResult
            if (!isRetryable(ex)) {
                Log.d(TAG, "Non-retryable error on attempt ${attempt + 1}: ${ex.message}")
                return lastResult
            }

            Log.w(TAG, "Retryable error on attempt ${attempt + 1}: ${ex.message}")
        }

        Log.e(TAG, "All $maxAttempts attempts exhausted")
        return lastResult
    }

    /**
     * Дефолтный предикат повторов: ретраим только [IOException]
     * (включает [Http429Exception] т.к. она extends IOException).
     *
     * Конфигурационные ошибки (401, 404) кидаются как [Exception] —
     * не [IOException] — поэтому сюда не попадают.
     */
    private fun defaultIsRetryable(e: Throwable): Boolean = e is IOException
}
