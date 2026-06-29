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
            // FIX #4: читаем все настройки атомарно здесь,
            // чтобы provider, key и systemPrompt не расходились с тем,
            // что перечитывает RemoteLlmEngine.generateResponse()
            val provider = settingsRepository.aiProvider.first() ?: "Gemini"
            val apiKey = if (provider == "Gemini")
                settingsRepository.geminiApiKey.first()
            else
                settingsRepository.groqApiKey.first()
            val modelName = settingsRepository.aiModelName.first()
                ?: if (provider == "Gemini") "gemini-1.5-flash" else "llama-3.3-70b-versatile"
            val systemPrompt = settingsRepository.aiSystemPrompt.first()
            val temperature = settingsRepository.aiTemperature.first()

            // FIX роутинга: приоритет у АКТИВНОЙ локальной модели (см. ChatUseCase).
            val localReady = llm.isReady()
            val useRemote = !localReady && !apiKey.isNullOrBlank()
            val isLocalProvider = provider.equals("Local", ignoreCase = true)

            if (!localReady && !useRemote && !isLocalProvider) {
                emit(Result.failure(Exception(
                    if (apiKey.isNullOrBlank())
                        "Не настроена локальная модель и нет API-ключа $provider. " +
                        "Скачайте офлайн-модель или укажите ключ в Настройках → AI-провайдер."
                    else
                        "API-ключ для $provider не настроен. Перейдите в Настройки → AI-провайдер."
                )))
                return@flow
            }
            if (!localReady && !useRemote) {
                emit(Result.failure(Exception(
                    "Локальная модель не готова и онлайн не настроен. " +
                    "Активируйте модель в Менеджере моделей или настройте API-ключ."
                )))
                return@flow
            }

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

            // FIX #4: передаём provider/modelName/apiKey/systemPrompt явно,
            // а не полагаемся на повторное чтение DataStore внутри Engine
            val result = if (useRemote) {
                remoteLlm.generateResponse(
                    prompt = prompt,
                    provider = provider,
                    modelName = modelName,
                    apiKey = apiKey!!,
                    systemPrompt = systemPrompt,
                    temperature = temperature,
                    onPartial = onPartial
                )
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
        // Читаем все настройки за один раз, передаём явно.
        val provider = settingsRepository.aiProvider.first() ?: "Gemini"
        val apiKey = if (provider == "Gemini")
            settingsRepository.geminiApiKey.first()
        else
            settingsRepository.groqApiKey.first()
        val modelName = settingsRepository.aiModelName.first()
            ?: if (provider == "Gemini") "gemini-1.5-flash" else "llama-3.3-70b-versatile"
        val systemPrompt = settingsRepository.aiSystemPrompt.first()
        val temperature = settingsRepository.aiTemperature.first()

        // FIX роутинга: приоритет у АКТИВНОЙ локальной модели.
        // Раньше useRemote = !apiKey.isNullOrBlank() — если настроен ключ Gemini/Groq,
        // чат ВСЕГДА шёл в облако, даже при активированной локальной модели.
        // Теперь: если локальная модель инициализирована и готова — используем её;
        // онлайн — только фолбэк, когда локальная не готова.
        val localReady = llm.isReady()
        val useRemote = !localReady && !apiKey.isNullOrBlank()
        val isLocalProvider = provider.equals("Local", ignoreCase = true)

        if (!localReady && !useRemote && !isLocalProvider) {
            return Result.failure(Exception(
                if (apiKey.isNullOrBlank())
                    "Не настроена локальная модель и нет API-ключа $provider. " +
                    "Скачайте офлайн-модель или укажите ключ в Настройках → AI-провайдер."
                else
                    "API-ключ для $provider не настроен. Перейдите в Настройки → AI-провайдер."
            ))
        }
        if (!localReady && !useRemote) {
            return Result.failure(Exception(
                "Локальная модель не готова и онлайн не настроен. " +
                "Активируйте модель в Менеджере моделей или настройте API-ключ."
            ))
        }

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
            remoteLlm.generateResponse(
                prompt = prompt,
                provider = provider,
                modelName = modelName,
                apiKey = apiKey!!,
                systemPrompt = systemPrompt,
                temperature = temperature,
                onPartial = onPartial
            )
        } else {
            llm.generateResponse(prompt, onPartial)
        }
    }
}
