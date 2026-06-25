package com.brp.assistant.data.llm.download

import android.content.Context
import com.brp.assistant.data.llm.OfflineModelInfo
import com.brp.assistant.data.llm.PromptBuilder
import java.util.Locale

/**
 * Helper for building a consultant prompt from built-in JSON files.
 */
class JsonConsultantPromptBuilder(
    private val context: Context,
    private val promptBuilder: PromptBuilder
) {
    fun buildPromptFromAssets(
        model: OfflineModelInfo,
        assetJsonPaths: List<String>,
        userQuestion: String,
        maxKnowledgeChars: Int = 10000
    ): String {
        val fullJson = assetJsonPaths
            .joinToString(separator = "\n\n") { path ->
                try {
                    context.assets.open(path).bufferedReader().use { it.readText() }
                } catch (e: Exception) { "" }
            }

        val relevantContext = promptBuilder.selectRelevantJsonContext(fullJson, userQuestion, maxKnowledgeChars)
        return promptBuilder.buildConsultantPrompt(model, relevantContext, userQuestion)
    }
}
