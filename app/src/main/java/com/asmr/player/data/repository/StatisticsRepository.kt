package com.asmr.player.data.repository

import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.entities.DailyStatEntity
import com.asmr.player.util.ListeningDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatisticsRepository @Inject constructor(
    private val db: AppDatabase
) {
    // "今天"以凌晨 5 点为重置点（[ListeningDay]），凌晨的收听仍归属前一天。
    private fun getTodayDate(): String = ListeningDay.currentDate()

    fun observeTodayStats(): Flow<DailyStatEntity?> {
        val today = getTodayDate()
        return db.dailyStatDao().observeDailyStat(today)
    }

    /** 区间内每日统计（含端点），用于收听热度图。 */
    fun observeStatsBetween(startDate: String, endDate: String): Flow<List<DailyStatEntity>> =
        db.dailyStatDao().observeStatsBetween(startDate, endDate)

    /** 全部历史每日统计，用于累计汇总。 */
    fun observeAllStats(): Flow<List<DailyStatEntity>> = db.dailyStatDao().observeAllStats()

    suspend fun addListeningDuration(ms: Long) = withContext(Dispatchers.IO) {
        if (ms <= 0) return@withContext
        val today = getTodayDate()
        db.dailyStatDao().addDuration(today, ms)
    }

    suspend fun incrementTrackCount() = withContext(Dispatchers.IO) {
        val today = getTodayDate()
        db.dailyStatDao().incrementTrackCount(today)
    }

    suspend fun addNetworkTraffic(bytes: Long) = withContext(Dispatchers.IO) {
        if (bytes <= 0) return@withContext
        val today = getTodayDate()
        db.dailyStatDao().addTraffic(today, bytes)
    }
}
