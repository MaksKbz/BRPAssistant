package com.brp.assistant.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Сессия чата — один диалог (может содержать много сообщений).
 *
 * [title]       — первые ~60 символов первого user-сообщения.
 * [vehicleId]   — UUID транспортного средства (FK к brp_models.id, не строгий).
 * [vehicleName] — денормализованное отображаемое имя («Can-Am Maverick X3 RS»)
 *                  чтобы не делать JOIN при отрисовке панели истории.
 * [mode]        — «chat» | «diagnosis» | «accessory».
 */
@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey val id: String,          // UUID
    val title: String,
    val vehicleId: String?,
    val vehicleName: String?,            // denormalized display name
    val mode: String,
    val createdAt: Long,                 // epoch millis
    val updatedAt: Long
)
