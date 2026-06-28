package com.brp.assistant.data.db

import androidx.room.*
import com.brp.assistant.data.db.entities.ChatMessageEntity
import com.brp.assistant.data.db.entities.ChatSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatSessionDao {

    // ── Сессии ──────────────────────────────────────────────────────────────────────────────────

    /** Реактивный поток всех сессий — UI обновляется автоматически при любом изменении. */
    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC")
    fun observeAllSessions(): Flow<List<ChatSessionEntity>>

    /** Фильтрация сессий по имени вехикла (#18). */
    @Query("SELECT * FROM chat_sessions WHERE (:vehicleName IS NULL OR vehicleName LIKE '%' || :vehicleName || '%') ORDER BY updatedAt DESC")
    fun observeSessionsByVehicle(vehicleName: String?): Flow<List<ChatSessionEntity>>

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

    /** Очистка сессий старше [olderThanMs] (#16). */
    @Query("DELETE FROM chat_sessions WHERE updatedAt < :olderThanMs")
    suspend fun deleteSessionsOlderThan(olderThanMs: Long)

    // ── Сообщения ───────────────────────────────────────────────────────────────────────────────────

    /** Все сообщения сессии в хронологическом порядке. */
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessages(sessionId: String): List<ChatMessageEntity>

    /**
     * Пагинация сообщений (#6).
     * Возвращает сообщения за один запрос с [limit] + [offset].
     * При больших сессиях (1000+ сообщений) предотвращает OOM.
     */
    @Query(
        "SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC LIMIT :limit OFFSET :offset"
    )
    suspend fun getMessagesPaged(sessionId: String, limit: Int, offset: Int): List<ChatMessageEntity>

    /**
     * Поиск по содержимому сообщений в сессии (#6).
     * Использует LIKE для простого case-insensitive поиска.
     */
    @Query(
        "SELECT * FROM chat_messages WHERE sessionId = :sessionId AND content LIKE '%' || :query || '%' ORDER BY timestamp ASC LIMIT :limit"
    )
    suspend fun searchMessages(sessionId: String, query: String, limit: Int = 50): List<ChatMessageEntity>

    /** Количество сообщений в сессии (для расчёта тотальных страниц). */
    @Query("SELECT COUNT(*) FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun getMessageCount(sessionId: String): Int

    /**
     * Bulk-insert с REPLACE — идемпотенен при повторных вызовах.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ChatMessageEntity>)

    /**
     * FIX #3: дельта-сохранение одного сообщения с IGNORE.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteMessages(sessionId: String)

    /** Удаление старых сообщений (#12): оставляет только последние [keepCount] в сессии. */
    @Query(
        "DELETE FROM chat_messages WHERE sessionId = :sessionId AND id NOT IN (" +
        "SELECT id FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp DESC LIMIT :keepCount)"
    )
    suspend fun trimMessages(sessionId: String, keepCount: Int)
}
