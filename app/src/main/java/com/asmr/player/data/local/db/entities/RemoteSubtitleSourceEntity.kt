package com.asmr.player.data.local.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "remote_subtitle_sources",
    indices = [Index(value = ["trackId"])]
)
data class RemoteSubtitleSourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val trackId: Long,
    val url: String,
    val language: String,
    val ext: String
)

