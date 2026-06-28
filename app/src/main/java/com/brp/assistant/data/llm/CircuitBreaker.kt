package com.brp.assistant.data.llm

import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * E3 — Circuit Breaker для LLM-провайдеров.
 *
 * После [failureThreshold] подряд идущих ошибок переходит в состояние OPEN
 * и блокирует все вызовы на [resetTimeoutMs] миллисекунд.
 * По истечении таймаута переходит в HALF_OPEN: пропускает один пробный запрос.
 * Если он успешен — переходит в CLOSED; если нет — снова OPEN.
 *
 * Использование:
 * ```kotlin
 * val result = circuitBreaker.call(providerKey) {
 *     provider.complete(prompt)
 * }
 * ```
 *
 * @param failureThreshold  Кол-во последовательных ошибок для открытия цепи. Default: 3.
 * @param resetTimeoutMs    Пауза в OPEN-состоянии перед пробным запросом. Default: 30 000 мс.
 */
class CircuitBreaker(
    private val failureThreshold: Int = 3,
    private val resetTimeoutMs: Long = 30_000L
) {
    private enum class State { CLOSED, OPEN, HALF_OPEN }

    private data class BreakerState(
        val state: State = State.CLOSED,
        val failures: Int = 0,
        val openedAt: Long = 0L
    )

    // Отдельный счётчик на каждый ключ провайдера
    private val states = mutableMapOf<String, BreakerState>()
    private val mutex = Mutex()

    /**
     * Выполняет [block] с защитой Circuit Breaker.
     *
     * @param key  Идентификатор провайдера (например: "openai", "anthropic").
     * @throws CircuitOpenException если цепь открыта и таймаут не истёк.
     */
    suspend fun <T> call(key: String, block: suspend () -> T): T {
        mutex.withLock {
            val s = states.getOrDefault(key, BreakerState())
            when (s.state) {
                State.OPEN -> {
                    val elapsed = System.currentTimeMillis() - s.openedAt
                    if (elapsed < resetTimeoutMs) {
                        val remaining = (resetTimeoutMs - elapsed) / 1000
                        throw CircuitOpenException(
                            "Circuit OPEN for '$key'. Retry in ~${remaining}s."
                        )
                    } else {
                        // Таймаут истёк — пробуем один запрос
                        states[key] = s.copy(state = State.HALF_OPEN)
                        Log.i(TAG, "Circuit HALF_OPEN for '$key'")
                    }
                }
                State.CLOSED, State.HALF_OPEN -> Unit // продолжаем
            }
        }

        return try {
            val result = block()
            mutex.withLock {
                // Успех — сбрасываем счётчик
                states[key] = BreakerState(state = State.CLOSED)
                Log.d(TAG, "Circuit CLOSED for '$key' (success)")
            }
            result
        } catch (e: Exception) {
            mutex.withLock {
                val s = states.getOrDefault(key, BreakerState())
                val newFailures = s.failures + 1
                if (newFailures >= failureThreshold || s.state == State.HALF_OPEN) {
                    states[key] = BreakerState(
                        state = State.OPEN,
                        failures = newFailures,
                        openedAt = System.currentTimeMillis()
                    )
                    Log.w(TAG, "Circuit OPEN for '$key' after $newFailures failures")
                } else {
                    states[key] = s.copy(failures = newFailures)
                }
            }
            throw e
        }
    }

    /** Принудительный сброс состояния для [key] (удобно в тестах и после успеха). */
    suspend fun reset(key: String) {
        mutex.withLock { states.remove(key) }
    }

    /** Текущее кол-во последовательных ошибок для [key]. */
    suspend fun failureCount(key: String): Int =
        mutex.withLock { states[key]?.failures ?: 0 }

    /**
     * Открыта ли цепь для [key] (OPEN и таймаут не истёк).
     * Если таймаут истёк — переводит в HALF_OPEN и возвращает false (пропускает пробный запрос).
     */
    suspend fun isOpen(key: String): Boolean = mutex.withLock {
        val s = states[key] ?: return@withLock false
        when (s.state) {
            State.OPEN -> {
                val elapsed = System.currentTimeMillis() - s.openedAt
                if (elapsed < resetTimeoutMs) {
                    true
                } else {
                    states[key] = s.copy(state = State.HALF_OPEN)
                    Log.i(TAG, "Circuit HALF_OPEN for '$key'")
                    false
                }
            }
            State.HALF_OPEN, State.CLOSED -> false
        }
    }

    /** Регистрирует успех: сбрасывает счётчик ошибок и закрывает цепь. */
    suspend fun recordSuccess(key: String) {
        mutex.withLock {
            states[key] = BreakerState(state = State.CLOSED)
            Log.d(TAG, "Circuit CLOSED for '$key' (recordSuccess)")
        }
    }

    /** Регистрирует сбой: инкремент счётчика, при достижении порога — OPEN. */
    suspend fun recordFailure(key: String) {
        mutex.withLock {
            val s = states.getOrDefault(key, BreakerState())
            val newFailures = s.failures + 1
            states[key] = if (newFailures >= failureThreshold || s.state == State.HALF_OPEN) {
                BreakerState(State.OPEN, newFailures, System.currentTimeMillis()).also {
                    Log.w(TAG, "Circuit OPEN for '$key' after $newFailures failures")
                }
            } else {
                s.copy(failures = newFailures)
            }
        }
    }

    companion object {
        private const val TAG = "CircuitBreaker"
    }
}

/** Исключение, бросаемое когда Circuit Breaker находится в состоянии OPEN. */
class CircuitOpenException(message: String) : Exception(message)
