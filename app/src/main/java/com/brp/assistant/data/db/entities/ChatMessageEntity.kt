package com.brp.assistant.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Одно сообщение внутри сессии.
 * CASCADE DELETE: при удалении сессии все её сообщения удаляются автоматически.
 */
@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class ChatMessageEntity(
    @PrimaryKey val id: String,          // UUID
    val sessionId: String,
    val role: String,                    // "user" | "assistant"
    val content: String,
    val timestamp: Long                  // epoch millis
)
