package com.brp.assistant.data.rag

import com.brp.assistant.data.db.KnowledgeChunkDao
import com.brp.assistant.data.db.KnowledgeDao
import com.brp.assistant.data.db.entities.KnowledgeCard
import com.brp.assistant.data.db.entities.KnowledgeChunk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Заполняет таблицу knowledge_chunks из knowledge_cards.fullText при первом
 * запуске после установки/миграции.
 *
 * Чанки производны от карточек и не содержат пользовательских данных, поэтому
 * их можно безопасно пересоздавать. Разбиение идёт по H2/H3-секциям markdown.
 */
@Singleton
class KnowledgeChunkInitializer @Inject constructor(
    private val knowledgeDao: KnowledgeDao,
    private val chunkDao: KnowledgeChunkDao
) {

    suspend fun ensureChunksPopulated() {
        withContext(Dispatchers.IO) {
            val cardCount = knowledgeDao.getAll().size
            val chunkCount = chunkDao.count()
            // Если чанков меньше половины карточек — пересобираем
            if (chunkCount >= cardCount) return@withContext
            rebuildChunks()
        }
    }

    suspend fun rebuildChunks() = withContext(Dispatchers.IO) {
        val cards = knowledgeDao.getAll()
        chunkDao.deleteAll()
        val chunks = mutableListOf<KnowledgeChunk>()
        var counter = 0
        for (card in cards) {
            // Обзорный чанок
            val preview = card.fullText.lineSequence()
                .filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
                .take(3).joinToString("\n").take(500)
            chunks += KnowledgeChunk(
                id = "${card.id}__overview",
                cardId = card.id,
                brand = card.brand,
                equipmentType = card.equipmentType,
                modelFamily = card.modelFamily,
                node = card.node,
                section = "Обзор",
                content = "${card.symptom}\n$preview".trim()
            )
            counter++

            // Чанки по секциям
            for ((title, content) in splitIntoSections(card.fullText, card.symptom)) {
                if (content.length < 40) continue
                chunks += KnowledgeChunk(
                    id = "${card.id}__sec$counter",
                    cardId = card.id,
                    brand = card.brand,
                    equipmentType = card.equipmentType,
                    modelFamily = card.modelFamily,
                    node = card.node,
                    section = title.take(120),
                    content = content.take(1500)
                )
                counter++
            }
        }
        if (chunks.isNotEmpty()) {
            chunkDao.insertAll(chunks)
        }
    }

    private fun splitIntoSections(text: String, symptom: String): List<Pair<String, String>> {
        if (text.isBlank()) return emptyList()
        val sections = mutableListOf<Pair<String, String>>()
        var currentTitle = symptom.substringAfter("# ").trim().ifBlank { "Обзор" }
        val currentLines = mutableListOf<String>()

        for (line in text.lines()) {
            val t = line.trim()
            if (t.startsWith("##")) {
                if (currentLines.isNotEmpty()) {
                    sections += currentTitle to currentLines.joinToString("\n").trim()
                }
                currentTitle = t.trimStart('#').trim()
                currentLines.clear()
            } else {
                if (t.startsWith("# ")) continue
                if (t == "---") continue
                currentLines += line
            }
        }
        if (currentLines.isNotEmpty()) {
            sections += currentTitle to currentLines.joinToString("\n").trim()
        }
        return sections.filter { it.second.isNotBlank() }.ifEmpty {
            listOf((symptom to text).let { it.first.trim().ifBlank { "Обзор" } to it.second })
        }
    }
}
