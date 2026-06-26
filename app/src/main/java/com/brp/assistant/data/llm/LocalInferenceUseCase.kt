package com.brp.assistant.data.llm

import android.util.Log
import com.brp.assistant.data.db.entities.Accessory
import com.brp.assistant.data.db.entities.BrpModel
import com.brp.assistant.data.db.entities.KnowledgeCard
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
         * Максимальное число символов в итоговом промпте.
         * ~1 токен ≈ 4 символа; 8000 симв ≈ 2000 токенов —
         * комфортный бюджет для большинства локальных моделей (2k–4k context).
         */
        private const val MAX_PROMPT_CHARS = 8_000
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
        val trimmedHistory = trimHistory(history, userMessage)
        val prompt = promptBuilder.buildDiagnosisPrompt(
            userMessage, cards, selectedModel, trimmedHistory, style
        )
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
        val trimmedHistory = trimHistory(history, userMessage)
        val prompt = promptBuilder.buildAccessoryPrompt(
            userMessage, accessories, selectedModel, trimmedHistory, style
        )
        return runInference(prompt, onPartial)
    }

    suspend fun modelSelection(
        userMessage: String,
        models: List<BrpModel>,
        history: List<ChatMessage>,
        onPartial: (String) -> Unit
    ): Result<String> {
        val style  = engine.getActivePromptStyle()
        val trimmedHistory = trimHistory(history, userMessage)
        val prompt = promptBuilder.buildModelSelectionPrompt(
            userMessage, models, trimmedHistory, style
        )
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
        val trimmedHistory = trimHistory(history, userMessage)
        val prompt = promptBuilder.buildFreeChatPrompt(
            userMessage, cards, accessories, selectedModel, trimmedHistory, style
        )
        return runInference(prompt, onPartial)
    }

    /**
     * Обрезает историю целыми сообщениями, если суммарная длина
     * userMessage + history превышает MAX_PROMPT_CHARS.
     *
     * Стратегия: удаляем старые сообщения с начала (контекст
     * всегда сохраняют последние сообщения).
     * Текущий userMessage некогда не выбрасывается.
     */
    private fun trimHistory(
        history: List<ChatMessage>,
        userMessage: String
    ): List<ChatMessage> {
        if (history.isEmpty()) return history

        // Резервируем бюджет для userMessage (всегда передаётся полностью)
        val reservedForCurrent = userMessage.length + 200 // +200 для системного промпта
        val budgetForHistory = MAX_PROMPT_CHARS - reservedForCurrent

        if (budgetForHistory <= 0) return emptyList()

        // Берём сообщения с конца (наиболее свежие) и набираем пока бюджет не исчерпан
        var used = 0
        val kept = mutableListOf<ChatMessage>()
        for (msg in history.asReversed()) {
            val msgLen = msg.content.length + 20 // +20 для ролевого префикса ("user:", "assistant:")
            if (used + msgLen > budgetForHistory) break
            kept.add(0, msg)
            used += msgLen
        }
        return kept
    }

    // ── Internal ────────────────────────────────────────────────
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
