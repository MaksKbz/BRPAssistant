package com.brp.assistant.data.rag

import org.junit.Assert.*
import org.junit.Test

class AccessoryIntentDetectorTest {

    @Test
    fun gunIntentDetected() {
        assertTrue(AccessoryIntentDetector.isGunQuery("чехол для ружья"))
        assertTrue(AccessoryIntentDetector.isGunQuery("как перевозить ружье на Outlander"))
        assertTrue(AccessoryIntentDetector.isGunQuery("gun case mount"))
        assertTrue(AccessoryIntentDetector.detectIntents("охотничье оружие").contains(AccessoryIntent.GUN))
    }

    @Test
    fun foodIntentDetected() {
        assertTrue(AccessoryIntentDetector.isFoodQuery("холодильник для еды"))
        assertTrue(AccessoryIntentDetector.isFoodQuery("как перевозить еду и напитки"))
        assertTrue(AccessoryIntentDetector.isFoodQuery("cooler for picnic"))
        assertTrue(AccessoryIntentDetector.isFoodQuery("термос для питья"))
        assertTrue(AccessoryIntentDetector.isFoodQuery("сумка холодильник"))
    }

    @Test
    fun cargoIntentDetected() {
        assertTrue(AccessoryIntentDetector.isCargoQuery("кофр для вещей"))
        assertTrue(AccessoryIntentDetector.isCargoQuery("багажник LinQ"))
        assertTrue(AccessoryIntentDetector.detectIntents("cargo box storage").contains(AccessoryIntent.CARGO))
    }

    @Test
    fun reducerNotDetectedAsFood() {
        // Regression for "ед" trigger
        assertFalse(AccessoryIntentDetector.isFoodQuery("редуктор"))
        assertFalse(AccessoryIntentDetector.isFoodQuery("передний редуктор"))
        assertFalse(AccessoryIntentDetector.isFalseFoodTrigger("редуктор"))
        assertFalse(AccessoryIntentDetector.isFalseFoodTrigger("передний редуктор"))
        assertFalse(AccessoryIntentDetector.isFoodQuery("передача заднего редуктора"))
    }

    @Test
    fun gunQueryNotFood() {
        assertFalse(AccessoryIntentDetector.isFoodQuery("gun case holder"))
    }

    @Test
    fun otherIntents() {
        assertTrue(AccessoryIntentDetector.detectIntents("лебедка warn 3500").contains(AccessoryIntent.WINCH))
        assertTrue(AccessoryIntentDetector.detectIntents("светодиодная балка").contains(AccessoryIntent.LIGHT))
        assertTrue(AccessoryIntentDetector.detectIntents("зеркала боковые").contains(AccessoryIntent.MIRROR))
    }
}
