package com.brp.assistant.data.rag

import com.brp.assistant.data.db.AccessoryDao
import com.brp.assistant.data.db.KnowledgeChunkDao
import com.brp.assistant.data.db.KnowledgeDao
import com.brp.assistant.data.db.ModelDao
import com.brp.assistant.data.db.UserDocumentDao
import com.brp.assistant.data.db.entities.Accessory
import com.brp.assistant.data.db.entities.BrpModel
import com.brp.assistant.data.db.entities.KnowledgeCard
import com.brp.assistant.data.db.entities.KnowledgeChunk
import com.brp.assistant.domain.model.RetrievalMode
import javax.inject.Inject
import javax.inject.Singleton

data class RetrievalResult(
    val cards: List<ScoredCard>,
    val accessories: List<ScoredAccessory>,
    val models: List<BrpModel>,
    val matchedChunks: List<KnowledgeChunk> = emptyList(),
    val userChunks: List<UserChunk> = emptyList()
)

data class ScoredCard(val card: KnowledgeCard, val score: Float)
data class ScoredAccessory(val accessory: Accessory, val score: Float)
data class UserChunk(val chunk: com.brp.assistant.data.db.entities.UserDocumentChunk, val docName: String)

@Singleton
class UnifiedRetriever @Inject constructor(
    private val knowledgeDao: KnowledgeDao,
    private val chunkDao: KnowledgeChunkDao,
    private val userDocDao: UserDocumentDao,
    private val accessoryDao: AccessoryDao,
    private val modelDao: ModelDao,
    private val scorer: RelevanceScorer,
    private val synonyms: SynonymDictionary
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
        var matchedChunksLocal: List<KnowledgeChunk> = emptyList()
        val cards = when (mode) {
            RetrievalMode.DIAGNOSIS, RetrievalMode.BOTH -> {

                // 0. FTS по ЧАНКАМ (более точный поиск, т.к. секции меньше по объёму)
                val chunkMatches = if (keywords.isNotBlank()) {
                    runCatching { chunkDao.searchFullText(keywords, topK * 3) }
                        .getOrDefault(emptyList())
                } else emptyList()
                matchedChunksLocal = chunkMatches

                // 1. FTS по карточкам целиком (fallback, если поиск по чанкам пуст)
                val ftsRaw = if (keywords.isNotBlank())
                    knowledgeDao.searchFullText(keywords, topK * 3)
                else emptyList()

                // Карточки, восстановленные из чанк-поиска
                val chunkCardIds = chunkMatches.map { it.cardId }.toSet()
                val fromChunks = if (chunkCardIds.isNotEmpty()) {
                    chunkCardIds.mapNotNull { cid ->
                        runCatching { knowledgeDao.getById(cid) }.getOrNull()
                    }
                } else emptyList()

                /**
                 * FIX #8: фоллбек на LIKE-поиск при пустых FTS-результатах.
                 */
                val ftsResults = if (ftsRaw.isEmpty() && fromChunks.isEmpty() && keywords.isNotBlank() && selectedModel != null) {
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

                // 3. Combine chunks → cards + FTS + scoped
                val combined = (fromChunks + ftsResults + scopedCards).distinctBy { it.id }

                val filtered = if (selectedModel != null) {
                    combined.filter { card -> isCardRelevantToModel(card, selectedModel) }
                } else {
                    (fromChunks + ftsResults).distinctBy { it.id }
                }

                filtered
                    .map { ScoredCard(it, scorer.scoreKnowledgeCard(query, it, selectedModel)) }
                    .sortedByDescending { it.score }
                    .filter { it.score > 1.5f }   // чанки дают более точные попадания, порог можно снизить
                    .take(topK)
            }
            RetrievalMode.ACCESSORY -> emptyList()
        }

        // ── Пользовательские документы (моя база знаний) ────────────────────────────────
        val userChunks = if (keywords.isNotBlank() && mode != RetrievalMode.ACCESSORY) {
            val raw = runCatching { userDocDao.searchChunks(keywords, topK * 2) }
                .getOrDefault(emptyList())
            raw.map { chunk ->
                val doc = runCatching { userDocDao.getById(chunk.documentId)?.displayName }
                    .getOrNull() ?: "Документ"
                UserChunk(chunk, doc)
            }.take(topK)
        } else emptyList()

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

        return RetrievalResult(cards, accessories, modelResults, matchedChunksLocal, userChunks)
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
        if (listOf("ед", "еда", "продукт", "холод", "напитк", "термос", "пищ", "куша", "пить", "вода", "пикник").any { q.contains(it) }) {
            words.addAll(listOf("cooler", "холодильник", "bag", "сумка", "box", "кофр"))
        }
        if (listOf("кофр", "багаж", "ящик", "перевоз", "груз", "вещ", "класть", "сумк", "багажник").any { q.contains(it) }) {
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
        if (listOf("свет", "фар", "led", "люстр", "фара").any { q.contains(it) }) {
            words.addAll(listOf("light", "led", "свет", "фара"))
        }
        if (listOf("сидень", "седло", "кресл", "пассажир").any { q.contains(it) }) {
            words.addAll(listOf("seat", "сиденье", "пассажир"))
        }
        if (listOf("музык", "звук", "аудио", "динамик", "колонк").any { q.contains(it) }) {
            words.addAll(listOf("audio", "soundbar", "bluetooth", "динамик", "аудио"))
        }
        if (listOf("зеркал").any { q.contains(it) }) {
            words.addAll(listOf("mirror", "зеркало"))
        }
        if (listOf("подогрев", "греет руки", "холодно").any { q.contains(it) }) {
            words.addAll(listOf("heated", "grips", "подогрев", "ручки"))
        }
        if (listOf("двер", "кабин").any { q.contains(it) }) {
            words.addAll(listOf("doors", "cab", "двери", "кабина"))
        }
        if (listOf("крыш", "козырёк", "козырек", "тент").any { q.contains(it) }) {
            words.addAll(listOf("roof", "крыша"))
        }
        if (words.isEmpty()) {
            val stopWords = setOf("мне", "надо", "как", "могу", "что", "где", "когда", "какой", "для", "моей", "технике", "помоги",
                "хочу", "нужно", "подскажите", "посоветуй", "рекомендуй")
            words.addAll(q.split("\\s+".toRegex()).filter { it.length > 3 && it !in stopWords })
        }
        // Расширяем через словарь синонимов
        return synonyms.expand(words).distinct()
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

        // Стеммируем обычные слова для поиска форм ("двигателя" → "двигател")
        val stemmedWords = words.map { stemForFts(it) }.distinct()

        // Расширяем термины синонимами/сленгом (квадрик → atv, глохнет → stall и т.д.)
        val expandedTerms = synonyms.expand(stemmedWords + words)
            .map { stemForFts(it) }
            .filter { it.length > 1 }

        // Биграммы: сочетания соседних значимых слов (без стоп-слов)
        val significantWords = words.filter { it.length > 3 && it !in stopWords }
        val bigrams = significantWords.zipWithNext()
            .map { (a, b) -> "\"${stemForFts(a)} ${stemForFts(b)}\"" }
            .take(3)  // не больше 3 биграмм — FTS-запрос не разрастёт

        val allTerms = (stemmedWords + expandedTerms + errorCodes + brpAcronyms + bigrams).distinct()
        // FTS не любит термины, содержащие дефис/не-ASCII после стемминга — экранируем
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
