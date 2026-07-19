package com.brp.assistant.data.rag

import com.brp.assistant.data.db.entities.Accessory
import com.brp.assistant.data.db.entities.BrpModel
import com.brp.assistant.data.db.entities.KnowledgeCard
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RelevanceScorer @Inject constructor() {

    /**
     * FIX: added optional selectedModel parameter.
     * Cards from the wrong modelFamily now receive a -30 penalty so they
     * never outrank on-model cards even with strong symptom overlap.
     */
    fun scoreKnowledgeCard(
        query: String,
        card: KnowledgeCard,
        selectedModel: BrpModel? = null
    ): Float {
        val q = query.lowercase()
        var score = 0f

        // 1. Symptom exact match (highest weight)
        val symptomLower = card.symptom.lowercase()
        if (symptomLower in q || q in symptomLower) score += 40f

        // 2. Symptom keyword overlap (стемминг)
        score += keywordOverlap(query, card.symptom) * 15f

        // 3. Causes keyword overlap
        score += keywordOverlap(query, card.causes) * 10f

        // 4. Full text keyword overlap
        score += keywordOverlap(query, card.fullText) * 5f

        // 5. Node match — расширенный список BRP-узловых терминов
        val nodes = listOf(
            // Двигатель / мотор
            "двигатель", "мотор", "цилиндр", "поршень", "клапан", "газораспределение",
            "зажигание", "свеча", "компрессия", "коленвал",
            // Трансмиссия / CVT
            "cvt", "ремень", "вариатор", "редуктор", "transmission",
            "4wd", "привод", "муфта", "полуось",
            // Охлаждение
            "охлажд", "радиатор", "термостат", "антифриз",
            // Электрика
            "электр", "аккумулятор", "генератор", "проводка", "ecu", "efi",
            // Подвеска / тормоз
            "подвеска", "амортизатор", "тормоз", "колодка",
            // Топливная
            "топливо", "инжектор", "насос", "фильтр",
            // BRP-специфично
            "ibr", "dps", "abc", "linq", "rotax", "etec", "ace", "turbo",
            // Английские алиасы
            "engine", "belt", "brake", "suspension", "fuel", "cooling",
            "steering", "electrical", "exhaust", "intake"
        )
        for (node in nodes) {
            if (q.contains(node) && card.node.lowercase().contains(node)) score += 20f
        }

        // 6. Risk level boost
        score += when (card.riskLevel) {
            "critical" -> 5f
            "high"     -> 3f
            "medium"   -> 1f
            else       -> 0f
        }

        // 7. Бонус за совпадение кода ошибки (DTC: P0xxx, E0xxx, U0xxx и т.д.)
        // При диагностике по коду ошибки это даёт точное сопоставление.
        val errorCodeRegex = Regex("[pPeEuUcC][0-9]{4}")
        val queryCode = errorCodeRegex.find(q.replace("\\s".toRegex(), ""))?.value?.lowercase()
        if (queryCode != null) {
            val cardText = (card.symptom + " " + card.causes + " " + card.fullText).lowercase()
            if (cardText.contains(queryCode)) score += 50f  // точное совпадение кода
        }

        // FIX 8: Model-family relevance boost / penalty
        if (selectedModel != null) {
            val familyMatch = !card.modelFamily.isNullOrBlank() &&
                card.modelFamily.equals(selectedModel.subcategory, ignoreCase = true)
            val familyMismatch = !card.modelFamily.isNullOrBlank() &&
                !card.modelFamily.equals(selectedModel.subcategory, ignoreCase = true)
            val categoryMismatch = !card.equipmentType.equals(selectedModel.category, ignoreCase = true)

            when {
                familyMatch       -> score += 25f
                familyMismatch    -> score -= 30f
                categoryMismatch  -> score -= 20f
            }

            if (!card.compatibleModels.isNullOrBlank() &&
                !card.compatibleModels.contains(selectedModel.id, ignoreCase = true) &&
                !card.compatibleModels.contains(selectedModel.subcategory ?: "", ignoreCase = true)
            ) {
                score -= 25f
            }
        }

        return score
    }

    fun scoreAccessory(query: String, accessory: Accessory): Float {
        val q = query.lowercase()
        var score = 0f

        val nameLower = accessory.name.lowercase()
        if (nameLower.contains(q) || q.contains(nameLower)) score += 40f
        score += keywordOverlap(query, accessory.name) * 15f
        score += keywordOverlap(query, accessory.description) * 8f
        accessory.tags?.let { score += keywordOverlap(query, it) * 12f }

        val cats = listOf(
            "хранение", "защита", "комфорт", "свет", "аудио", "лебёд",
            "ветров", "бампер", "крыша", "двер", "storage", "protection",
            "comfort", "light", "audio", "winch", "windshield", "bumper", "roof", "door",
            "linq", "cargo", "rack", "mirror", "зеркало", "порог", "багажник"
        )
        for (cat in cats) {
            if (q.contains(cat) && (
                    accessory.category.lowercase().contains(cat) ||
                    accessory.subcategory?.lowercase()?.contains(cat) == true
                )
            ) score += 20f
        }

        val combined = "${accessory.name} ${accessory.description} ${accessory.tags}".lowercase()
        if (listOf("руж", "оруж", "винтовк", "охот", "gun").any { q.contains(it) }) {
            if (combined.contains("gun")) score += 30f
            if (combined.contains("case") && combined.contains("gun")) score += 20f
        }
        if (listOf("еда", "еду", "пища", "продукт", "холодильник", "cooler", "пикник", "термос", "холод").any { q.contains(it) }) {
            if (combined.contains("cooler")) score += 35f
            if (combined.contains("bag") || combined.contains("box") || combined.contains("сумка") || combined.contains("кофр")) score += 10f
        }

        if (accessory.isNew2026 == 1) score += 3f
        return score
    }

    /**
     * Подсчитывает долю совпадающих ключевых слов между query и text.
     *
     * Применяет частичный стемминг для кириллицы: обрезает падежные окончания
     * (ево, его, а, я, ю, ь, и, т, ь) до корня. Это позволяет сопоставлять
     * "двигатель", "двигателя", "двигателем" — все к одному корню.
     */
    private fun keywordOverlap(query: String, text: String): Float {
        if (text.isBlank()) return 0f
        val stopWords = setOf(
            "не", "как", "что", "почему", "делать", "где", "когда", "какой", "какая",
            "какие", "может", "нужно", "подскажи", "посоветуй", "хочу", "бы",
            "подобрать", "выбрать", "купить", "рекомендуй", "the", "and", "for",
            "is", "my", "a", "an", "с", "на", "в", "и", "по", "из", "за", "о",
            "у", "от", "до", "но", "или", "это", "то", "все", "так", "же", "был",
            "было", "быть", "нет", "да", "уже", "ещё", "тоже", "при", "после"
        )
        val queryWords = query.lowercase()
            .replace(Regex("[^\u0430-\u044f\u0451a-z0-9\\s]"), "")
            .split("\\s+".toRegex())
            .filter { it.length > 2 && it !in stopWords }
            .map { stemCyrillic(it) }
            .toSet()
        if (queryWords.isEmpty()) return 0f
        val textLower = text.lowercase()
        // Стеммируем слова в тексте и сопоставляем со стеммированными словами запроса
        val textWords = textLower
            .replace(Regex("[^\u0430-\u044f\u0451a-z0-9\\s]"), "")
            .split("\\s+".toRegex())
            .filter { it.length > 2 }
            .map { stemCyrillic(it) }
            .toSet()
        val matched = queryWords.count { it in textWords }
        return matched.toFloat() / queryWords.size
    }

    /**
     * Упрощённый стеммер для русских слов: обрезает типичные падежные окончания.
     * Не является полным морфологическим анализатором, но значительно улучшает
     * сопоставление форм: "двигателя" → "двигател", "тормозами" → "тормозам".
     */
    private fun stemCyrillic(word: String): String {
        // Длинные окончания — в порядке убывания (от длинных к коротким)
        val suffixes = listOf(
            "евались", "овались",  // -евались
            "ивается", "ывается",  // глагольные
            "ивания", "ования",
            "ами", "ями",         // творительный падеж
            "ами", "еми",
            "ах", "ях",           // предложный падеж
            "ов", "ев",           // родительный падеж
            "ам", "ям",           // дательный
            "ого", "его",       // родительный прилагательное
            "ие", "ые",           // именительный мн.ч
            "ое", "ее",           // именительное ед.ч
            "ий", "ый", "ой", "ей",  // прилагательные окончания
            "ем", "им",           // дательный ед.ч
            "ии", "ы",            // генитив ед.ч / именительный мн.ч
            "у", "ю",             // винительный
            "а", "я",             // именительный ж.р. / мн.ч ср.р.
            "е",               // предложный
            "и",               // родительный ж.р.
            "ом", "ем",         // творительный
            "ость", "ости"    // существительные на -ость
        )
        // Минимальная длина корня: 4 символа — не обрезаем слишком короткие слова
        val minStemLength = 4
        for (suffix in suffixes) {
            if (word.endsWith(suffix) && word.length - suffix.length >= minStemLength) {
                return word.dropLast(suffix.length)
            }
        }
        return word
    }
}
