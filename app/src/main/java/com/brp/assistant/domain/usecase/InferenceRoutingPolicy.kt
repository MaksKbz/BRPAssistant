package com.brp.assistant.domain.usecase

/**
 * Allows local-to-remote fallback only for resource failures.
 * Model, prompt, configuration and network failures must remain visible to the user.
 */
object InferenceRoutingPolicy {
    private val resourceMarkers = listOf("недостаточно памяти", "outofmemory", "heap", "availram")

    fun mayFallbackToRemote(error: Throwable, remoteApiKey: String?): Boolean {
        if (remoteApiKey.isNullOrBlank()) return false
        if (error is OutOfMemoryError) return true
        val text = generateSequence(error) { it.cause }
            .joinToString(" ") { it.message.orEmpty() }
            .lowercase()
        return resourceMarkers.any(text::contains)
    }
}
