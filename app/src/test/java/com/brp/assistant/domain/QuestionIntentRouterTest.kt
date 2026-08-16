package com.brp.assistant.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class QuestionIntentRouterTest {
    @Test
    fun `routes comparison separately from vehicle context`() {
        assertEquals(QuestionIntent.COMPARE_MODELS, QuestionIntentRouter.classify("Сравни Ryker 600 и Ryker Rally 900"))
    }

    @Test
    fun `routes general outdoor safety`() {
        assertEquals(QuestionIntent.SAFETY_CRITICAL, QuestionIntentRouter.classify("Как развести огонь в лесу"))
    }
}
