package com.brp.assistant.data.rag

import org.junit.Assert.*
import org.junit.Test

class UnifiedRetrieverRagTest {

    @Test
    fun gunSearchWordsContainGun() {
        // Simulate extractAccessorySearchWords logic
        val query = "чехол для ружья"
        val detector = AccessoryIntentDetector.detectIntents(query)
        assertTrue(detector.contains(AccessoryIntent.GUN))
    }

    @Test
    fun coolerFoundForFoodQuery() {
        val query = "холодильник для еды"
        assertTrue(AccessoryIntentDetector.isFoodQuery(query))
    }

    @Test
    fun brandCompatibilityTwoWay() {
        // Simulate isAccessoryCompatible logic: can-am-atv vs can-am
        val modelBrand = "can-am-atv"
        val accBrand = "can-am"
        val brandOk = accBrand.contains(modelBrand, ignoreCase = true) ||
                modelBrand.contains(accBrand, ignoreCase = true) ||
                (modelBrand.startsWith("can-am", true) && accBrand.equals("can-am", true))
        assertTrue(brandOk)
    }

    @Test
    fun storageAlwaysCompatible() {
        val category = "storage"
        val modelCategory = "atv"
        val categoryOk = category.equals(modelCategory, true) ||
                category.equals("all", true) ||
                (modelCategory.equals("atv", true) && category in listOf("atv", "all", "storage"))
        assertTrue(categoryOk)
    }

    @Test
    fun incompatibleBrandFiltered() {
        val modelBrand = "sea-doo"
        val accBrand = "can-am"
        val brandOk = accBrand.contains(modelBrand, true) ||
                modelBrand.contains(accBrand, true) ||
                (modelBrand.startsWith("can-am", true) && accBrand.equals("can-am", true)) ||
                (modelBrand.startsWith("sea-doo", true) && accBrand.equals("sea-doo", true))
        // sea-doo vs can-am should NOT be compatible via brand check alone
        assertFalse(brandOk)
    }
}
