package com.brp.assistant.data.llm

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Загружает системный промпт из assets/prompts/system_prompt.txt с кешированием.
 *
 * В промпте содержится описание брендов BRP, двигателей Rotax и базовые правила
 * работы ассистента. Он используется как системное сообщение для ЛОКАЛЬНЫХ
 * и ОНЛАЙН-моделей вместо хардкода в [PromptBuilder].
 */
@Singleton
class SystemPromptProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @Volatile
    private var cached: String? = null

    /**
     * Возвращает системный промпт из assets/prompts/system_prompt.txt.
     * При ошибке чтения возвращается минимальный дефолтный промпт.
     */
    fun getSystemPrompt(): String {
        cached?.let { return it }
        val text = runCatching {
            context.assets.open("prompts/system_prompt.txt")
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText().trim() }
        }.getOrElse {
            DEFAULT_FALLBACK
        }
        cached = text
        return text
    }

    companion object {
        private const val DEFAULT_FALLBACK =
            "Ты BRP-ассистент. Отвечай кратко, чётко и по существу вопроса на русском языке. " +
                "Используй только предоставленную информацию о технике BRP."
    }
}
