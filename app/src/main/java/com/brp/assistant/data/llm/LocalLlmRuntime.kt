package com.brp.assistant.data.llm

/**
 * Common lifecycle contract for the local LLM facade.
 *
 * Implementations must make stop safe to call before or during generation and must not emit
 * callbacks after close has started. Runtime-specific native APIs stay behind this contract.
 */
interface LocalLlmRuntime {
    suspend fun initialize(model: OfflineModelInfo): Result<Unit>

    suspend fun generateResponse(
        prompt: String,
        onPartial: (String) -> Unit,
        systemPrompt: String = ""
    ): Result<String>

    fun stop()

    suspend fun resetConversation()

    suspend fun close()
}
