package com.brp.assistant.domain.tools

/** Typed, read-only tool calls available to a future local tool-calling adapter. */
sealed interface BrpReadOnlyToolCall {
    data class FindManualSection(val query: String) : BrpReadOnlyToolCall
    data class FindAccessory(val query: String) : BrpReadOnlyToolCall
    data class CalculateMaintenanceInterval(val engineHours: Int, val intervalHours: Int) : BrpReadOnlyToolCall
    data class CreateServiceChecklist(val items: List<String>) : BrpReadOnlyToolCall
}

data class BrpToolResult(val success: Boolean, val content: String)

/**
 * Safe BRP domain tools. There is intentionally no arbitrary code, network, filesystem or Intent
 * execution in this registry. Callers provide read-only domain lookups and own user confirmation.
 */
class BrpReadOnlyToolRegistry(
    private val findManualSection: suspend (String) -> String,
    private val findAccessory: suspend (String) -> String
) {
    suspend fun execute(call: BrpReadOnlyToolCall): BrpToolResult {
        return when (call) {
            is BrpReadOnlyToolCall.FindManualSection ->
                BrpToolResult(true, findManualSection(call.query.trim()))
            is BrpReadOnlyToolCall.FindAccessory ->
                BrpToolResult(true, findAccessory(call.query.trim()))
            is BrpReadOnlyToolCall.CalculateMaintenanceInterval -> {
                if (call.engineHours < 0 || call.intervalHours <= 0) {
                    BrpToolResult(false, "Некорректные значения моточасов")
                } else {
                    val remaining = (call.intervalHours - call.engineHours % call.intervalHours)
                        .let { if (it == call.intervalHours) 0 else it }
                    BrpToolResult(true, "До следующего интервала обслуживания: $remaining ч")
                }
            }
            is BrpReadOnlyToolCall.CreateServiceChecklist -> {
                val items = call.items.map(String::trim).filter(String::isNotBlank).distinct()
                if (items.isEmpty()) BrpToolResult(false, "Checklist пуст")
                else BrpToolResult(true, items.mapIndexed { index, item -> "${index + 1}. $item" }.joinToString("\n"))
            }
        }
    }
}
