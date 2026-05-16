package com.asmr.player.data.local.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val path: String,
    val localPath: String? = null,
    val downloadPath: String? = null,
    val circle: String = "",
    val cv: String = "",
    val tags: String = "",
    val coverUrl: String = "",
    val coverPath: String = "",
    val coverThumbPath: String = "",
    val workId: String = "",
    val rjCode: String = "",
    val description: String = "",
    val audioTrackCount: Int = 0,
    val audioTotalDuration: Double = 0.0,
    val audioTotalSizeBytes: Long = 0L
)
