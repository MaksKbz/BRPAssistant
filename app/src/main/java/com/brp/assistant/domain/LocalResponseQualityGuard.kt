package com.brp.assistant.domain

/** Rejects obviously stale/off-topic local output before it reaches the user. */
object LocalResponseQualityGuard {
    fun validate(prompt: String, response: String): Result<Unit> {
        val text = response.trim()
        if (text.length < 5) return Result.failure(IllegalStateException("Локальная модель вернула пустой ответ"))
        if (text.contains("<|im_start|>") || text.contains("<|im_end|>")) {
            return Result.failure(IllegalStateException("Локальная модель вернула служебные токены"))
        }

        if (prompt.contains("РЕЖИМ СРАВНЕНИЯ")) {
            val question = prompt.substringAfter("ВОПРОС КЛИЕНТА:", "").lowercase()
            val candidates = Regex("[a-zа-яё][a-zа-яё0-9-]{3,}", RegexOption.IGNORE_CASE)
                .findAll(question)
                .map { it.value }
                .filterNot { it in setOf("сравни", "сравнить", "технику", "модели", "техника", "клиента") }
                .toList()
            if (candidates.isNotEmpty() && candidates.none { text.lowercase().contains(it) }) {
                return Result.failure(IllegalStateException("Ответ локальной модели не относится к текущему сравнению"))
            }
        }
        return Result.success(Unit)
    }
}
