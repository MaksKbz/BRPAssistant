package com.brp.assistant.domain.usecase

import com.brp.assistant.data.llm.LlmInferenceEngine
import com.brp.assistant.data.llm.PromptBuilder
import com.brp.assistant.data.llm.RemoteLlmEngine
import com.brp.assistant.data.rag.*
import com.brp.assistant.data.repository.SettingsRepository
import com.brp.assistant.domain.model.ChatMessage
import com.brp.assistant.domain.model.DiagnosisResult
import com.brp.assistant.domain.model.RetrievalMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Определяет, является ли вопрос простым (приветствие, общение) — для таких
 * вопросов RAG-контекст не нужен и только путает маленькие модели.
 */
private fun isSimpleQuestion(message: String): Boolean {
    val lower = message.trim().lowercase()
    if (lower.length > 60) return false
    val triggers = listOf(
        "привет", "здравств", "хай", "hello", "hi", "помоги", "можешь помочь",
        "кто ты", "что ты умеешь", "что ты можешь", "как тебя зовут",
        "телефон", "контакт", "адрес", "как связаться", "дилер",
        "спасибо", "благодар", "ок", "понятно", "отлично", "хорошо"
    )
    return triggers.any { lower.contains(it) }
}

class DiagnoseUseCase @Inject constructor(
    private val retriever: UnifiedRetriever,
    private val promptBuilder: PromptBuilder,
    private val llm: LlmInferenceEngine,
    private val remoteLlm: RemoteLlmEngine,
    private val settingsRepository: SettingsRepository,
    private val modelRepository: com.brp.assistant.data.repository.ModelRepository
) {
    operator fun invoke(
        message: String,
        vehicleId: String?,
        history: List<ChatMessage>,
        onPartial: (String) -> Unit,
        forceRemote: Boolean = false
    ): Flow<Result<DiagnosisResult>> = flow {
        try {
            val provider = settingsRepository.aiProvider.first() ?: "Gemini"
            val apiKey = if (provider == "Gemini")
                settingsRepository.geminiApiKey.first()
            else
                settingsRepository.groqApiKey.first()
            val modelName = settingsRepository.aiModelName.first()
                ?: if (provider == "Gemini") "gemini-1.5-flash" else "llama-3.3-70b-versatile"
            val systemPrompt = settingsRepository.aiSystemPrompt.first()
            val temperature = settingsRepository.aiTemperature.first()

            val localReady = llm.isReady()
            val useRemote = forceRemote || (!localReady && !apiKey.isNullOrBlank())

            if (useRemote && apiKey.isNullOrBlank()) {
                emit(Result.failure(Exception(
                    "API-ключ для $provider не настроен. Перейдите в Настройки → AI-провайдер."
                )))
                return@flow
            }
            if (!localReady && !useRemote) {
                emit(Result.failure(Exception(
                    "Локальная модель не готова. Активируйте модель в Менеджере моделей или настройте API-ключ."
                )))
                return@flow
            }

            val brpModel = vehicleId?.let { modelRepository.getById(it) }

            // RAG: для простых вопросов (приветствия/благодарности) поиск не нужен.
            val simple = isSimpleQuestion(message)
            val retrieval = if (simple) null
                else retriever.retrieve(message, RetrievalMode.DIAGNOSIS, vehicleId, topK = if (useRemote) 7 else 3)
            val cards = retrieval?.cards?.map { it.card } ?: emptyList()
            val matchedChunks = retrieval?.matchedChunks ?: emptyList()
            val userChunks = retrieval?.userChunks ?: emptyList()

            // Если симптом полностью совпал с названием карточки — даём готовый ответ
            if (cards.any { it.symptom.contains(message, ignoreCase = true) }) {
                val bestMatch = cards.first { it.symptom.contains(message, ignoreCase = true) }
                val predefinedAnswer = "🤖 **Готовое решение:**\n\n${bestMatch.fullText}"
                onPartial(predefinedAnswer)
                emit(Result.success(DiagnosisResult(
                    message = predefinedAnswer,
                    riskLevel = bestMatch.riskLevel,
                    sources = listOf(bestMatch.symptom),
                    requiresEvacuation = bestMatch.requiresEvacuation == 1
                )))
                return@flow
            }

            // ЛОКАЛЬНАЯ: компактный промпт с топ-3 картами + релевантные чанки
            if (!useRemote) {
                val localPrompt = promptBuilder.buildLocalChatPrompt(
                    userMessage = message,
                    selectedModel = brpModel,
                    customSystemPrompt = systemPrompt,
                    cards = cards.take(3),
                    chunks = matchedChunks.take(4),
                    userChunks = userChunks.take(4)
                )
                val localResult = llm.generateResponse(localPrompt, onPartial)
                localResult.onSuccess { text ->
                    emit(Result.success(DiagnosisResult(
                        message = text,
                        riskLevel = "low",
                        sources = emptyList(),
                        requiresEvacuation = false
                    )))
                }.onFailure { emit(Result.failure(it)) }
                return@flow
            }

            // ОНЛАЙН: полный промпт с RAG
            val prompt = promptBuilder.buildDiagnosisPrompt(
                userMessage = message,
                cards = cards,
                selectedModel = brpModel,
                history = history,
                style = llm.getActivePromptStyle(),
                customSystemPrompt = systemPrompt
            )

            val result = remoteLlm.generateResponse(
                prompt = prompt,
                provider = provider,
                modelName = modelName,
                apiKey = apiKey!!,
                systemPrompt = systemPrompt,
                temperature = temperature,
                onPartial = onPartial
            )

            result.onSuccess { text ->
                val maxRisk = cards.maxOfOrNull {
                    when (it.riskLevel) { "critical" -> 4; "high" -> 3; "medium" -> 2; else -> 1 }
                } ?: 1
                emit(Result.success(DiagnosisResult(
                    message = text,
                    riskLevel = when (maxRisk) { 4 -> "critical"; 3 -> "high"; 2 -> "medium"; else -> "low" },
                    sources = cards.map { "${it.brand} ${it.symptom}" },
                    requiresEvacuation = cards.any { it.requiresEvacuation == 1 }
                )))
            }.onFailure { emit(Result.failure(it)) }
        } catch (e: Exception) { emit(Result.failure(e)) }
    }
}

class ChatUseCase @Inject constructor(
    private val retriever: UnifiedRetriever,
    private val promptBuilder: PromptBuilder,
    private val llm: LlmInferenceEngine,
    private val remoteLlm: RemoteLlmEngine,
    private val settingsRepository: SettingsRepository,
    private val modelRepository: com.brp.assistant.data.repository.ModelRepository
) {
    suspend operator fun invoke(
        message: String,
        mode: RetrievalMode,
        vehicleId: String?,
        history: List<ChatMessage>,
        onPartial: (String) -> Unit,
        forceRemote: Boolean = false
    ): Result<String> {
        val provider = settingsRepository.aiProvider.first() ?: "Gemini"
        val apiKey = if (provider == "Gemini")
            settingsRepository.geminiApiKey.first()
        else
            settingsRepository.groqApiKey.first()
        val modelName = settingsRepository.aiModelName.first()
            ?: if (provider == "Gemini") "gemini-1.5-flash" else "llama-3.3-70b-versatile"
        val systemPrompt = settingsRepository.aiSystemPrompt.first()
        val temperature = settingsRepository.aiTemperature.first()

        val localReady = llm.isReady()
        val useRemote = forceRemote || (!localReady && !apiKey.isNullOrBlank())

        if (useRemote && apiKey.isNullOrBlank()) {
            return Result.failure(Exception(
                "API-ключ для $provider не настроен. Перейдите в Настройки → AI-провайдер."
            ))
        }
        if (!localReady && !useRemote) {
            return Result.failure(Exception(
                "Локальная модель не готова. Активируйте модель в Менеджере моделей или настройте API-ключ."
            ))
        }

        val brpModel = vehicleId?.let { modelRepository.getById(it) }
        val simple = isSimpleQuestion(message)
        val retrieval = if (simple) null
            else retriever.retrieve(message, mode, vehicleId, topK = if (useRemote) 7 else 3)
        val cards = retrieval?.cards?.map { it.card } ?: emptyList()
        val chunks = retrieval?.matchedChunks ?: emptyList()
        val userChunks = retrieval?.userChunks ?: emptyList()
        val accessories = retrieval?.accessories?.map { it.accessory } ?: run {
            if (mode == RetrievalMode.ACCESSORY)
                retriever.retrieve(message, mode, vehicleId).accessories.map { it.accessory }
            else emptyList()
        }

        // ДЛЯ ЛОКАЛЬНЫХ МОДЕЛЕЙ: компактный сфокусированный промпт.
        if (!useRemote) {
            val localPrompt = promptBuilder.buildLocalChatPrompt(
                userMessage = message,
                selectedModel = brpModel,
                customSystemPrompt = systemPrompt,
                accessories = accessories.take(4),
                cards = cards.take(3),
                chunks = chunks.take(4),
                userChunks = userChunks.take(4)
            )
            return llm.generateResponse(localPrompt, onPartial)
        }

        // ОНЛАЙН: полный промпт с RAG
        val fullRetrieval = retriever.retrieve(message, mode, vehicleId)
        val fullCards = fullRetrieval.cards.map { it.card }.ifEmpty { cards }
        val fullAccessories = fullRetrieval.accessories.map { it.accessory }.ifEmpty { accessories }
        val fullUserChunks = fullRetrieval.userChunks.ifEmpty { userChunks }
        @Suppress("UNUSED_VARIABLE")
        val fullChunks = fullRetrieval.matchedChunks

        val prompt = promptBuilder.buildFreeChatPrompt(
            userMessage = message,
            history = history,
            cards = fullCards,
            accessories = fullAccessories,
            selectedModel = brpModel,
            style = llm.getActivePromptStyle(),
            customSystemPrompt = systemPrompt
        )

        return remoteLlm.generateResponse(
            prompt = prompt,
            provider = provider,
            modelName = modelName,
            apiKey = apiKey!!,
            systemPrompt = systemPrompt,
            temperature = temperature,
            onPartial = onPartial
        )
    }
}
