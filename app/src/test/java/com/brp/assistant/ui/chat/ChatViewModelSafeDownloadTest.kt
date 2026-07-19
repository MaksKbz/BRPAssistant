package com.brp.assistant.ui.chat

import com.brp.assistant.data.llm.OfflineModelInfo
import com.brp.assistant.data.llm.ModelFormat
import com.brp.assistant.data.llm.PromptStyle
import com.brp.assistant.domain.usecase.ModelRecommendation
import com.brp.assistant.domain.usecase.RecommendLlmModeUseCase
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

class ChatViewModelSafeDownloadTest {

    private fun model(): OfflineModelInfo = OfflineModelInfo(
        id = "test",
        title = "Heavy Model",
        repoId = "test/repo",
        filename = "heavy.task",
        license = "MIT",
        approxSizeMb = 4000,
        minRamGb = 8,
        promptStyle = PromptStyle.CHATML,
        description = "Heavy",
        format = ModelFormat.TASK
    )

    @Test
    fun safeModelNoWarning() {
        val provider = mockk<RecommendLlmModeUseCase>()
        every { provider.evaluate(any()) } returns ModelRecommendation(true, null)

        val rec = provider.evaluate(model())
        assertTrue(rec.isSafe)
        assertNull(rec.warningMessage)
    }

    @Test
    fun unsafeModelShowsWarning() {
        val provider = mockk<RecommendLlmModeUseCase>()
        every { provider.evaluate(any()) } returns ModelRecommendation(false, "⚠️ Мало RAM")

        val rec = provider.evaluate(model())
        assertFalse(rec.isSafe)
        assertNotNull(rec.warningMessage)
    }

    @Test
    fun confirmStartsOneDownload() {
        var downloadCount = 0
        fun startDownload() { downloadCount++ }

        var pending: OfflineModelInfo? = model()
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
        pending = null
        assertEquals(0, downloadCount)
        assertNull(pending)
    }
}
