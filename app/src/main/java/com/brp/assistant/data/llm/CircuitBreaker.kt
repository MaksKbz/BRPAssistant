package com.brp.assistant.data.llm

import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Простой CircuitBreaker для защиты от cascade-failures при проблемах
 * с сетью или LLM-провайдером.
 *
 * Состояния:
 *  CLOSED   — нормальная работа, запросы проходят.
 *  OPEN     — провайдер временно заблокирован после [failureThreshold] ошибок подряд.
 *             Запросы сразу отклоняются с [CircuitBreakerOpenException].
 *  HALF_OPEN — спустя [resetTimeoutMs] один запрос пропускается для проверки.
 *              Успех → CLOSED. Неудача → снова OPEN.
 *
 * @param name             Имя провайдера для логов ("Groq", "Gemini", "MediaPipe").
 * @param failureThreshold Порог последовательных ошибок для перехода в OPEN (default: 3).
 * @param resetTimeoutMs   Время в OPEN-состоянии перед HALF_OPEN (default: 60 секунд).
 */
class CircuitBreaker(
    private val name: String,
    private val failureThreshold: Int = 3,
    private val resetTimeoutMs: Long = 60_000L
) {
    private enum class State { CLOSED, OPEN, HALF_OPEN }

    private val mutex = Mutex()
    private val consecutiveFailures = AtomicInteger(0)
    private val openedAt = AtomicLong(0L)
    @Volatile private var state = State.CLOSED

    /**
     * Проверяет, можно ли выполнять запрос.
     * Если OPEN и таймаут истёк — переводит в HALF_OPEN.
     * @throws CircuitBreakerOpenException если провайдер заблокирован.
     */
    suspend fun checkState() = mutex.withLock {
        when (state) {
            State.CLOSED    -> Unit
            State.OPEN      -> {
                val elapsed = System.currentTimeMillis() - openedAt.get()
                if (elapsed >= resetTimeoutMs) {
                    Log.i("CircuitBreaker", "[$name] OPEN → HALF_OPEN after ${elapsed / 1000}s")
                    state = State.HALF_OPEN
                } else {
                    val remaining = (resetTimeoutMs - elapsed) / 1000
                    throw CircuitBreakerOpenException(
                        "Провайдер '$name' временно недоступен. Повторите через ~${remaining}с."
                    )
                }
            }
            State.HALF_OPEN -> Unit
        }
    }

    /** Вызывается при успешном запросе — сбрасывает счётчик ошибок. */
    suspend fun recordSuccess() = mutex.withLock {
        if (state != State.CLOSED) {
            Log.i("CircuitBreaker", "[$name] → CLOSED (success)")
        }
        state = State.CLOSED
        consecutiveFailures.set(0)
    }

    /** Вызывается при ошибке — инкрементирует счётчик, при достижении порога открывает. */
    suspend fun recordFailure() = mutex.withLock {
        val failures = consecutiveFailures.incrementAndGet()
        if (state == State.HALF_OPEN || failures >= failureThreshold) {
            Log.w("CircuitBreaker", "[$name] → OPEN after $failures failures")
            state = State.OPEN
            openedAt.set(System.currentTimeMillis())
        } else {
            Log.d("CircuitBreaker", "[$name] failure $failures/$failureThreshold")
        }
    }

    /** Принудительный сброс (для тестов или ручного восстановления). */
    suspend fun reset() = mutex.withLock {
        state = State.CLOSED
        consecutiveFailures.set(0)
        Log.i("CircuitBreaker", "[$name] manually reset")
    }

    fun isOpen(): Boolean = state == State.OPEN
    fun getFailureCount(): Int = consecutiveFailures.get()
}

class CircuitBreakerOpenException(message: String) : Exception(message)
