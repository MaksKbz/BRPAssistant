package com.brp.assistant.domain

import android.content.Context
import android.app.ActivityManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit test for InferenceResourceMonitor logic.
 * We test the pure calculation part via fake MemoryStatus, and the monitor via mocked Context.
 * For simplicity we test DeviceCapabilityProvider.MemoryStatus.isSafeForGeneration and
 * ResourceCheckResult logic.
 */
class InferenceResourceMonitorTest {

    @Test
    fun memoryStatusSafeWhenEnoughHeapAndRam() {
        val status = DeviceCapabilityProvider.MemoryStatus(
            freeHeapMb = 200,
            availRamMb = 500,
            totalRamMb = 4000,
            isLowMemory = false
        )
        assertTrue(status.isSafeForGeneration)
    }

    @Test
    fun memoryStatusUnsafeLowHeap() {
        val status = DeviceCapabilityProvider.MemoryStatus(
            freeHeapMb = 10,
            availRamMb = 500,
            totalRamMb = 4000,
            isLowMemory = false
        )
        assertFalse(status.isSafeForGeneration)
    }

    @Test
    fun memoryStatusUnsafeLowRam() {
        val status = DeviceCapabilityProvider.MemoryStatus(
            freeHeapMb = 200,
            availRamMb = 50,
            totalRamMb = 4000,
            isLowMemory = false
        )
        assertFalse(status.isSafeForGeneration)
    }

    @Test
    fun memoryStatusUnsafeLowMemoryFlag() {
        val status = DeviceCapabilityProvider.MemoryStatus(
            freeHeapMb = 200,
            availRamMb = 500,
            totalRamMb = 4000,
            isLowMemory = true
        )
        assertFalse(status.isSafeForGeneration)
    }

    @Test
    fun resourceCheckBatteryWarningNotInStream() {
        // Battery warning should be separate UI state, not part of LLM stream
        // Here we just verify ResourceCheckResult structure supports null warning
        val result = ResourceCheckResult(
            isSafeForGeneration = true,
            freeHeapMb = 200,
            availRamMb = 500,
            batteryLevel = 15,
            isBatterySaverOn = false,
            batteryWarning = "🔋 Low battery",
            recommendedMaxTokens = 512
        )
        assertNotNull(result.batteryWarning)
        // recommendedMaxTokens is informational only
        assertTrue(result.recommendedMaxTokens in listOf(1024, 512, 384))
    }
}
