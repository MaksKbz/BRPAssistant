package com.brp.assistant

import com.brp.assistant.data.llm.CircuitBreaker
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CircuitBreakerTest {

    private lateinit var breaker: CircuitBreaker

    @Before
    fun setUp() {
        // openDurationMs = 0 чтобы тест не ждал 30 секунд
        breaker = CircuitBreaker(failureThreshold = 3, openDurationMs = 0)
    }

    @Test
    fun `closed by default — calls pass through`() = runTest {
        assertTrue(breaker.isAllowed())
    }

    @Test
    fun `opens after threshold failures`() = runTest {
        repeat(3) { breaker.recordFailure() }
        assertFalse(breaker.isAllowed())
    }

    @Test
    fun `resets to closed after open duration expires`() = runTest {
        repeat(3) { breaker.recordFailure() }
        // openDurationMs = 0 → должен сразу перейти в half-open
        assertTrue(breaker.isAllowed())
    }

    @Test
    fun `recordSuccess resets failure count`() = runTest {
        repeat(2) { breaker.recordFailure() }
        breaker.recordSuccess()
        assertTrue(breaker.isAllowed())
        // После success счётчик сброшен — нужно снова 3 ошибки
        repeat(2) { breaker.recordFailure() }
        assertTrue(breaker.isAllowed())
    }
}
