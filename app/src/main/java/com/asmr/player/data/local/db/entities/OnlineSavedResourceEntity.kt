package com.asmr.player.data.local.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "online_saved_resources",
    indices = [Index(value = ["albumId"])]
)
data class OnlineSavedResourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val albumId: Long,
    val relativePath: String,
    val url: String,
    val fileType: String
)
