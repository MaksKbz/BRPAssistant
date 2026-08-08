package com.brp.assistant.domain.tools

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrpReadOnlyToolRegistryTest {
    private val registry = BrpReadOnlyToolRegistry(
        findManualSection = { "manual:$it" },
        findAccessory = { "accessory:$it" }
    )

    @Test
    fun `manual and accessory calls are delegated as read only lookups`() = runTest {
        assertEquals("manual:brake", registry.execute(BrpReadOnlyToolCall.FindManualSection(" brake ")).content)
        assertEquals("accessory:winch", registry.execute(BrpReadOnlyToolCall.FindAccessory(" winch ")).content)
    }

    @Test
    fun `maintenance and checklist tools validate inputs`() = runTest {
        assertEquals("До следующего интервала обслуживания: 0 ч", registry.execute(
            BrpReadOnlyToolCall.CalculateMaintenanceInterval(500, 100)
        ).content)
        assertTrue(registry.execute(BrpReadOnlyToolCall.CreateServiceChecklist(listOf("Oil", "Oil", " Filter "))).success)
        assertFalse(registry.execute(BrpReadOnlyToolCall.CreateServiceChecklist(emptyList())).success)
    }
}
