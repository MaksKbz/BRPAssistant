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

        // Knowledge cards
        val cards = when (mode) {
            RetrievalMode.DIAGNOSIS, RetrievalMode.BOTH -> {
                val fts = if (keywords.isNotBlank()) {
                    knowledgeDao.searchFullText(keywords, topK * 2)
                } else emptyList()
                
                // Фильтрация по бренду и категории выбранной модели
                val brandCards = selectedModelId?.let { mid ->
                    modelDao.getById(mid)?.let { m ->
                        knowledgeDao.getByBrandOrCategory(m.brand, m.category)
                    }
                } ?: knowledgeDao.getAll()
                
                (fts + brandCards)
                    .distinctBy { it.id }
                    .filter { card ->
                        // Если модель выбрана, отдаем приоритет или жестко фильтруем
                        selectedModelId?.let { mid ->
                            val m = modelDao.getById(mid)
                            m == null || card.brand.equals(m.brand, ignoreCase = true) || card.equipmentType.equals(m.category, ignoreCase = true)
                        } ?: true
                    }
                    .map { ScoredCard(it, scorer.scoreKnowledgeCard(query, it)) }
                    .sortedByDescending { it.score }
                    .take(topK)
            }
            RetrievalMode.ACCESSORY -> emptyList()
        }

        // Accessories
        val accessories = when (mode) {
            RetrievalMode.ACCESSORY, RetrievalMode.BOTH -> {
                val byModel = selectedModelId?.let { accessoryDao.getForModel(it) } ?: emptyList()
                val byQuery = if (keywords.isNotBlank()) accessoryDao.search(keywords, topK * 2) else emptyList()
                val byPlatform = selectedModelId?.let { mid ->
                    modelDao.getById(mid)?.platform?.let { plat ->
                        accessoryDao.getByPlatform(plat)
                    }
                } ?: emptyList()
                
                (byModel + byQuery + byPlatform)
                    .distinctBy { it.id }
                    .filter { acc ->
                        // Дополнительная проверка на совместимость если модель выбрана
                        selectedModelId?.let { mid ->
                            val m = modelDao.getById(mid)
                            m == null || acc.brand.contains(m.brand, ignoreCase = true) || acc.compatiblePlatforms?.contains(m.platform ?: "", ignoreCase = true) == true
                        } ?: true
                    }
                    .map { ScoredAccessory(it, scorer.scoreAccessory(query, it)) }
                    .sortedByDescending { it.score }
                    .take(topK)
            }
            RetrievalMode.DIAGNOSIS -> emptyList()
        }

        // Models for selection mode
        val modelResults = modelDao.search(query = if (keywords.isNotBlank()) keywords else null)

        return RetrievalResult(cards, accessories, modelResults)
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
