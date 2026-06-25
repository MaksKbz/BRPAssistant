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
        onPartial: (String) -> Unit
    ): Flow<Result<DiagnosisResult>> = flow {
        try {
            // FIX #1: read the correct API key based on selected provider
            val provider = settingsRepository.aiProvider.first() ?: "Gemini"
            val apiKey = if (provider == "Gemini")
                settingsRepository.geminiApiKey.first()
            else
                settingsRepository.grokApiKey.first()
            val useRemote = !apiKey.isNullOrBlank()

            val brpModel = vehicleId?.let { modelRepository.getById(it) }
            val retrieval = retriever.retrieve(message, RetrievalMode.DIAGNOSIS, vehicleId)
            val cards = retrieval.cards.map { it.card }

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

            val prompt = promptBuilder.buildDiagnosisPrompt(
                userMessage = message,
                cards = cards,
                selectedModel = brpModel,
                history = history,
                style = llm.getActivePromptStyle()
            )

            val result = if (useRemote) {
                remoteLlm.generateResponse(prompt, onPartial)
            } else {
                llm.generateResponse(prompt, onPartial)
            }

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
        onPartial: (String) -> Unit
    ): Result<String> {
        // FIX #1: read the correct API key based on selected provider
        val provider = settingsRepository.aiProvider.first() ?: "Gemini"
        val apiKey = if (provider == "Gemini")
            settingsRepository.geminiApiKey.first()
        else
            settingsRepository.grokApiKey.first()
        val useRemote = !apiKey.isNullOrBlank()

        val retrieval = retriever.retrieve(message, mode, vehicleId)
        val cards = retrieval.cards.map { it.card }
        val accessories = retrieval.accessories.map { it.accessory }
        val brpModel = vehicleId?.let { modelRepository.getById(it) }

        val prompt = promptBuilder.buildFreeChatPrompt(
            userMessage = message,
            history = history,
            cards = cards,
            accessories = accessories,
            selectedModel = brpModel,
            style = llm.getActivePromptStyle()
        )

        return if (useRemote) {
            remoteLlm.generateResponse(prompt, onPartial)
        } else {
            llm.generateResponse(prompt, onPartial)
        }
    }
}
