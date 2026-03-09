package com.asmr.player.data.local.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "track_slices",
    indices = [
        Index(value = ["trackMediaId"])
    ]
)
data class TrackSliceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val trackMediaId: String,
    val startMs: Long,
    val endMs: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
