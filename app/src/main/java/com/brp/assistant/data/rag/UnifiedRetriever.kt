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

    /**
     * FIX: topK повышен до 7 (было 5) — даёт больше кандидатов для скоринга,
     * что особенно важно при малой базе знаний или одном бренде.
     * Порог снижен до 2f (было 3f): уменьшает пропуск неочевидных, но релевантных
     * карточек при неполных запросах (1-2 ключевых слова).
     */
    suspend fun retrieve(
        query: String,
        mode: RetrievalMode,
        selectedModelId: String? = null,
        topK: Int = 7
    ): RetrievalResult {

        val keywords = extractKeywords(query)

        val selectedModel: BrpModel? = selectedModelId?.let { modelDao.getById(it) }

        // ── Knowledge cards ────────────────────────────────────────────────────────────────────
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
                 * возвращает пустой список. LIKE-поиск работает корректно.
                 */
                val ftsResults = if (ftsRaw.isEmpty() && keywords.isNotBlank() && selectedModel != null) {
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
                    .filter { it.score > 2f }   // FIX: снижен порог 3f → 2f
                    .take(topK)
            }
            RetrievalMode.ACCESSORY -> emptyList()
        }

        // ── Accessories ────────────────────────────────────────────────────────────────────
        val accessories = run {
            val searchWords = extractAccessorySearchWords(query)
            val allAcc = accessoryDao.getAll()
            val byWords = if (searchWords.isNotEmpty()) {
                allAcc.filter { acc ->
                    val text = "${acc.name} ${acc.description} ${acc.sku} ${acc.tags}".lowercase()
                    searchWords.any { word -> text.contains(word.lowercase()) }
                }
            } else emptyList()

            val byModel = selectedModelId?.let { accessoryDao.getForModel(it) } ?: emptyList()
            val byPlatform = selectedModel?.platform?.let { plat ->
                accessoryDao.getByPlatform(plat)
            } ?: emptyList()

            (byWords + byModel + byPlatform)
                .distinctBy { it.id }
                .filter { acc ->
                    if (selectedModel != null) isAccessoryCompatible(acc, selectedModel)
                    else true
                }
                .map { ScoredAccessory(it, scorer.scoreAccessory(query, it)) }
                .sortedByDescending { it.score }
                .filter { it.score >= 0f }
                .take(topK)
        }

        val modelResults = modelDao.search(
            query = if (keywords.isNotBlank()) keywords else null
        )

        return RetrievalResult(cards, accessories, modelResults)
    }

    private fun isCardRelevantToModel(card: KnowledgeCard, model: BrpModel): Boolean {
        val brandMatch = card.brand.equals(model.brand, ignoreCase = true) ||
            model.brand.contains(card.brand, ignoreCase = true) ||
            card.brand.contains(model.brand, ignoreCase = true) ||
            card.brand.equals("brp", ignoreCase = true)
        val categoryMatch = card.equipmentType.equals(model.category, ignoreCase = true) ||
            card.equipmentType.equals("all", ignoreCase = true) ||
            card.equipmentType.contains(model.category, ignoreCase = true) ||
            model.category.contains(card.equipmentType, ignoreCase = true) ||
            (model.category.equals("ssv", ignoreCase = true) && card.equipmentType in listOf("sxs", "utv", "atv_utv")) ||
            (model.category.equals("atv", ignoreCase = true) && card.equipmentType == "atv_utv")
        val familyMatch = card.modelFamily.isNullOrBlank() ||
            card.modelFamily.equals("all", ignoreCase = true) ||
            card.modelFamily.equals(model.subcategory, ignoreCase = true) ||
            model.subcategory.contains(card.modelFamily, ignoreCase = true)
        val compatibleModelsOk = card.compatibleModels.isNullOrBlank() ||
            card.compatibleModels == "[]" ||
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

    private fun extractAccessorySearchWords(query: String): List<String> {
        val q = query.lowercase()
        val words = mutableListOf<String>()
        if (listOf("руж", "оружи", "винтовк", "карабин", "охот", "gun", "стрел").any { q.contains(it) }) {
            words.addAll(listOf("gun", "case", "boot", "holder", "руж"))
        }
        if (listOf("ед", "еда", "продукт", "холод", "напитк", "термос", "пищ", "куша", "пить", "вода").any { q.contains(it) }) {
            words.addAll(listOf("cooler", "холодильник", "bag", "сумка", "box", "кофр"))
        }
        if (listOf("кофр", "багаж", "ящик", "перевоз", "груз", "вещ", "класть", "сумк").any { q.contains(it) }) {
            words.addAll(listOf("box", "trunk", "bag", "cargo", "linq", "кофр", "ящик", "сумка"))
        }
        if (listOf("стекл", "ветр", "дефлект").any { q.contains(it) }) {
            words.addAll(listOf("windshield", "стекло", "deflector"))
        }
        if (listOf("защит", "днищ", "бампер", "грязь").any { q.contains(it) }) {
            words.addAll(listOf("skid", "plate", "bumper", "защита", "бампер"))
        }
        if (listOf("лебедк", "winch", "трос").any { q.contains(it) }) {
            words.addAll(listOf("winch", "лебедка"))
        }
        if (listOf("свет", "фар", "led", "люстр").any { q.contains(it) }) {
            words.addAll(listOf("light", "led", "свет", "фара"))
        }
        if (words.isEmpty()) {
            val stopWords = setOf("мне", "надо", "как", "могу", "что", "где", "когда", "какой", "для", "моей", "технике", "помоги")
            words.addAll(q.split("\\s+".toRegex()).filter { it.length > 3 && it !in stopWords })
        }
        return words.distinct()
    }

    /**
     * Извлекает ключевые слова для FTS-запроса.
     *
     * Улучшения по сравнению с предыдущей версией:
     *  1. Частичный стемминг кириллицы — "двигателя" → "двигател" —
     *     помогает FTS найти карточки с "двигатель" и "двигателем".
     *  2. Биграммы — добавляют в FTS-запрос сочетания соседних слов,
     *     чтобы найти фразеологические совпадения вроде "двигатель греет".
     *  3. Извлекаются коды ошибок (P0xxx/E0xxx) и акронимы BRP без фильтрации.
     */
    private fun extractKeywords(query: String): String {
        val stopWords = setOf(
            "не", "как", "что", "почему", "делать", "где", "когда", "какой",
            "подскажи", "посоветуй", "хочу", "подобрать", "выбрать", "купить",
            "рекомендуй", "нужно", "может", "какая", "какие", "бы"
        )

        val cleaned = query.lowercase()
            .replace(Regex("[^\u0430-\u044f\u0451a-z0-9\\s]"), " ")

        val words = cleaned.split("\\s+".toRegex())
            .filter { it.length > 1 && it !in stopWords }

        // Коды ошибок и акронимы BRP включаем без изменений
        val errorCodes = Regex("[pPeEuUcC][0-9]{4}").findAll(query)
            .map { it.value.lowercase() }.toList()
        val brpAcronyms = Regex("\\b(ibr|dps|cvt|ecu|efi|abc|4wd|linq|rotax|etec|ace)\\b")
            .findAll(query.lowercase()).map { it.value }.toList()

        // Стеммируем обычные слова для поиска форм ("\u0434вигателя" → "двигател")
        val stemmedWords = words.map { stemForFts(it) }.distinct()

        // Биграммы: сочетания соседних значимых слов (без стоп-слов)
        val significantWords = words.filter { it.length > 3 && it !in stopWords }
        val bigrams = significantWords.zipWithNext()
            .map { (a, b) -> "\"${stemForFts(a)} ${stemForFts(b)}\"" }
            .take(3)  // не больше 3 биграмм — FTS-запрос не разрастёт

        val allTerms = (stemmedWords + errorCodes + brpAcronyms + bigrams).distinct()
        return allTerms.joinToString(" OR ")
    }

    /**
     * Обрезает падежные окончания для более точного FTS-поиска.
     * Аналогично stemCyrillic() в RelevanceScorer, но облегчённый вариант
     * для FTS (больше суффиксов, меньше преобразований).
     */
    private fun stemForFts(word: String): String {
        val suffixes = listOf(
            "евались", "овались", "иваются", "ываются",
            "ающего", "ющего", "ающей", "ющей",
            "ования", "ивания",
            "ами", "ями", "ах", "ях",
            "ов", "ев", "ам", "ям",
            "ого", "его", "ие", "ые",
            "ий", "ый", "ой", "ей",
            "ость", "ости",
            "ем", "им", "ии", "ы",
            "ю", "я", "е", "и",
            "а", "ом"
        )
        val minLength = 4
        for (suffix in suffixes) {
            if (word.endsWith(suffix) && word.length - suffix.length >= minLength) {
                return word.dropLast(suffix.length)
            }
        }
        return word
    }
}
