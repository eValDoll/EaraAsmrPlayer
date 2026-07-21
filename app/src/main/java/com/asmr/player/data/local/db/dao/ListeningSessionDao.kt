package com.asmr.player.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.asmr.player.data.local.db.entities.ListeningSessionEntity
import kotlinx.coroutines.flow.Flow

/** [ListeningSessionDao.tagDurationTotals] 的投影：某 tags 快照对应的累计时长。 */
data class TagDurationRow(
    val tags: String,
    val durationMs: Long
)

/** 每小时时段的累计时长投影，用于时段偏好分析。 */
data class HourDurationRow(
    val hour: Int,
    val durationMs: Long
)

/** 单个作品的累计收听投影，用于"最常收听作品"排行。 */
data class AlbumListeningRow(
    val albumId: Long,
    val rjCode: String,
    val title: String,
    val circle: String,
    val cv: String,
    val coverUrl: String,
    val coverPath: String,
    val coverThumbPath: String,
    val durationMs: Long,
    val sessionCount: Int
)

@Dao
interface ListeningSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: ListeningSessionEntity): Long

    @Update
    suspend fun update(session: ListeningSessionEntity)

    @Query("SELECT * FROM listening_sessions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ListeningSessionEntity?

    /** 某收听日的全部会话，按开始时间降序（用于顶部最新、底部最旧的垂直时间线）。 */
    @Query("SELECT * FROM listening_sessions WHERE listeningDate = :date ORDER BY startAtMs DESC")
    fun observeSessionsForDate(date: String): Flow<List<ListeningSessionEntity>>

    @Query("SELECT * FROM listening_sessions WHERE listeningDate = :date ORDER BY startAtMs DESC")
    suspend fun getSessionsForDate(date: String): List<ListeningSessionEntity>

    // ---- 年度报告数据基础：聚合查询 ----

    /** 区间内不同作品数量（按 rjCode 去重，忽略空 rjCode）。 */
    @Query(
        "SELECT COUNT(DISTINCT rjCode) FROM listening_sessions " +
            "WHERE listeningDate BETWEEN :startDate AND :endDate AND rjCode != ''"
    )
    suspend fun distinctWorkCount(startDate: String, endDate: String): Int

    /** 区间内有收听记录的天数。 */
    @Query(
        "SELECT COUNT(DISTINCT listeningDate) FROM listening_sessions " +
            "WHERE listeningDate BETWEEN :startDate AND :endDate"
    )
    suspend fun activeDayCount(startDate: String, endDate: String): Int

    /** 区间内累计收听时长（ms）。 */
    @Query(
        "SELECT COALESCE(SUM(durationMs), 0) FROM listening_sessions " +
            "WHERE listeningDate BETWEEN :startDate AND :endDate"
    )
    suspend fun totalDurationMs(startDate: String, endDate: String): Long

    /**
     * 区间内按 tags 快照聚合的累计时长。tags 为逗号分隔字符串，
     * 由上层拆分后再按单个 tag 汇总（保持 DAO 层简单、可测）。
     */
    @Query(
        "SELECT tags AS tags, SUM(durationMs) AS durationMs FROM listening_sessions " +
            "WHERE listeningDate BETWEEN :startDate AND :endDate AND tags != '' " +
            "GROUP BY tags"
    )
    suspend fun tagDurationTotals(startDate: String, endDate: String): List<TagDurationRow>

    /**
     * 区间内按"开始时刻的小时"聚合时长，用于时段偏好。
     * 使用 SQLite strftime 以设备本地时间解释毫秒时间戳（'unixepoch','localtime'）。
     */
    @Query(
        "SELECT CAST(strftime('%H', startAtMs / 1000, 'unixepoch', 'localtime') AS INTEGER) AS hour, " +
            "SUM(durationMs) AS durationMs FROM listening_sessions " +
            "WHERE listeningDate BETWEEN :startDate AND :endDate " +
            "GROUP BY hour ORDER BY hour ASC"
    )
    suspend fun hourDurationTotals(startDate: String, endDate: String): List<HourDurationRow>

    /** 区间内按作品聚合的收听排行（时长降序）。 */
    @Query(
        "SELECT albumId AS albumId, rjCode AS rjCode, title AS title, circle AS circle, cv AS cv, " +
            "coverUrl AS coverUrl, coverPath AS coverPath, coverThumbPath AS coverThumbPath, " +
            "SUM(durationMs) AS durationMs, COUNT(*) AS sessionCount FROM listening_sessions " +
            "WHERE listeningDate BETWEEN :startDate AND :endDate AND rjCode != '' " +
            "GROUP BY rjCode ORDER BY durationMs DESC LIMIT :limit"
    )
    suspend fun topAlbums(startDate: String, endDate: String, limit: Int): List<AlbumListeningRow>
}
