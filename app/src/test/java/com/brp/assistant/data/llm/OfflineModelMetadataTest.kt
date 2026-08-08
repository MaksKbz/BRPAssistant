package com.brp.assistant.data.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineModelMetadataTest {
    @Test
    fun `runtime peak memory is higher than download size`() {
        val model = OfflineModelInfo(
            id = "test",
            title = "Test",
            repoId = "test/repo",
            filename = "test.task",
            license = "Apache 2.0",
            approxSizeMb = 1000,
            minRamGb = 2,
            promptStyle = PromptStyle.CHATML,
            description = "test"
        )

        assertEquals(2500, model.estimatedPeakMemoryMb)
        assertTrue(model.estimatedPeakMemoryMb > model.approxSizeMb)
    }

    @Test
    fun `explicit runtime metadata is preserved`() {
        val model = OfflineModelInfo(
            id = "test",
            title = "Test",
            repoId = "test/repo",
            filename = "test.litertlm",
            license = "Apache 2.0",
            approxSizeMb = 1000,
            minRamGb = 2,
            promptStyle = PromptStyle.QWEN3,
            description = "test",
            estimatedPeakMemoryMb = 3200,
            maxContextTokens = 4096,
            defaultMaxTokens = 1024
        )

        assertEquals(3200, model.estimatedPeakMemoryMb)
        assertEquals(4096, model.maxContextTokens)
        assertEquals(1024, model.defaultMaxTokens)
    }
}
