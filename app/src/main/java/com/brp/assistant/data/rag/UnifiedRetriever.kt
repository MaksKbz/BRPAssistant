package com.brp.assistant.data.rag

import com.brp.assistant.data.db.AccessoryDao
import com.brp.assistant.data.db.KnowledgeDao
import com.brp.assistant.data.db.ModelDao
import com.brp.assistant.data.db.enteties.Accessory
import com.brp.assistant.data.db.enteties.BrpModel
import com.brp.assistant.data.db.enteties.KnowledgeCard
import com.brp.assistant.domain.model.RetrievalMode
import javax.inject.Inject
import javax.inject.Singleton

data class RetrievalResult(
    val cards: List<ScoredCard>,
    val accessories: List<ScoredAccessory>,
    val models: List<BrpModel>
)

data class ScoredCard(val card: KnowledgeCard, val score: Float)
data class ScoredAccessory(val accessory: Accessory, val score: Float)

@Singleton
class UnifiedRetriever @Inject constructor(
    private val knowledgeDao: KnowledgeDao,
    private val accessoryDao: AccessoryDao,
    private val modelDao: ModelDao,
    private val scorer: RelevanceScorer
) {

    suspend fun retrieve(
        query: String,
        mode: RetrievalMode,
        selectedModelId: String? = null,
        topK: Int = 5
    ): RetrievalResult {

        val keywords = extractKeywords(query)

        // Resolve selected model once
        val selectedModel: BrpModel? = selectedModelId?.let { modelDao.getById(it) }

        // ── Knowledge cards ──────────────────────────────────────────────────────
        val cards = when (mode) {
            RetrievalMode.DIAGNOSIS, RetrievalMode.BOTH -> {

                // 1. FTS search across all cards
                val ftsRaw = if (keywords.isNotBlank())
                    knowledgeDao.searchFullText(keywords, topK * 3)
                else emptyList()

                // 2. Strict model-scoped candidates
                //    FIX: use AND (brand AND equipmentType) instead of OR
                //    Priority: modelFamily match > brand+category > brand only
                val scopedCards: List<KnowledgeCard> = if (selectedModel != null) {
                    val byFamily = knowledgeDao.getByModelFamily(
                        brand = selectedModel.brand,
                        modelFamily = selectedModel.subcategory  // e.g. "Renegade"
                    )
                    val byBrandAndCat = knowledgeDao.getByBrandAndCategory(
                        brand = selectedModel.brand,
                        category = selectedModel.category
                    )
                    // Merge: family-specific first, then brand+category, deduplicate
                    (byFamily + byBrandAndCat).distinctBy { it.id }
                } else {
                    // No model selected — return nothing from scoped pool;
                    // rely entirely on FTS so we don't dump the whole DB
                    emptyList()
                }

                // 3. Combine FTS + scoped, then apply strict model filter
                val combined = (ftsRaw + scopedCards).distinctBy { it.id }

                val filtered = if (selectedModel != null) {
                    combined.filter { card ->
                        isCardRelevantToModel(card, selectedModel)
                    }
                } else {
                    // No model context — only return FTS hits, limit scope
                    ftsRaw.distinctBy { it.id }
                }

                filtered
                    .map { ScoredCard(it, scorer.scoreKnowledgeCard(query, it, selectedModel)) }
                    .sortedByDescending { it.score }
                    .take(topK)
            }
            RetrievalMode.ACCESSORY -> emptyList()
        }

        // ── Accessories ──────────────────────────────────────────────────────────
        val accessories = when (mode) {
            RetrievalMode.ACCESSORY, RetrievalMode.BOTH -> {
                val byModel = selectedModelId?.let { accessoryDao.getForModel(it) } ?: emptyList()
                val byQuery = if (keywords.isNotBlank()) accessoryDao.search(keywords, topK * 2) else emptyList()
                val byPlatform = selectedModel?.platform?.let { plat ->
                    accessoryDao.getByPlatform(plat)
                } ?: emptyList()

                (byModel + byQuery + byPlatform)
                    .distinctBy { it.id }
                    .filter { acc ->
                        // FIX: require explicit model compatibility when model is selected
                        if (selectedModel != null) {
                            isAccessoryCompatible(acc, selectedModel)
                        } else true
                    }
                    .map { ScoredAccessory(it, scorer.scoreAccessory(query, it)) }
                    .sortedByDescending { it.score }
                    .take(topK)
            }
            RetrievalMode.DIAGNOSIS -> emptyList()
        }

        val modelResults = modelDao.search(
            query = if (keywords.isNotBlank()) keywords else null
        )

        return RetrievalResult(cards, accessories, modelResults)
    }

    /**
     * FIX: strict relevance check for a knowledge card against the selected model.
     * A card is relevant only if:
     *   - brand matches exactly, AND
     *   - equipmentType matches the model's category, AND
     *   - modelFamily is null (generic) OR matches model's subcategory
     */
    private fun isCardRelevantToModel(card: KnowledgeCard, model: BrpModel): Boolean {
        val brandMatch = card.brand.equals(model.brand, ignoreCase = true)
        val categoryMatch = card.equipmentType.equals(model.category, ignoreCase = true)
        val familyMatch = card.modelFamily.isNullOrBlank() ||
            card.modelFamily.equals(model.subcategory, ignoreCase = true)
        // compatibleModels JSON check: if card explicitly lists models and selected
        // model is NOT in the list — exclude it
        val compatibleModelsOk = card.compatibleModels.isNullOrBlank() ||
            card.compatibleModels.contains(model.id, ignoreCase = true) ||
            card.compatibleModels.contains(model.subcategory ?: "", ignoreCase = true)

        return brandMatch && categoryMatch && familyMatch && compatibleModelsOk
    }

    /**
     * FIX: accessory compatibility — must match model id OR platform.
     * byModel join already guarantees compatibility, but byQuery/byPlatform
     * results need explicit check.
     */
    private fun isAccessoryCompatible(acc: Accessory, model: BrpModel): Boolean {
        val platformOk = !model.platform.isNullOrBlank() &&
            acc.compatiblePlatforms?.contains(model.platform, ignoreCase = true) == true
        val brandOk = acc.brand.contains(model.brand, ignoreCase = true)
        val modelIdOk = acc.compatibleModels?.contains(model.id, ignoreCase = true) == true
        return platformOk || modelIdOk || brandOk
    }

    private fun extractKeywords(query: String): String {
        val stopWords = setOf(
            "не", "как", "что", "почему", "делать", "где", "когда", "какой",
            "подскажи", "посоветуй", "хочу", "подобрать", "выбрать", "купить",
            "рекомендуй", "нужно", "может", "какая", "какие", "бы"
        )
        return query.lowercase()
            .replace(Regex("[^а-яёa-z0-9\\s]"), "")
            .split("\\s+".toRegex())
            .filter { it.length > 2 && it !in stopWords }
            .joinToString(" OR ")
    }
}
