package com.brp.assistant.data.rag

import com.brp.assistant.data.db.AccessoryDao
import com.brp.assistant.data.db.KnowledgeDao
import com.brp.assistant.data.db.ModelDao
import com.brp.assistant.data.db.entities.Accessory
import com.brp.assistant.data.db.entities.BrpModel
import com.brp.assistant.data.db.entities.KnowledgeCard
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

        val selectedModel: BrpModel? = selectedModelId?.let { modelDao.getById(it) }

        // ── Knowledge cards ──────────────────────────────────────────────────────
        val cards = when (mode) {
            RetrievalMode.DIAGNOSIS, RetrievalMode.BOTH -> {

                // 1. FTS search
                val ftsRaw = if (keywords.isNotBlank())
                    knowledgeDao.searchFullText(keywords, topK * 3)
                else emptyList()

                /**
                 * FIX #8: фоллбек на LIKE-поиск при пустых FTS-результатах.
                 *
                 * SQLite FTS4 с токенайзером unicode61 плохо обрабатывает
                 * кириллицу на Android — запрос с русскими словами нередко
                 * возвращает пустой список даже при наличии совпадений.
                 *
                 * Решение: если FTS вернул пустой список, делаем LIKE-поиск
                 * через getByBrandAndCategory или getByModelFamily (они уже
                 * существуют в KnowledgeDao) и фильтруем по symptom/fullText.
                 * LIKE работает с кириллицей корректно.
                 */
                val ftsResults = if (ftsRaw.isEmpty() && keywords.isNotBlank() && selectedModel != null) {
                    // Фоллбек: берём все карточки по модели и фильтруем вручную
                    val byFamily = knowledgeDao.getByModelFamily(
                        brand = selectedModel.brand,
                        modelFamily = selectedModel.subcategory
                    )
                    val firstKeyword = keywords.split(" OR ").firstOrNull()?.trim() ?: ""
                    if (firstKeyword.isNotBlank()) {
                        byFamily.filter { card ->
                            card.symptom.contains(firstKeyword, ignoreCase = true) ||
                            card.fullText.contains(firstKeyword, ignoreCase = true) ||
                            card.causes.contains(firstKeyword, ignoreCase = true)
                        }
                    } else byFamily
                } else ftsRaw

                // 2. Strict model-scoped candidates
                val scopedCards: List<KnowledgeCard> = if (selectedModel != null) {
                    val byFamily = knowledgeDao.getByModelFamily(
                        brand = selectedModel.brand,
                        modelFamily = selectedModel.subcategory
                    )
                    val byBrandAndCat = knowledgeDao.getByBrandAndCategory(
                        brand = selectedModel.brand,
                        category = selectedModel.category
                    )
                    (byFamily + byBrandAndCat).distinctBy { it.id }
                } else {
                    emptyList()
                }

                // 3. Combine FTS + scoped
                val combined = (ftsResults + scopedCards).distinctBy { it.id }

                val filtered = if (selectedModel != null) {
                    combined.filter { card -> isCardRelevantToModel(card, selectedModel) }
                } else {
                    ftsResults.distinctBy { it.id }
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
                        if (selectedModel != null) isAccessoryCompatible(acc, selectedModel)
                        else true
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

    private fun isCardRelevantToModel(card: KnowledgeCard, model: BrpModel): Boolean {
        val brandMatch = card.brand.equals(model.brand, ignoreCase = true)
        val categoryMatch = card.equipmentType.equals(model.category, ignoreCase = true)
        val familyMatch = card.modelFamily.isNullOrBlank() ||
            card.modelFamily.equals(model.subcategory, ignoreCase = true)
        val compatibleModelsOk = card.compatibleModels.isNullOrBlank() ||
            card.compatibleModels.contains(model.id, ignoreCase = true) ||
            card.compatibleModels.contains(model.subcategory ?: "", ignoreCase = true)
        return brandMatch && categoryMatch && familyMatch && compatibleModelsOk
    }

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
            // FIX: порог > 1 вместо > 2, чтобы BRP/ECU/RPM/CAN не отсеивались
            .filter { it.length > 1 && it !in stopWords }
            .joinToString(" OR ")
    }
}
