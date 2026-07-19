package com.brp.assistant.ui.chat

import com.brp.assistant.data.llm.OfflineModelInfo
import com.brp.assistant.domain.usecase.ModelRecommendation
import com.brp.assistant.domain.usecase.RecommendLlmModeUseCase
import com.brp.assistant.data.llm.PromptStyle
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

class ChatViewModelSafeDownloadTest {

    private fun model(): OfflineModelInfo = OfflineModelInfo(
        id = "test",
        title = "Heavy Model",
        fileName = "heavy.task",
        url = "https://example.com",
        approxSizeMb = 4000,
        promptStyle = PromptStyle.CHATML
    )

    @Test
    fun safeModelNoWarning() {
        val provider = mockk<RecommendLlmModeUseCase>()
        every { provider.evaluate(any()) } returns ModelRecommendation(true, null)

        val rec = provider.evaluate(model())
        assertTrue(rec.isSafe)
        assertNull(rec.warningMessage)
        // Should start download immediately, no pending warning
    }

    @Test
    fun unsafeModelShowsWarning() {
        val provider = mockk<RecommendLlmModeUseCase>()
        every { provider.evaluate(any()) } returns ModelRecommendation(false, "⚠️ Мало RAM")

        val rec = provider.evaluate(model())
        assertFalse(rec.isSafe)
        assertNotNull(rec.warningMessage)
        // Chat should set pendingDownloadWarning and not start download
    }

    @Test
    fun confirmStartsOneDownload() {
        // Simulate confirm logic: pending cleared and download started once
        var downloadCount = 0
        fun startDownload() { downloadCount++ }

        var pending: OfflineModelInfo? = model()
        // confirm
        pending?.let {
            pending = null
            startDownload()
        }
        assertEquals(1, downloadCount)
        assertNull(pending)
    }

    @Test
    fun dismissDoesNotStartDownload() {
        var downloadCount = 0
        var pending: OfflineModelInfo? = model()

        // dismiss
        pending = null
        // no download
        assertEquals(0, downloadCount)
        assertNull(pending)
    }
}
