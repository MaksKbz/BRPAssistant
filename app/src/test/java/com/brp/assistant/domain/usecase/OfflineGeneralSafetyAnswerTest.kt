package com.brp.assistant.domain.usecase

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineGeneralSafetyAnswerTest {
    @Test
    fun `campfire question gets direct safe offline answer`() {
        val answer = OfflineGeneralSafetyAnswer.answerFor("Как развести огонь в лесу?")
        assertNotNull(answer)
        assertTrue(answer!!.contains("разрешённое место"))
        assertTrue(answer.contains("гидроцикле"))
        assertTrue(answer.contains("воду"))
    }

    @Test
    fun `unrelated question is left for the model`() {
        assertFalse(OfflineGeneralSafetyAnswer.answerFor("Как заменить масло?") != null)
    }
}
