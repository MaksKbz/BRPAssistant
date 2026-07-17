package com.brp.assistant.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.brp.assistant.data.db.UserDocumentDao
import com.brp.assistant.data.db.entities.UserDocument
import com.brp.assistant.data.db.entities.UserDocumentChunk
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Управление пользовательской базой знаний: добавление .md/.txt
 * файлов через SAF (Storage Access Framework), разбиение на чанки
 * и удаление. Чанки участвуют в RAG-поиске наравне со встроенными
 * карточками BRP.
 */
@Singleton
class UserDocumentsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: UserDocumentDao
) {

    suspend fun listDocuments(): List<UserDocument> = dao.getAll()

    suspend fun count(): Int = dao.count()

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        dao.deleteChunksByDoc(id)
        dao.deleteById(id)
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        // Чанки удаляются каскадно через FK ON DELETE CASCADE
        dao.deleteAll()
    }

    suspend fun addFromUri(uri: Uri, displayName: String? = null): Result<UserDocument> =
        withContext(Dispatchers.IO) {
            runCatching {
                val content = readUriAsText(uri)
                    ?: error("Не удалось прочитать файл")
                val fileName = displayName ?: uri.lastPathSegment ?: "document.md"
                val mimeType = context.contentResolver.getType(uri) ?: "text/plain"

                val doc = UserDocument(
                    id = "usr_${UUID.randomUUID()}",
                    displayName = fileName.substringBeforeLast('.').take(80),
                    fileName = fileName,
                    mimeType = mimeType,
                    sizeBytes = content.toByteArray(Charsets.UTF_8).size.toLong(),
                    addedAt = System.currentTimeMillis()
                )

                val chunks = chunkText(content, doc.id, fileName)
                val docWithChunkCount = doc.copy(chunkCount = chunks.size)
                dao.insert(docWithChunkCount)
                if (chunks.isNotEmpty()) {
                    dao.insertChunks(chunks)
                }

                Log.i(TAG, "Added user doc '${doc.displayName}': ${chunks.size} chunks")
                docWithChunkCount
            }
        }

    suspend fun addRawText(text: String, displayName: String): Result<UserDocument> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (text.isBlank()) error("Пустой документ")
                val doc = UserDocument(
                    id = "usr_${UUID.randomUUID()}",
                    displayName = displayName.take(80),
                    fileName = "$displayName.txt",
                    mimeType = "text/plain",
                    sizeBytes = text.toByteArray(Charsets.UTF_8).size.toLong(),
                    addedAt = System.currentTimeMillis()
                )
                val chunks = chunkText(text, doc.id, displayName)
                val docWithChunkCount = doc.copy(chunkCount = chunks.size)
                dao.insert(docWithChunkCount)
                if (chunks.isNotEmpty()) dao.insertChunks(chunks)
                docWithChunkCount
            }
        }

    suspend fun searchChunks(query: String, limit: Int = 6): List<UserDocumentChunk> =
        runCatching { dao.searchChunks(query, limit) }
            .getOrDefault(emptyList())

    private fun readUriAsText(uri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
    }.getOrNull()

    /**
     * Разбивает текст на чанки по абзацам / H2-заголовкам / пустым строкам,
     * стараясь выдерживать размер ~600 символов на чанк.
     */
    private fun chunkText(text: String, docId: String, docName: String): List<UserDocumentChunk> {
        val cleaned = text
            .replace(Regex("\\r\\n?"), "\n")
            .replace(Regex("\\n{3,}"), "\n\n")

        val sections = mutableListOf<Pair<String, StringBuilder>>()
        var currentTitle = "Введение"
        var currentBody = StringBuilder()
        var chunkCounter = 0

        fun flush() {
            val body = currentBody.toString().trim()
            if (body.isNotBlank()) {
                sections += currentTitle to StringBuilder(body)
            }
            currentBody = StringBuilder()
        }

        for (line in cleaned.lines()) {
            val stripped = line.trim()
            if (stripped.startsWith("##")) {
                flush()
                currentTitle = stripped.trimStart('#').trim().take(120)
            } else if (stripped.startsWith("# ")) {
                // H1 — название документа
                flush()
                currentTitle = stripped.trimStart('#').trim().ifBlank { docName }.take(120)
            } else if (stripped.isBlank() && currentBody.length > 500) {
                // Абзацный разрыв при достаточном размере
                flush()
            } else {
                if (currentBody.length + line.length > MAX_CHUNK_CHARS) {
                    flush()
                }
                currentBody.append(line).append('\n')
            }
        }
        flush()

        return sections.map { (title, body) ->
            chunkCounter++
            UserDocumentChunk(
                id = "${docId}_$chunkCounter",
                documentId = docId,
                section = title,
                content = body.toString().trim().take(1500)
            )
        }
    }

    companion object {
        private const val TAG = "UserDocRepo"
        private const val MAX_CHUNK_CHARS = 800
    }
}
