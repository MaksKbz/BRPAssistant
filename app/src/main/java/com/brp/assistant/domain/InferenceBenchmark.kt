package com.brp.assistant.domain

/** Measurements from one local inference request. */
data class InferenceBenchmark(
    val modelId: String?,
    val runtime: String,
    val timeToFirstTokenMs: Long?,
    val totalTimeMs: Long,
    val outputChars: Int,
    val outputTokensApprox: Int,
    val availableRamBeforeMb: Long,
    val availableRamAfterMb: Long,
    val batteryLevel: Int,
    val batterySaverOn: Boolean,
    val succeeded: Boolean
) {
    val tokensPerSecond: Double?
        get() = timeToFirstTokenMs?.let { first ->
            val generationMs = (totalTimeMs - first).coerceAtLeast(1L)
            outputTokensApprox * 1000.0 / generationMs
        }
}
