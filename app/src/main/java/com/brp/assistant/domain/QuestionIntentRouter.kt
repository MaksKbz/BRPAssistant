package com.brp.assistant.domain

enum class QuestionIntent {
    GENERAL_OUTDOOR,
    COMPARE_MODELS,
    ACCESSORY_LOOKUP,
    TECHNICAL_DIAGNOSIS,
    SAFETY_CRITICAL,
    GENERAL
}

/** Small deterministic router kept outside the LLM so a weak model gets a narrow prompt. */
object QuestionIntentRouter {
    fun classify(text: String): QuestionIntent {
        val q = text.lowercase()
        if (listOf("огонь", "костёр", "костер", "топлив", "утечк", "травм", "тонет", "лёд", "лед", "гроза").any(q::contains)) {
            return QuestionIntent.SAFETY_CRITICAL
        }
        if (listOf("сравни", "сравнить", "разница", "отличие", "что лучше").any(q::contains)) {
            return QuestionIntent.COMPARE_MODELS
        }
        if (listOf("аксессуар", "кофр", "багаж", "лебёд", "лебед", "креплен").any(q::contains)) {
            return QuestionIntent.ACCESSORY_LOOKUP
        }
        if (listOf("лес", "природ", "поход", "кемпинг", "палат", "рыбал", "ориентир", "ночёв", "ночев").any(q::contains) &&
            listOf("двигател", "ремень", "масл", "ошибк", "гидроцикл", "sea-doo", "can-am", "ski-doo", "lynx").none(q::contains)) {
            return QuestionIntent.GENERAL_OUTDOOR
        }
        if (listOf("не завод", "не включ", "перегрев", "ошибка", "ремень", "масло", "вариатор", "тормоз").any(q::contains)) {
            return QuestionIntent.TECHNICAL_DIAGNOSIS
        }
        return QuestionIntent.GENERAL
    }
}
