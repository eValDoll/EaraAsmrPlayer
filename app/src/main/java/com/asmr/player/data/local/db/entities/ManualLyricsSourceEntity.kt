package com.asmr.player.data.local.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "manual_lyrics_sources",
    indices = [
        Index(value = ["exactTargetKey"], unique = true),
        Index(value = ["fallbackTargetKey"]),
        Index(value = ["canonicalMediaId"])
    ]
)
data class ManualLyricsSourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val exactTargetKey: String,
    val fallbackTargetKey: String,
    val canonicalMediaId: String,
    val sourceUri: String,
    val displayName: String,
    val createdAt: Long,
    val updatedAt: Long
)

