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
    val logicalTaskKey: String = taskKey,
    val title: String,
    val subtitle: String = "",
    val rootDir: String,
    val destinationRoot: String = "",
    val albumRootDir: String = rootDir,
    val albumTitle: String = "",
    val albumCircle: String = "",
    val albumCv: String = "",
    val albumTagsCsv: String = "",
    val albumCoverUrl: String = "",
    val albumDescription: String = "",
    val albumWorkId: String = "",
    val albumRjCode: String = "",
    val createdAt: Long,
    val updatedAt: Long
)
