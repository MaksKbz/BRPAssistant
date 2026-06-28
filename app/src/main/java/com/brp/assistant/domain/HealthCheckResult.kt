package com.brp.assistant.domain

/**
 * Результат Health Check при запуске приложения.
 *
 * @param isStorageSufficient   Достаточно ли места на диске (> 200 МБ свободно).
 * @param hasActiveModel        Есть ли хотя бы одна скачанная/активная модель.
 * @param isDatabaseHealthy     База данных доступна и не повреждена.
 * @param freeMb                Свободное место в МБ (-1 если не удалось определить).
 * @param warnings              Список предупреждений для отображения пользователю.
 */
data class HealthCheckResult(
    val isStorageSufficient: Boolean,
    val hasActiveModel: Boolean,
    val isDatabaseHealthy: Boolean,
    val freeMb: Long = -1L,
    val warnings: List<String> = emptyList()
) {
    val hasIssues: Boolean
        get() = !isStorageSufficient || !isDatabaseHealthy

    val summary: String
        get() = warnings.joinToString("\n")
}
