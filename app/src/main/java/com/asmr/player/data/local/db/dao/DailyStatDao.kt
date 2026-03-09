package com.asmr.player.data.local.db.dao

import androidx.room.*
import com.asmr.player.data.local.db.entities.DailyStatEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyStatDao {
    @Query("SELECT * FROM daily_stats WHERE date = :date")
    fun observeDailyStat(date: String): Flow<DailyStatEntity?>

    @Query("SELECT * FROM daily_stats WHERE date = :date")
    suspend fun getDailyStat(date: String): DailyStatEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stat: DailyStatEntity)

    @Transaction
    suspend fun addDuration(date: String, ms: Long) {
        val current = getDailyStat(date) ?: DailyStatEntity(date)
        upsert(current.copy(listeningDurationMs = current.listeningDurationMs + ms))
    }

    @Transaction
    suspend fun incrementTrackCount(date: String) {
        val current = getDailyStat(date) ?: DailyStatEntity(date)
        upsert(current.copy(trackCount = current.trackCount + 1))
    }

    @Transaction
    suspend fun addTraffic(date: String, bytes: Long) {
        val current = getDailyStat(date) ?: DailyStatEntity(date)
        upsert(current.copy(networkTrafficBytes = current.networkTrafficBytes + bytes))
    }
}
