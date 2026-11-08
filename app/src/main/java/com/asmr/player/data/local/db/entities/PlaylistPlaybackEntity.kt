package com.asmr.player.data.local.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playlist_playback",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistItemEntity::class,
            parentColumns = ["playlistId", "mediaId"],
            childColumns = ["playlistId", "mediaId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["playlistId", "mediaId"])]
)
data class PlaylistPlaybackEntity(
    @PrimaryKey val playlistId: Long,
    val mediaId: String,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isCompleted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
