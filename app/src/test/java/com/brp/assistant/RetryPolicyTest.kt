package com.brp.assistant

import com.brp.assistant.data.llm.RetryPolicy
import com.brp.assistant.data.llm.execute
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class RetryPolicyTest {

    private val policy = RetryPolicy(maxAttempts = 3, baseDelayMs = 0, factor = 1.0)

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

    @Test
    fun `non-retryable exception still retries per current policy`() = runTest {
        // Current RetryPolicy retries all Exceptions, not only IOException
        var calls = 0
        try {
            policy.execute {
                calls++
                throw IllegalArgumentException("bad input")
            }
        } catch (_: IllegalArgumentException) {}
        // Should have retried maxAttempts times
        assertEquals(3, calls)
    }
}
