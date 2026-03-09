package com.asmr.player.data.local.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "track_playback_progress",
    indices = [
        Index(value = ["albumId"]),
        Index(value = ["updatedAt"])
    ]
)
data class TrackPlaybackProgressEntity(
    @PrimaryKey
    val mediaId: String,
    val albumId: Long? = null,
    val trackId: Long? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val completed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

