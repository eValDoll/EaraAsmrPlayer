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

    /** 区间内（含端点）的每日统计，按日期升序。用于收听热度图。 */
    @Query("SELECT * FROM daily_stats WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    fun observeStatsBetween(startDate: String, endDate: String): Flow<List<DailyStatEntity>>

    /** 全部历史每日统计（升序）。用于累计汇总与年度报告数据基础。 */
    @Query("SELECT * FROM daily_stats ORDER BY date ASC")
    fun observeAllStats(): Flow<List<DailyStatEntity>>

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
