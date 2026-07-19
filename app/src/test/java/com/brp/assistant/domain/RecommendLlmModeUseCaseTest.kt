package com.brp.assistant.domain

import com.brp.assistant.data.llm.OfflineModelInfo
import com.brp.assistant.domain.usecase.RecommendLlmModeUseCase
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

class RecommendLlmModeUseCaseTest {

    private val deviceProvider = mockk<DeviceCapabilityProvider>()
    private val useCase = RecommendLlmModeUseCase(deviceProvider)

    private fun model(approxMb: Long, title: String = "Test"): OfflineModelInfo {
        return OfflineModelInfo(
            id = "test-id",
            title = title,
            fileName = "test.task",
            url = "https://example.com/model.task",
            approxSizeMb = approxMb,
            promptStyle = com.brp.assistant.data.llm.PromptStyle.CHATML,
            isCustom = false
        )
    }

    @Test
    fun unknownSizeIsSafe() {
        every { deviceProvider.isSafeForModel(any()) } returns true
        val m = model(0)
        val rec = useCase.evaluate(m)
        assertTrue(rec.isSafe)
        assertNull(rec.warningMessage)
    }

    @Test
    fun safeModelNoWarning() {
        every { deviceProvider.isSafeForModel(any()) } returns true
        val m = model(600)
        val rec = useCase.evaluate(m)
        assertTrue(rec.isSafe)
    }

    @Test
    fun unsafeModelReturnsWarning() {
        every { deviceProvider.isSafeForModel(any()) } returns false
        val m = model(4000, "Qwen3-4B")
        val rec = useCase.evaluate(m)
        assertFalse(rec.isSafe)
        assertNotNull(rec.warningMessage)
        assertTrue(rec.warningMessage!!.contains("может работать нестабильно"))
    }

    @Test
    fun warningContainsTitle() {
        every { deviceProvider.isSafeForModel(any()) } returns false
        val m = model(1700, "Qwen3-1.7B")
        val rec = useCase.evaluate(m)
        assertTrue(rec.warningMessage!!.contains("Qwen3-1.7B"))
    }
}
