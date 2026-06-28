package com.brp.assistant.data.llm

import kotlinx.coroutines.delay

/**
 * E2 — Подключение RetryPolicy к suspend-лямбдам LLM-запросов.
 *
 * Расширение позволяет использовать RetryPolicy с любым suspend-блоком:
 *
 * ```kotlin
 * val result = retryPolicy.execute {
 *     provider.complete(prompt)
 * }
 * ```
 *
 * Логика:
 *  1. Выполняем [block].
 *  2. При исключении: если попыток ещё осталось — ждём [RetryPolicy.delayFor] и повторяем.
 *  3. После исчерпания попыток — пробрасываем последнее исключение наверх.
 *
 * [RetryPolicy] — data-класс с полями: maxAttempts, baseDelayMs, factor.
 */
suspend fun <T> RetryPolicy.execute(block: suspend () -> T): T {
    var lastError: Throwable? = null
    repeat(maxAttempts) { attempt ->
        try {
            return block()
        } catch (e: Exception) {
            lastError = e
            val waitMs = delayFor(attempt)   // baseDelayMs * factor^attempt
            delay(waitMs)
        }
    }
    throw lastError ?: IllegalStateException("RetryPolicy: no attempts executed")
}
