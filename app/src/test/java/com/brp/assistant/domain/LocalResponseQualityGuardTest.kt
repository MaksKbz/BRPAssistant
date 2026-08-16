package com.brp.assistant.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalResponseQualityGuardTest {
    @Test
    fun `comparison response must mention requested model`() {
        val prompt = "РЕЖИМ СРАВНЕНИЯ: сравнивай только модели\nВОПРОС КЛИЕНТА: Сравни Ryker 600 и Ryker Rally 900"
        assertTrue(LocalResponseQualityGuard.validate(prompt, "Ryker 600 легче, Rally 900 мощнее").isSuccess)
        assertFalse(LocalResponseQualityGuard.validate(prompt, "Почему важно выключить двигатель перед разборкой?").isSuccess)
    }

    @Test
    fun `service markers are rejected`() {
        assertFalse(LocalResponseQualityGuard.validate("ВОПРОС КЛИЕНТА: вопрос", "<|im_start|>bad").isSuccess)
    }
}
