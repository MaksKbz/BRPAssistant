package com.brp.assistant.data.rag

import com.brp.assistant.data.db.enteties.Accessory
import com.brp.assistant.data.db.enteties.BrpModel
import com.brp.assistant.data.db.enteties.KnowledgeCard
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ln

/**
 * BM25-inspired relevance scorer for RAG retrieval.
 *
 * Replaces the naive keyword-overlap scorer with Okapi BM25
 * term-frequency saturation and IDF weighting.
 *
 * TF saturation: tf_sat = (tf*(k1+1)) / (tf + k1*(1-b+b*fieldLen/avgFieldLen))
 * IDF approx:    idf = ln((N - df + 0.5)/(df + 0.5) + 1)
 *
 * k1=1.5, b=0.75 (standard Okapi BM25 constants)
 * Field weights: symptom:3 / causes:2 / fullText:1
 */
@Singleton
class RelevanceScorer @Inject constructor() {

    private val k1 = 1.5f
    private val b  = 0.75f

    // Approximate field-length averages for BRP knowledge base
    private val avgSymptomLen   = 8f
    private val avgCausesLen    = 15f
    private val avgFullTextLen  = 60f
    private val avgAccessNameLen = 6f
    private val avgAccessDescLen = 20f
    private val N = 100f  // assumed corpus size

    fun scoreKnowledgeCard(
        query: String,
        card: KnowledgeCard,
        selectedModel: BrpModel? = null
    ): Float {
        val tokens = tokenize(query)
        if (tokens.isEmpty()) return 0f

        var score = bm25Field(tokens, card.symptom,  avgSymptomLen)  * 3f
        score    += bm25Field(tokens, card.causes,   avgCausesLen)   * 2f
        score    += bm25Field(tokens, card.fullText, avgFullTextLen) * 1f

        // Node-type structural boost
        val nodeKeywords = listOf(
            "\u0434\u0432\u0438\u0433\u0430\u0442\u0435\u043b\u044c", "cvt", "\u0440\u0435\u043c\u0435\u043d", "4wd", "\u043f\u0440\u0438\u0432\u043e\u0434", "\u043e\u0445\u043b\u0430\u0436\u0434",
            "\u044d\u043b\u0435\u043a\u0442\u0440", "\u043f\u043e\u0434\u0432\u0435\u0441\u043a", "\u0442\u043e\u0440\u043c\u043e\u0437", "transmission", "ibr", "\u0442\u043e\u043f\u043b\u0438\u0432"
        )
        val q = query.lowercase()
        nodeKeywords.forEach { kw ->
            if (q.contains(kw) && card.node.lowercase().contains(kw)) score += 5f
        }

        score += when (card.riskLevel) {
            "critical" -> 2f
            "high"     -> 1f
            else       -> 0f
        }

        if (selectedModel != null) {
            val familyMatch = !card.modelFamily.isNullOrBlank() &&
                card.modelFamily.equals(selectedModel.subcategory, ignoreCase = true)
            val familyMismatch = !card.modelFamily.isNullOrBlank() &&
                !card.modelFamily.equals(selectedModel.subcategory, ignoreCase = true)
            val categoryMismatch = !card.equipmentType.equals(selectedModel.category, ignoreCase = true)

            score += when {
                familyMatch      ->  8f
                familyMismatch   -> -10f
                categoryMismatch ->  -7f
                else             ->  0f
            }
            if (!card.compatibleModels.isNullOrBlank() &&
                !card.compatibleModels.contains(selectedModel.id, ignoreCase = true) &&
                !card.compatibleModels.contains(selectedModel.subcategory ?: "", ignoreCase = true)
            ) score -= 8f
        }
        return score
    }

    fun scoreAccessory(query: String, accessory: Accessory): Float {
        val tokens = tokenize(query)
        if (tokens.isEmpty()) return 0f

        var score = bm25Field(tokens, accessory.name, avgAccessNameLen) * 4f
        score    += bm25Field(tokens, accessory.description, avgAccessDescLen) * 2f
        accessory.tags?.let { score += bm25Field(tokens, it, avgAccessDescLen) * 3f }

        val q = query.lowercase()
        val catKeywords = listOf(
            "\u0445\u0440\u0430\u043d\u0435\u043d\u0438\u0435", "\u0437\u0430\u0449\u0438\u0442\u0430", "\u043a\u043e\u043c\u0444\u043e\u0440\u0442", "\u0441\u0432\u0435\u0442", "\u0430\u0443\u0434\u0438\u043e", "\u043b\u0435\u0431\u0451\u0434",
            "\u0432\u0435\u0442\u0440\u043e\u0432", "\u0431\u0430\u043c\u043f\u0435\u0440", "\u043a\u0440\u044b\u0448\u0430", "\u0434\u0432\u0435\u0440",
            "storage", "protection", "comfort", "light", "audio", "winch", "windshield", "bumper", "roof", "door"
        )
        catKeywords.forEach { kw ->
            if (q.contains(kw) && (
                accessory.category.lowercase().contains(kw) ||
                accessory.subcategory?.lowercase()?.contains(kw) == true
            )) score += 3f
        }
        if (accessory.isNew2026 == 1) score += 1f
        return score
    }

    private fun bm25Field(tokens: List<String>, field: String, avgLen: Float): Float {
        if (field.isBlank()) return 0f
        val fieldTokens = tokenize(field)
        val fieldLen = fieldTokens.size.toFloat().coerceAtLeast(1f)
        val tfMap = HashMap<String, Int>(fieldTokens.size)
        for (t in fieldTokens) tfMap[t] = (tfMap[t] ?: 0) + 1

        var score = 0f
        for (token in tokens) {
            val tf: Float = tfMap[token]?.toFloat() ?: run {
                // partial match fallback
                fieldTokens.count { it.contains(token) || token.contains(it) }.toFloat() * 0.6f
            }
            if (tf == 0f) continue
            val tfSat = (tf * (k1 + 1f)) / (tf + k1 * (1f - b + b * fieldLen / avgLen))
            val idf   = ln((N - 1f + 0.5f) / (1f + 0.5f) + 1f)
            score += tfSat * idf
        }
        return score
    }

    private val stopWords = setOf(
        "\u043d\u0435", "\u043a\u0430\u043a", "\u0447\u0442\u043e", "\u043f\u043e\u0447\u0435\u043c\u0443", "\u0434\u0435\u043b\u0430\u0442\u044c", "\u0433\u0434\u0435", "\u043a\u043e\u0433\u0434\u0430", "\u043a\u0430\u043a\u043e\u0439", "\u043a\u0430\u043a\u0430\u044f",
        "\u043a\u0430\u043a\u0438\u0435", "\u043c\u043e\u0436\u0435\u0442", "\u043d\u0443\u0436\u043d\u043e", "\u043f\u043e\u0434\u0441\u043a\u0430\u0436\u0438", "\u043f\u043e\u0441\u043e\u0432\u0435\u0442\u0443\u0439", "\u0445\u043e\u0447\u0443", "\u0431\u044b",
        "\u043f\u043e\u0434\u043e\u0431\u0440\u0430\u0442\u044c", "\u0432\u044b\u0431\u0440\u0430\u0442\u044c", "\u043a\u0443\u043f\u0438\u0442\u044c", "\u0440\u0435\u043a\u043e\u043c\u0435\u043d\u0434\u0443\u0439", "the", "and", "for",
        "is", "my", "a", "an", "\u0441", "\u043d\u0430", "\u0432", "\u0438", "\u043f\u043e", "\u0438\u0437", "\u0437\u0430", "\u043e",
        "\u0443", "\u043e\u0442", "\u0434\u043e", "\u043d\u043e", "\u0438\u043b\u0438", "\u044d\u0442\u043e", "\u0442\u043e", "\u0432\u0441\u0435", "\u0442\u0430\u043a", "\u0436\u0435", "\u0431\u044b\u043b",
        "\u0431\u044b\u043b\u043e", "\u0431\u044b\u0442\u044c", "\u043d\u0435\u0442", "\u0434\u0430", "\u0443\u0436\u0435", "\u0435\u0449\u0451", "\u0442\u043e\u0436\u0435", "\u043f\u0440\u0438", "\u043f\u043e\u0441\u043b\u0435"
    )

    private fun tokenize(text: String): List<String> =
        text.lowercase()
            .replace(Regex("[^\u0430-\u044f\u0451a-z0-9\\s\\-]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 2 && it !in stopWords }
}