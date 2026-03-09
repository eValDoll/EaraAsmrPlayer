package com.asmr.player.data.local.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "download_tasks",
    indices = [
        Index(value = ["taskKey"], unique = true)
    ]
)
data class DownloadTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val taskKey: String,
    val title: String,
    val subtitle: String = "",
    val rootDir: String,
    val createdAt: Long,
    val updatedAt: Long
)
