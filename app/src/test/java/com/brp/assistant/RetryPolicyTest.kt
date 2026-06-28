package com.brp.assistant

import com.brp.assistant.data.llm.RetryPolicy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class RetryPolicyTest {

    private val policy = RetryPolicy(maxAttempts = 3, initialDelayMs = 0, maxDelayMs = 0)

    @Test
    fun `success on first attempt — returns result`() = runTest {
        val result = policy.execute { "ok" }
        assertEquals("ok", result)
    }

    @Test
    fun `retries on IOException and succeeds on 3rd attempt`() = runTest {
        var calls = 0
        val result = policy.execute {
            calls++
            if (calls < 3) throw IOException("network")
            "recovered"
        }
        assertEquals("recovered", result)
        assertEquals(3, calls)
    }

    @Test(expected = IOException::class)
    fun `exhausts retries and rethrows`() = runTest {
        policy.execute { throw IOException("permanent") }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non-retryable exception propagates immediately`() = runTest {
        var calls = 0
        policy.execute {
            calls++
            throw IllegalArgumentException("bad input")
        }
        assertEquals(1, calls)
    }
}
