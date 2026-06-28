package com.brp.assistant.domain.usecase

import com.brp.assistant.domain.model.ChatMessage
import javax.inject.Inject

/**
 * #9 — UseCase автоматической генерации заголовка сессии.
 *
 * Генерирует краткий title из истории диалога без сетевого вызова:
 *   1. Берёт первое user-сообщение из [messages].
 *   2. Убирает лишние пробелы и управляющие символы.
 *   3. Обрезает до [maxLength] символов, добавляет '…' если текст длиннее.
 *   4. Возвращает [fallback] если история пуста или первое сообщение пустое.
 *
 * Stateless — создаётся per-ViewModel через @Inject (не @Singleton).
 *
 * Использование:
 * ```kotlin
 * class ChatViewModel @Inject constructor(
 *     private val summaryUseCase: ConversationSummaryUseCase,
 *     ...
 * ) {
 *     fun onFirstReply(messages: List<ChatMessage>) {
 *         val title = summaryUseCase(messages)   // «Не заводится двигатель…»
 *         // сохранить title в БД
 *     }
 * }
 * ```
 *
 * В следующей итерации можно добавить опциональный LLM-путь:
 * если useAi=true — делегировать RemoteLlmEngine с prompt
 * «Summarize this question in 5 words».
 */
class ConversationSummaryUseCase @Inject constructor() {

    /**
     * Генерирует title для [messages].
     *
     * @param messages  История диалога (порядок: хронологический ASC).
     * @param maxLength Максимальная длина строки в символах (default 60).
     * @param fallback  Возвращается если нет user-сообщений (default «Новый чат»).
     * @return          Краткий заголовок ≤ [maxLength] символов.
     */
    operator fun invoke(
        messages: List<ChatMessage>,
        maxLength: Int = DEFAULT_MAX_LENGTH,
        fallback: String = DEFAULT_FALLBACK
    ): String {
        val firstUserText = messages
            .firstOrNull { it.isUser }
            ?.text
            ?.trim()
            ?.replace(Regex("[\\r\\n\\t]+"), " ")   // убираем переносы строк
            ?.replace(Regex(" {2,}"), " ")           // схлопываем двойные пробелы
            .orEmpty()

        if (firstUserText.isBlank()) return fallback

        return if (firstUserText.length <= maxLength) {
            firstUserText
        } else {
            // Обрезаем по последнему пробелу перед maxLength-1,
            // чтобы не разрывать слово посередине.
            val cutPoint = firstUserText.lastIndexOf(' ', maxLength - 1)
                .takeIf { it > maxLength / 2 }   // пробел должен быть в разумной позиции
                ?: (maxLength - 1)                // иначе жёсткий обрез
            firstUserText.substring(0, cutPoint).trimEnd() + ELLIPSIS
        }
    }

    companion object {
        const val DEFAULT_MAX_LENGTH = 60
        const val DEFAULT_FALLBACK = "Новый чат"
        private const val ELLIPSIS = "…"   // U+2026, один символ
    }
}
