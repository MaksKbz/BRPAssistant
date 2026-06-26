package com.brp.assistant.data.db

import androidx.room.*
import com.brp.assistant.data.db.entities.ChatMessageEntity
import com.brp.assistant.data.db.entities.ChatSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatSessionDao {

    // ── Сессии ───────────────────────────────────────────────────────────────────────────────

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

    // ── Сообщения ───────────────────────────────────────────────────────────────────────

    /** Все сообщения сессии в хронологическом порядке. */
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessages(sessionId: String): List<ChatMessageEntity>

    /**
     * Bulk-insert с REPLACE — идемпотенен при повторных вызовах.
     * Оставлен для обратной совместимости и полной перезаписи в легаци-сценариях.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ChatMessageEntity>)

    /**
     * FIX #3: дельта-сохранение одного сообщения с IGNORE.
     *
     * Используется в ChatViewModel.persistMessages() для сохранения двух новых
     * сообщений (вопрос + ответ) вместо перезаписи всей сессии.
     *
     * IGNORE: если сообщение уже есть в БД (по id) — пропускаем. Такое поведение
     * правильно для сообщений с единственным UUID: повторная запись — это
     * одно и то же сообщение, а не обновлённое.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteMessages(sessionId: String)
}
