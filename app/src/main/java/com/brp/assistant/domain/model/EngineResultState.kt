package com.brp.assistant.domain.model

/**
 * Единый sealed-враппер для всех операций LLM-движков.
 *
 * Используйте вместо разрозненных Result<T>, Exception и необработанных OOM-крэшей.
 * Все ViewModel должны обрабатывать результат через этот класс.
 *
 * [Loading]  — операция в процессе, можно передать частичный [partial] ответ
 * [Success]  — ответ готов, содержит [data]
 * [Error]    — обычная ошибка LLM-движка, [message] готов к показу пользователю
 * [OomError] — недостаточно памяти, [usedMb]/[availMb] для диагностики
 * [Idle]     — начальное состояние, ничего не запущено
 */
sealed class EngineResultState<out T> {

    data object Idle : EngineResultState<Nothing>()

    data class Loading<T>(
        val partial: String = ""
    ) : EngineResultState<T>()

    data class Success<T>(
        val data: T
    ) : EngineResultState<T>()

    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : EngineResultState<Nothing>()

    /**
     * Отдельный подкласс для OOM — позволяет UI предложить
     * пользователю переключиться на легкую модель или закрыть фоновые задачи.
     */
    data class OomError(
        val usedMb: Long = 0L,
        val availMb: Long = 0L
    ) : EngineResultState<Nothing>() {
        val message: String get() =
            "Недостаточно памяти (исп. ${usedMb} МБ, доступ. ${availMb} МБ). " +
            "Попробуйте переключиться на модель поменьше или освободить RAM."

        companion object
    }
}

/** Краткий helper: создаёт OomError с данными Runtime.getRuntime() */
fun EngineResultState.OomError.Companion.capture(): EngineResultState.OomError {
    val rt = Runtime.getRuntime()
    val usedMb  = (rt.totalMemory() - rt.freeMemory()) / 1_048_576L
    val availMb = rt.maxMemory() / 1_048_576L
    return EngineResultState.OomError(usedMb, availMb)
}
