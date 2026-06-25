package com.brp.assistant.data.rag

import com.brp.assistant.data.db.enteties.Accessory
import com.brp.assistant.data.db.enteties.BrpModel
import com.brp.assistant.data.db.enteties.KnowledgeCard
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

        // 2. Symptom keyword overlap
        score += keywordOverlap(query, card.symptom) * 15f

        // 3. Causes keyword overlap
        score += keywordOverlap(query, card.causes) * 10f

        // 4. Full text keyword overlap
        score += keywordOverlap(query, card.fullText) * 5f

        // 5. Node match
        val nodes = listOf(
            "двигатель", "cvt", "ремен", "4wd", "привод", "охлажд",
            "электр", "подвеск", "тормоз", "transmission", "ibr", "топлив"
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

        // FIX 7: Model-family relevance boost / penalty
        if (selectedModel != null) {
            val familyMatch = !card.modelFamily.isNullOrBlank() &&
                card.modelFamily.equals(selectedModel.subcategory, ignoreCase = true)
            val familyMismatch = !card.modelFamily.isNullOrBlank() &&
                !card.modelFamily.equals(selectedModel.subcategory, ignoreCase = true)
            val categoryMismatch = !card.equipmentType.equals(selectedModel.category, ignoreCase = true)

            when {
                familyMatch       -> score += 25f  // exact model family match — boost
                familyMismatch    -> score -= 30f  // wrong model family — heavy penalty
                categoryMismatch  -> score -= 20f  // wrong vehicle category — penalty
            }

            // Extra penalty if compatibleModels explicitly lists other models
            // but NOT the selected one
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
            "comfort", "light", "audio", "winch", "windshield", "bumper", "roof", "door"
        )
        for (cat in cats) {
            if (q.contains(cat) && (
                    accessory.category.lowercase().contains(cat) ||
                    accessory.subcategory?.lowercase()?.contains(cat) == true
                )
            ) score += 20f
        }

        if (accessory.isNew2026 == 1) score += 3f
        return score
    }

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
            .replace(Regex("[^а-яёa-z0-9\\s]"), "")
            .split("\\s+".toRegex())
            .filter { it.length > 2 && it !in stopWords }
            .toSet()
        if (queryWords.isEmpty()) return 0f
        val textLower = text.lowercase()
        val matched = queryWords.count { textLower.contains(it) }
        return matched.toFloat() / queryWords.size
    }
}
