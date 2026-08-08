package com.brp.assistant.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InferenceBenchmarkTest {
    @Test
    fun `tokens per second uses generation time after first token`() {
        val benchmark = InferenceBenchmark(
            modelId = "qwen",
            runtime = "LITERTLM",
            timeToFirstTokenMs = 500,
            totalTimeMs = 1500,
            outputChars = 400,
            outputTokensApprox = 100,
            availableRamBeforeMb = 2000,
            availableRamAfterMb = 1800,
            batteryLevel = 80,
            batterySaverOn = false,
            succeeded = true
        )

        assertEquals(100.0, benchmark.tokensPerSecond!!, 0.0)
        assertTrue(benchmark.succeeded)
    }
}
