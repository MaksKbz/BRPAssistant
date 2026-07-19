package com.brp.assistant.domain

import com.brp.assistant.data.rag.AccessoryIntentDetector
import com.brp.assistant.data.rag.AccessoryIntent
import org.junit.Assert.*
import org.junit.Test

class AccessoryRagRegressionTest {

    @Test
    fun reducerIsNotFood() {
        assertFalse(AccessoryIntentDetector.isFoodQuery("редуктор"))
        assertFalse(AccessoryIntentDetector.isFoodQuery("передний редуктор"))
        assertFalse(AccessoryIntentDetector.isFoodQuery("задний редуктор в сборе"))
        assertFalse(AccessoryIntentDetector.isFoodQuery("передача заднего редуктора"))
    }

    @Test
    fun gunQueries() {
        assertTrue(AccessoryIntentDetector.isGunQuery("чехол для ружья"))
        assertTrue(AccessoryIntentDetector.isGunQuery("как перевозить ружьё на Outlander X mr 850"))
        assertTrue(AccessoryIntentDetector.isGunQuery("кронштейн для ружья LinQ"))
    }

    @Test
    fun foodQueries() {
        assertTrue(AccessoryIntentDetector.isFoodQuery("как перевозить еду"))
        assertTrue(AccessoryIntentDetector.isFoodQuery("холодильник для еды и напитков"))
        assertTrue(AccessoryIntentDetector.isFoodQuery("термос и сумка холодильник"))
        assertTrue(AccessoryIntentDetector.isFoodQuery("cooler для пикника"))
    }

    @Test
    fun cargoQueries() {
        assertTrue(AccessoryIntentDetector.isCargoQuery("кофр для вещей"))
        assertTrue(AccessoryIntentDetector.isCargoQuery("багажник LinQ и сумка"))
    }

    @Test
    fun cargoNotFalseFood() {
        // cargo words contain "box" etc, should not be food unless food intent also present
        assertFalse(AccessoryIntentDetector.isFoodQuery("коробка передач редуктора"))
    }
}
