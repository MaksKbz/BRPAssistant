package com.brp.assistant.data.db

import androidx.room.*
import com.brp.assistant.data.db.entities.ChatMessageEntity
import com.brp.assistant.data.db.entities.ChatSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatSessionDao {

    // ── Сессии ────────────────────────────────────────────────────────────────────────────────────────

    /** Реактивный поток всех сессий — UI обновляется автоматически при любом изменении. */
    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC")
    fun observeAllSessions(): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_sessions WHERE id = :sessionId")
    suspend fun getSession(sessionId: String): ChatSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSessionEntity)

    /**
     * Обновляет title, vehicleName и updatedAt сессии.
     * vehicleName может быть null если пользователь не выбрал технику.
     */
    @Query(
        "UPDATE chat_sessions SET title = :title, vehicleName = :vehicleName, updatedAt = :updatedAt WHERE id = :id"
    )
    suspend fun updateSession(id: String, title: String, vehicleName: String?, updatedAt: Long)

    @Query("DELETE FROM chat_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("DELETE FROM chat_sessions")
    suspend fun deleteAllSessions()

    /**
     * CleanupWorker: удаляет сессии, не обновлявшиеся с определённого момента.
     * Используется для еженедельной очистки: удаляет сессии старше 90 дней.
     * ON DELETE CASCADE в chat_messages автоматически удаляет сообщения.
     * @param cutoffMs Timestamp в миллисекундах: удаляются сессии с updatedAt < cutoffMs.
     */
    @Query("DELETE FROM chat_sessions WHERE updatedAt < :cutoffMs")
    suspend fun deleteSessionsOlderThan(cutoffMs: Long)

    // ── #6 Поиск и пагинация ──────────────────────────────────────────────────

    /**
     * #6 — Полнотекстовый поиск по title и vehicleName сессий.
     *
     * LIKE '%query%' достаточно для текущего объёма данных (сотни сессий).
     */
    @Query(
        "SELECT * FROM chat_sessions " +
        "WHERE title LIKE '%' || :query || '%' " +
        "   OR vehicleName LIKE '%' || :query || '%' " +
        "ORDER BY updatedAt DESC"
    )
    suspend fun searchSessions(query: String): List<ChatSessionEntity>

    /**
     * #6 — Курсорная пагинация сессий по updatedAt DESC.
     *
     * @param limit  Размер страницы (20–50).
     * @param offset Смещение (страница × limit).
     */
    @Query(
        "SELECT * FROM chat_sessions " +
        "ORDER BY updatedAt DESC " +
        "LIMIT :limit OFFSET :offset"
    )
    suspend fun getSessionsPaged(limit: Int, offset: Int): List<ChatSessionEntity>

    // ── Сообщения ─────────────────────────────────────────────────────────────────────

    /** Все сообщения сессии в хронологическом порядке. */
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessages(sessionId: String): List<ChatMessageEntity>

    /**
     * Bulk-insert с REPLACE — идемпотенен при повторных вызовах.
     * Оставлен для обратной совместимости.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ChatMessageEntity>)

    /**
     * FIX #3: дельта-сохранение одного сообщения с IGNORE.
     *
     * Используется в ChatViewModel.persistMessages() для сохранения двух новых
     * сообщений (вопрос + ответ). IGNORE: если id уже есть — пропускаем.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteMessages(sessionId: String)
}
