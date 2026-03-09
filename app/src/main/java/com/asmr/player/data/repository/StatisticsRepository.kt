package com.asmr.player.data.repository

import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.entities.DailyStatEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatisticsRepository @Inject constructor(
    private val db: AppDatabase
) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private fun getTodayDate(): String {
        return dateFormat.format(Date())
    }

    fun observeTodayStats(): Flow<DailyStatEntity?> {
        val today = getTodayDate()
        return db.dailyStatDao().observeDailyStat(today)
    }

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
