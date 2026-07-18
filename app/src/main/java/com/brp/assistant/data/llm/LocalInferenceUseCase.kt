package com.brp.assistant.data.llm

import android.util.Log
import com.brp.assistant.data.db.entities.Accessory
import com.brp.assistant.data.db.entities.BrpModel
import com.brp.assistant.data.db.entities.KnowledgeCard
import com.brp.assistant.domain.InferenceResourceMonitor
import com.brp.assistant.domain.model.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Оркестрирует сборку промпта + локальный инференс.
 *
 * До каждого запуска вызывает [InferenceResourceMonitor.checkMemory]
 * и отклоняет генерацию если RAM < 200 MB или heap < 50 MB.
 * Предупреждение о батарее передаётся в UI через onPartial().
 *
 * Все методы возвращают Result<String>.
 * При ошибке вызывающий код должен переключиться на RemoteLlmEngine.
 */
@Singleton
class LocalInferenceUseCase @Inject constructor(
    private val engine: LlmInferenceEngine,
    private val promptBuilder: PromptBuilder,
    private val resourceMonitor: InferenceResourceMonitor
) {
    companion object {
        private const val TAG = "LocalInferenceUseCase"

        /**
         * Максимальное число символов в итоговом промпте.
         * ~1 токен ≈ 4 символа; 8000 симв ≈ 2000 токенов.
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
        val style = engine.getActivePromptStyle()
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
        val style = engine.getActivePromptStyle()
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
        val style = engine.getActivePromptStyle()
        val trimmedHistory = trimHistory(history, userMessage)
        val prompt = promptBuilder.buildFreeChatPrompt(
            userMessage, cards, accessories, selectedModel, trimmedHistory, style
        )
        return runInference(prompt, onPartial)
    }

    /**
     * Обрезает историю целыми сообщениями если суммарная длина
     * userMessage + history превышает MAX_PROMPT_CHARS.
     * Удаляет старые сообщения с начала, сохраняя последние.
     */
    private fun trimHistory(
        history: List<ChatMessage>,
        userMessage: String
    ): List<ChatMessage> {
        if (history.isEmpty()) return history
        val reservedForCurrent = userMessage.length + 200
        val budgetForHistory = MAX_PROMPT_CHARS - reservedForCurrent
        if (budgetForHistory <= 0) return emptyList()
        var used = 0
        val kept = mutableListOf<ChatMessage>()
        for (msg in history.asReversed()) {
            val msgLen = msg.content.length + 20
            if (used + msgLen > budgetForHistory) break
            kept.add(0, msg)
            used += msgLen
        }
        return kept
    }

    /**
     * Запускает инференс с предварительной проверкой ресурсов.
     *
     * 1. checkMemory() — если недостаточно RAM/heap — Result.failure без crash.
     * 2. batteryWarning — если низкий заряд/Battery Saver — предупреждение через UI.
     * 3. Генерация запускается в обычном режиме.
     */
    private suspend fun runInference(
        prompt: String,
        onPartial: (String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!engine.isReady()) {
            return@withContext Result.failure(Exception("LocalModel: engine not ready"))
        }

        // Проверка ресурсов: RAM, heap, батарея
        val resourceCheck = resourceMonitor.checkMemory()
        if (!resourceCheck.isSafeForGeneration) {
            val msg = "⚠️ Недостаточно памяти для запуска модели " +
                "(RAM: ${resourceCheck.availRamMb} MB, heap: ${resourceCheck.freeHeapMb} MB). " +
                "Попробуйте закрыть другие приложения или перезагрузить."
            Log.w(TAG, msg)
            return@withContext Result.failure(Exception(msg))
        }

        // Предупреждение о батарее — показывается в UI перед генерацией
        resourceCheck.batteryWarning?.let { warning ->
            Log.d(TAG, "Battery warning: $warning")
            withContext(Dispatchers.Main) { onPartial("$warning\n\n") }
        }

        Log.d(TAG, "Sending prompt (${prompt.length} chars) to local model")
        engine.generateResponse(prompt, onPartial).also { result ->
            if (result.isFailure) Log.e(TAG, "Inference failed", result.exceptionOrNull())
        }
    }
}
