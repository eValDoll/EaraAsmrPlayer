package com.asmr.player.data.local.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "local_tree_cache",
    indices = [
        Index(value = ["albumId", "cacheKey"], unique = true)
    ]
)
data class LocalTreeCacheEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val albumId: Long,
    val cacheKey: String,
    val stamp: Long,
    val payloadJson: String,
    val updatedAt: Long
)

