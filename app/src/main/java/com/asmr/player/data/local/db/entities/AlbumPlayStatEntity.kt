package com.asmr.player.data.local.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "album_play_stats")
data class AlbumPlayStatEntity(
    @PrimaryKey val albumId: Long,
    val lastPlayedAt: Long,
    val playCount: Long
)

