package com.asmr.player.data.local.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_stats")
data class DailyStatEntity(
    @PrimaryKey val date: String, // "yyyy-MM-dd"
    val listeningDurationMs: Long = 0,
    val trackCount: Int = 0,
    val networkTrafficBytes: Long = 0
)
