package com.brp.assistant.data.llm

import org.junit.Assert.assertEquals
import org.junit.Test

class GenerationConfigTest {
    @Test
    fun `model metadata controls output budget`() {
        val model = OfflineModelInfo(
            id = "qwen",
            title = "Qwen",
            repoId = "repo",
            filename = "qwen.litertlm",
            license = "Apache 2.0",
            approxSizeMb = 600,
            minRamGb = 3,
            promptStyle = PromptStyle.QWEN3,
            description = "test",
            defaultMaxTokens = 1024
        )

        val config = GenerationConfig.forModel(model)
        assertEquals(1024, config.maxTokens)
        assertEquals(40, config.topK)
        assertEquals(0.9, config.topP, 0.0)
    }

    @Test
    fun `missing model uses safe defaults`() {
        val config = GenerationConfig.forModel(null)
        assertEquals(2048, config.maxTokens)
        assertEquals(0.7, config.temperature, 0.0)
    }
}
