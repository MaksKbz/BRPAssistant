package com.brp.assistant.data.llm

import android.util.Log
import com.brp.assistant.data.db.enteties.Accessory
import com.brp.assistant.data.db.enteties.BrpModel
import com.brp.assistant.data.db.enteties.KnowledgeCard
import com.brp.assistant.domain.model.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates prompt building + local inference.
 *
 * Usage:
 *   val result = localInferenceUseCase.diagnose(
 *       userMessage, cards, selectedModel, history, onPartial
 *   )
 *
 * All methods return Result<String>. On failure the caller should
 * fall back to RemoteLlmEngine.
 */
@Singleton
class LocalInferenceUseCase @Inject constructor(
    private val engine: LlmInferenceEngine,
    private val promptBuilder: PromptBuilder
) {
    companion object {
        private const val TAG = "LocalInferenceUseCase"
        /**
         * Rough token budget: 1 token ≈ 4 chars.
         * Keeps prompt within model context window.
         */
        private const val MAX_PROMPT_CHARS = 3_000
    }

    fun isReady() = engine.isReady()

    suspend fun diagnose(
        userMessage: String,
        cards: List<KnowledgeCard>,
        selectedModel: BrpModel?,
        history: List<ChatMessage>,
        onPartial: (String) -> Unit
    ): Result<String> {
        val style = engine.getActivePromptStyle()
        val prompt = promptBuilder.buildDiagnosisPrompt(
            userMessage, cards, selectedModel, history, style
        ).take(MAX_PROMPT_CHARS)
        return runInference(prompt, onPartial)
    }

    suspend fun accessories(
        userMessage: String,
        accessories: List<Accessory>,
        selectedModel: BrpModel?,
        history: List<ChatMessage>,
        onPartial: (String) -> Unit
    ): Result<String> {
        val style  = engine.getActivePromptStyle()
        val prompt = promptBuilder.buildAccessoryPrompt(
            userMessage, accessories, selectedModel, history, style
        ).take(MAX_PROMPT_CHARS)
        return runInference(prompt, onPartial)
    }

    suspend fun modelSelection(
        userMessage: String,
        models: List<BrpModel>,
        history: List<ChatMessage>,
        onPartial: (String) -> Unit
    ): Result<String> {
        val style  = engine.getActivePromptStyle()
        val prompt = promptBuilder.buildModelSelectionPrompt(
            userMessage, models, history, style
        ).take(MAX_PROMPT_CHARS)
        return runInference(prompt, onPartial)
    }

    suspend fun freeChat(
        userMessage: String,
        cards: List<KnowledgeCard>,
        accessories: List<Accessory>,
        selectedModel: BrpModel?,
        history: List<ChatMessage>,
        onPartial: (String) -> Unit
    ): Result<String> {
        val style  = engine.getActivePromptStyle()
        val prompt = promptBuilder.buildFreeChatPrompt(
            userMessage, cards, accessories, selectedModel, history, style
        ).take(MAX_PROMPT_CHARS)
        return runInference(prompt, onPartial)
    }

    // ── Internal ──────────────────────────────────────────────────
    private suspend fun runInference(
        prompt: String,
        onPartial: (String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!engine.isReady()) {
            return@withContext Result.failure(Exception("LocalModel: engine not ready"))
        }
        Log.d(TAG, "Sending prompt (${prompt.length} chars) to local model")
        engine.generateResponse(prompt, onPartial).also { result ->
            if (result.isFailure) Log.e(TAG, "Inference failed", result.exceptionOrNull())
        }
    }
}
