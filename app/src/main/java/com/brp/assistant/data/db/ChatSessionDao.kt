package com.brp.assistant.data.db

import androidx.room.*
import com.brp.assistant.data.db.entities.ChatMessageEntity
import com.brp.assistant.data.db.entities.ChatSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatSessionDao {

    // ── Sessions ─────────────────────────────────────────────────────────────

    /**
     * Все сессии, отсортированные по дате последнего обновления (новые сверху).
     * Flow — LiveData-подобный поток: UI обновится автоматически при изменениях.
     */
    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC")
    fun observeAllSessions(): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_sessions WHERE id = :id")
    suspend fun getSession(id: String): ChatSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSessionEntity)

    @Query("UPDATE chat_sessions SET updatedAt = :updatedAt, title = :title WHERE id = :id")
    suspend fun updateSession(id: String, title: String, updatedAt: Long)

    @Query("DELETE FROM chat_sessions WHERE id = :id")
    suspend fun deleteSession(id: String)

    @Query("DELETE FROM chat_sessions")
    suspend fun deleteAll()

    // ── Messages ─────────────────────────────────────────────────────────────

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessages(sessionId: String): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ChatMessageEntity>)

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteMessages(sessionId: String)
}
