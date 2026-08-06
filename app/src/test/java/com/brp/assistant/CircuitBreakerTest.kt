package com.brp.assistant

import android.util.Log
import com.brp.assistant.data.llm.CircuitBreaker
import com.brp.assistant.data.llm.CircuitOpenException
import io.mockk.every
import io.mockk.mockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CircuitBreakerTest {

    private lateinit var breaker: CircuitBreaker

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.i(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.d(any(), any<String>()) } returns 0
        // resetTimeoutMs=60_000 keeps the breaker in OPEN state for the
        // entire duration of the test (no wall-clock time will exceed 60 s
        // inside a synchronous runTest block).
        breaker = CircuitBreaker(failureThreshold = 3, resetTimeoutMs = 60_000L)
    }

    @Test
    fun `closed by default — call succeeds`() = runTest {
        val result = breaker.call("test") { "ok" }
        assertEquals("ok", result)
    }

    @Test
    fun `opens after threshold failures`() = runTest {
        repeat(3) {
            try { breaker.call("test") { throw RuntimeException("fail") } } catch (_: Exception) {}
        }
        try {
            breaker.call("test") { "should not" }
            fail("Should have thrown CircuitOpenException")
        } catch (e: CircuitOpenException) {
            assertTrue(e.message!!.contains("OPEN"))
        }
    }

    @Test
    fun `resets to closed after open duration expires`() = runTest {
        // Use a fresh breaker with resetTimeoutMs=0 so timeout expires instantly
        val fastBreaker = CircuitBreaker(failureThreshold = 3, resetTimeoutMs = 0)
        repeat(3) {
            try { fastBreaker.call("test") { throw RuntimeException("fail") } } catch (_: Exception) {}
        }
        // resetTimeoutMs=0 → elapsed >= 0 → transitions to HALF_OPEN → probe call succeeds → CLOSED
        val result = fastBreaker.call("test") { "recovered" }
        assertEquals("recovered", result)
    }

    @Test
    fun `recordSuccess resets failure count`() = runTest {
        repeat(2) {
            try { breaker.call("test") { throw RuntimeException("fail") } } catch (_: Exception) {}
        }
        breaker.call("test") { "ok" }
        repeat(2) {
            try { breaker.call("test") { throw RuntimeException("fail") } } catch (_: Exception) {}
        }
        val result = breaker.call("test") { "still ok" }
        assertEquals("still ok", result)
    }
}
