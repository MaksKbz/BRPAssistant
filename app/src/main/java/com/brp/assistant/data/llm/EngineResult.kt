package com.brp.assistant.data.llm

/**
 * Унифицированная обёртка результата для всех LLM-движков.
 *
 * Заменяет голый Result<T> и позволяет явно различать:
 * - [Success]    — нормальный ответ
 * - [OomError]   — OutOfMemoryError при загрузке/инференсе модели
 * - [EngineError] — прочие ошибки движка (IO, native crash и т.д.)
 *
 * Использование:
 * ```kotlin
 * when (val r = engine.initialize(model)) {
 *     is EngineResult.Success      -> { /* ok */ }
 *     is EngineResult.OomError     -> showOomDialog(r.modelSizeMb)
 *     is EngineResult.EngineError  -> showError(r.cause.message)
 * }
 * ```
 */
sealed class EngineResult<out T> {

    data class Success<T>(val value: T) : EngineResult<T>()

    /**
     * Нехватка памяти при инициализации или инференсе.
     * @param modelSizeMb приблизительный размер модели для отображения в UI
     * @param availableRamMb доступная RAM на момент ошибки (для диагностики)
     */
    data class OomError(
        val modelSizeMb: Int,
        val availableRamMb: Long,
        val cause: OutOfMemoryError
    ) : EngineResult<Nothing>()

    /**
     * Прочие ошибки движка.
     */
    data class EngineError(
        val cause: Throwable,
        val message: String = cause.message ?: "Неизвестная ошибка движка"
    ) : EngineResult<Nothing>()

    // ── Convenience helpers ───────────────────────────────────────────────────

    val isSuccess get() = this is Success
    val isError   get() = this !is Success

    fun getOrNull(): T? = (this as? Success)?.value

    fun toResult(): Result<T> = when (this) {
        is Success     -> Result.success(value)
        is OomError    -> Result.failure(cause)
        is EngineError -> Result.failure(cause)
    }

    /**
     * Конвертировать стандартный Result<T> в EngineResult,
     * автоматически классифицируя OutOfMemoryError.
     */
    companion object {
        fun <T> fromResult(result: Result<T>, modelSizeMb: Int = 0, availableRamMb: Long = 0): EngineResult<T> {
            return result.fold(
                onSuccess = { Success(it) },
                onFailure = { e ->
                    when (e) {
                        is OutOfMemoryError -> OomError(modelSizeMb, availableRamMb, e)
                        else               -> EngineError(e)
                    }
                }
            )
        }

        /**
         * Безопасный вызов блока с автоматическим перехватом OOM и Throwable.
         */
        inline fun <T> runSafe(
            modelSizeMb: Int = 0,
            availableRamMb: Long = 0,
            block: () -> T
        ): EngineResult<T> {
            return try {
                Success(block())
            } catch (e: OutOfMemoryError) {
                OomError(modelSizeMb, availableRamMb, e)
            } catch (e: Throwable) {
                EngineError(e)
            }
        }
    }
}
