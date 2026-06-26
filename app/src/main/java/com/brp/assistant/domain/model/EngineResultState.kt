package com.brp.assistant.domain.model

/**
 * Единый sealed wrapper для результата LLM-генерации.
 *
 * Заменяет прокидывание сырых исключений из MediaPipe / LiteRT / RemoteLlmEngine
 * типизированным ответом — UI-слой получает готовый к показу объект.
 *
 * Используется как тип возврата в [ChatViewModel.sendMessage] и
 * прокидывается в [ChatState.engineResult] для отображения точных статусов.
 */
sealed class EngineResultState {

    /** Успешно сгенерированный или частичный стриминговый ответ. */
    data class Success(
        val text: String,
        /** true — промежуточный чанк стриминга, false — финальный ответ */
        val isPartial: Boolean = false,
        /** Идентификатор движка ("mediapipe", "litert", "gemini", "groq", "ollama") */
        val engineId: String = ""
    ) : EngineResultState()

    /** Обобщённая ошибка генерации — не OOM. */
    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : EngineResultState() {

        /**
         * Специализация для OutOfMemoryError:
         * содержит подсказку о выборе более лёгкой модели.
         */
        data class OOM(
            val heapMb: Int,
            val ramMb: Int
        ) : EngineResultState() {
            val userMessage: String
                get() = "💾 Модель не поместилась в память устройства. " +
                        "Доступно heap: ~${heapMb} МБ, RAM: ~${ramMb} МБ. " +
                        "Попробуйте более лёгкую модель (≤1.5B) или перезапустите приложение."
        }
    }

    /** Состояние «генерация в процессе» — используется для индикатора загрузки. */
    object Loading : EngineResultState()

    // ── Фабричные методы ────────────────────────────────────────────────────────
    companion object {
        /** Создать из произвольного Throwable — автоматически определяет OOM. */
        fun from(
            throwable: Throwable,
            heapMb: Int = 0,
            ramMb: Int = 0
        ): EngineResultState = when (throwable) {
            is OutOfMemoryError -> Error.OOM(heapMb, ramMb)
            else                -> Error(throwable.message ?: "Неизвестная ошибка", throwable)
        }

        /** Фабрика для промежуточного стримингового чанка. */
        fun loading(): Loading = Loading

        /** Фабрика для успешного ответа. */
        fun success(text: String, engineId: String = "", isPartial: Boolean = false): Success =
            Success(text, isPartial, engineId)

        /** Фабрика для ошибки без Throwable (текстовое сообщение). */
        fun error(message: String): Error = Error(message)
    }
}
