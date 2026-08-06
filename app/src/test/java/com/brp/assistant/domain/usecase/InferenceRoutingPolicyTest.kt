package com.brp.assistant.domain.usecase

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InferenceRoutingPolicyTest {
    @Test
    fun `resource failure with configured key may fallback`() {
        assertTrue(InferenceRoutingPolicy.mayFallbackToRemote(
            IllegalStateException("Недостаточно памяти: heap 42 MB"), "key"
        ))
    }

    @Test
    fun `out of memory with configured key may fallback`() {
        assertTrue(InferenceRoutingPolicy.mayFallbackToRemote(OutOfMemoryError("OOM"), "key"))
    }

    @Test
    fun `resource failure without key cannot fallback`() {
        assertFalse(InferenceRoutingPolicy.mayFallbackToRemote(
            IllegalStateException("availRam is too low"), null
        ))
    }

    @Test
    fun `ordinary model failure cannot fallback`() {
        assertFalse(InferenceRoutingPolicy.mayFallbackToRemote(
            IllegalStateException("model format is invalid"), "key"
        ))
    }
}
