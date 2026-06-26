package com.brp.assistant.domain.model

/**
 * Typed wrapper for LLM engine responses.
 *
 * Every engine (MediaPipe GenAI, LiteRT, online provider) returns one of:
 *   - Success   — text generated, may be partial during streaming
 *   - Loading   — engine is initialising or generating, no output yet
 *   - Error     — recoverable error with user-visible message
 *   - Error.OOM — OutOfMemoryError: UI must show a "try smaller model" hint
 *
 * Usage in ChatViewModel:
 * ```
 *   _state.value = EngineResultState.Loading
 *   val result = runCatching { engine.generate(prompt) }
 *       .fold(
 *           onSuccess  = { EngineResultState.from(it) },
 *           onFailure  = { EngineResultState.error(it) }
 *       )
 *   _state.value = result
 * ```
 */
sealed class EngineResultState {

    object Loading : EngineResultState()

    data class Success(
        val text: String,
        val isPartial: Boolean = false,   // true during streaming
        val engineId: String  = "unknown" // which engine produced this
    ) : EngineResultState()

    open class Error(open val message: String) : EngineResultState() {
        /** Subtype specifically for OutOfMemoryError. */
        class OOM(cause: String = "") : Error(
            message = "Недостаточно памяти для модели. Попробуйте более лёгкую (Q4, 1B параметров)."
                    + if (cause.isNotBlank()) " ($cause)" else ""
        )
    }

    companion object {
        fun loading(): EngineResultState = Loading

        fun from(text: String, engineId: String = "unknown"): EngineResultState =
            Success(text, isPartial = false, engineId = engineId)

        fun error(throwable: Throwable): EngineResultState = when (throwable) {
            is OutOfMemoryError -> Error.OOM(throwable.message ?: "")
            else                -> Error(throwable.message ?: "Неизвестная ошибка")
        }
    }
}
