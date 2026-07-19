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
        breaker = CircuitBreaker(failureThreshold = 3, resetTimeoutMs = 0)
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
        repeat(3) {
            try { breaker.call("test") { throw RuntimeException("fail") } } catch (_: Exception) {}
        }
        val result = breaker.call("test") { "recovered" }
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
