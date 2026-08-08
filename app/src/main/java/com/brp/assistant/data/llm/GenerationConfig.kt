package com.brp.assistant.data.llm

/** Sampling and output limits used by a local runtime for one generation request. */
data class GenerationConfig(
    val maxTokens: Int,
    val temperature: Double,
    val topK: Int,
    val topP: Double
) {
    companion object {
        fun forModel(model: OfflineModelInfo?): GenerationConfig {
            return GenerationConfig(
                maxTokens = model?.defaultMaxTokens ?: 2048,
                temperature = 0.7,
                topK = 40,
                topP = 0.9
            )
        }
    }
}
