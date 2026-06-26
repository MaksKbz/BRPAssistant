package com.brp.assistant.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Сессия чата — один диалог (может содержать много сообщений).
 * title — первые ~60 символов первого user-сообщения.
 * vehicleId / mode хранятся, чтобы при восстановлении сессии можно было
 * передать правильный контекст обратно в LLM-движок.
 */
@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey val id: String,          // UUID
    val title: String,
    val vehicleId: String?,
    val mode: String,
    val createdAt: Long,                 // epoch millis
    val updatedAt: Long
)
