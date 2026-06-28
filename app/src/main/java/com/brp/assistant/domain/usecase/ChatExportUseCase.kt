package com.brp.assistant.domain.usecase

import android.content.Context
import android.util.Log
import com.brp.assistant.data.db.ChatSessionDao
import com.brp.assistant.data.db.entities.ChatMessageEntity
import com.brp.assistant.data.db.entities.ChatSessionEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Экспорт сессии чата в Markdown или JSON.
 *
 * Используется в ChatViewModel.exportSession().
 * Файл сохраняется в временную папку приложения
 * и передается через ShareCompat/ShareSheet.
 */
class ChatExportUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chatSessionDao: ChatSessionDao
) {
    companion object {
        private const val TAG = "ChatExportUseCase"
    }

    enum class ExportFormat { MARKDOWN, JSON }

    /**
     * Экспортирует сессию [sessionId] в файл формата [format].
     * @return [Result.success] с [File] готовым к передаче, или [Result.failure].
     */
    suspend fun exportSession(
        sessionId: String,
        format: ExportFormat = ExportFormat.MARKDOWN
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val session = chatSessionDao.getSession(sessionId)
                ?: return@withContext Result.failure(Exception("Сессия не найдена"))
            val messages = chatSessionDao.getMessages(sessionId)

            val fileName = buildFileName(session, format)
            val outputDir = File(context.cacheDir, "exports").also { it.mkdirs() }
            val outputFile = File(outputDir, fileName)

            val content = when (format) {
                ExportFormat.MARKDOWN -> buildMarkdown(session, messages)
                ExportFormat.JSON     -> buildJson(session, messages)
            }

            outputFile.writeText(content, Charsets.UTF_8)
            Log.i(TAG, "Exported session '$sessionId' to ${outputFile.absolutePath} (${outputFile.length()} bytes)")
            Result.success(outputFile)
        } catch (e: Exception) {
            Log.e(TAG, "Export failed for session '$sessionId'", e)
            Result.failure(e)
        }
    }

    private fun buildFileName(session: ChatSessionEntity, format: ExportFormat): String {
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(session.createdAt))
        val safeTitlePart = session.title.take(30)
            .replace(Regex("[^\\w\\u0430-\\u044f\\u0410-\\u042f\\u0020]"), "_")
            .replace(" ", "_")
        val ext = if (format == ExportFormat.MARKDOWN) "md" else "json"
        return "BRP_Chat_${dateStr}_${safeTitlePart}.${ext}"
    }

    private fun buildMarkdown(session: ChatSessionEntity, messages: List<ChatMessageEntity>): String {
        val sb = StringBuilder()
        sb.appendLine("# ${session.title}")
        sb.appendLine()
        session.vehicleName?.let { sb.appendLine("Техника: **${it}**") }
        val dateStr = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(session.createdAt))
        sb.appendLine("Дата: $dateStr")
        sb.appendLine("Режим: ${session.mode}")
        sb.appendLine()
        sb.appendLine("---")
        sb.appendLine()
        messages.forEach { msg ->
            val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp))
            val roleLabel = if (msg.role == "user") "👤 Пользователь" else "🤖 BRP Assistant"
            sb.appendLine("### $roleLabel [$timeStr]")
            sb.appendLine()
            sb.appendLine(msg.content)
            sb.appendLine()
        }
        return sb.toString()
    }

    private fun buildJson(session: ChatSessionEntity, messages: List<ChatMessageEntity>): String {
        val json = JSONObject().apply {
            put("session_id", session.id)
            put("title", session.title)
            put("vehicle_name", session.vehicleName)
            put("mode", session.mode)
            put("created_at", session.createdAt)
            put("updated_at", session.updatedAt)
            put("messages", JSONArray().apply {
                messages.forEach { msg ->
                    put(JSONObject().apply {
                        put("id", msg.id)
                        put("role", msg.role)
                        put("content", msg.content)
                        put("timestamp", msg.timestamp)
                    })
                }
            })
        }
        return json.toString(2)
    }
}
