package com.brp.assistant.data.llm

import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * #3 — Circuit Breaker для remote LLM провайдеров.
 *
 * Состояния:
 *  CLOSED  — нормальная работа, запросы проходят.
 *  OPEN    — после [FAILURE_THRESHOLD] подряд ошибок; запросы отклоняются
 *            немедленно без обращения к сети на [RESET_TIMEOUT_MS] мс.
 *  HALF_OPEN — после таймаута; один пробный запрос разрешён:
 *              успех → CLOSED, ошибка → OPEN снова.
 *
 * Почему не просто withRetry:
 *  withRetry повторяет ошибочные запросы и добавляет задержку.
 *  CircuitBreaker останавливает поток запросов целиком, когда провайдер
 *  явно недоступен — не тратит батарею и лимиты API на безнадёжные попытки.
 *
 * Ключи circuit-breaker'ов хранятся по provider-name ("Gemini", "Groq").
 */
@Singleton
class CircuitBreaker @Inject constructor() {

    private data class BreakerState(
        val failures: Int = 0,
        val state: State = State.CLOSED,
        val openedAt: Long = 0L
    )

    private enum class State { CLOSED, OPEN, HALF_OPEN }

    private val mutex = Mutex()
    private val breakers = mutableMapOf<String, BreakerState>()

    /**
     * Возвращает true если провайдер заблокирован (OPEN-состояние).
     * В HALF_OPEN пропускает один запрос.
     */
    suspend fun isOpen(provider: String): Boolean = mutex.withLock {
        val s = breakers[provider] ?: return@withLock false
        when (s.state) {
            State.CLOSED -> false
            State.OPEN -> {
                val elapsed = System.currentTimeMillis() - s.openedAt
                if (elapsed >= RESET_TIMEOUT_MS) {
                    // Переходим в HALF_OPEN — даём один шанс
                    breakers[provider] = s.copy(state = State.HALF_OPEN)
                    Log.d(TAG, "[$provider] OPEN → HALF_OPEN after ${elapsed}ms")
                    false // пропускаем запрос
                } else {
                    Log.d(TAG, "[$provider] OPEN, blocking. ${RESET_TIMEOUT_MS - elapsed}ms to reset")
                    true
                }
            }
            State.HALF_OPEN -> false // один пробный запрос
        }
    }

    /**
     * Вызывается после успешного запроса — сбрасывает счётчик ошибок.
     */
    suspend fun recordSuccess(provider: String) = mutex.withLock {
        val prev = breakers[provider]
        if (prev != null && prev.state != State.CLOSED) {
            Log.i(TAG, "[$provider] → CLOSED (success)")
        }
        breakers[provider] = BreakerState()
    }

    /**
     * Вызывается после ошибки — инкрементирует счётчик.
     * При достижении [FAILURE_THRESHOLD] переходит в OPEN.
     */
    suspend fun recordFailure(provider: String) = mutex.withLock {
        val s = breakers.getOrDefault(provider, BreakerState())
        val newFailures = s.failures + 1
        val newState = if (newFailures >= FAILURE_THRESHOLD) {
            Log.w(TAG, "[$provider] → OPEN after $newFailures failures")
            State.OPEN
        } else {
            Log.d(TAG, "[$provider] failure $newFailures/$FAILURE_THRESHOLD")
            State.CLOSED
        }
        breakers[provider] = BreakerState(
            failures = newFailures,
            state = newState,
            openedAt = if (newState == State.OPEN) System.currentTimeMillis() else s.openedAt
        )
    }

    /** Принудительный сброс конкретного провайдера (например, после смены ключа). */
    suspend fun reset(provider: String) = mutex.withLock {
        breakers.remove(provider)
        Log.i(TAG, "[$provider] manually reset")
    }

    companion object {
        private const val TAG = "CircuitBreaker"
        /** Число подряд идущих ошибок для открытия circuit. */
        const val FAILURE_THRESHOLD = 3
        /** Время блокировки провайдера после открытия circuit (30 с). */
        const val RESET_TIMEOUT_MS = 30_000L
    }
}
