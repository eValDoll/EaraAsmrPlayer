package com.asmr.player.data.local.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "download_items",
    foreignKeys = [
        ForeignKey(
            entity = DownloadTaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["taskId"]),
        Index(value = ["workId"], unique = true)
    ]
)
data class DownloadItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val taskId: Long,
    val workId: String,
    val url: String,
    val relativePath: String,
    val fileName: String,
    val targetDir: String,
    val filePath: String,
    val state: String,
    val downloaded: Long,
    val total: Long,
    val speed: Long,
    val createdAt: Long,
    val updatedAt: Long
)

