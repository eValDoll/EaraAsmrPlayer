package com.asmr.player.data.local.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "subtitles",
    indices = [Index(value = ["trackId"])]
)
data class SubtitleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val trackId: Long,
    val startMs: Long,
    val endMs: Long,
    val text: String
)

