package com.asmr.player.data.repository

import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.dao.AlbumListeningRow
import com.asmr.player.data.local.db.dao.HourDurationRow
import com.asmr.player.data.local.db.entities.ListeningSessionEntity
import com.asmr.player.util.ListeningDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 当前正在播放的作品上下文快照，由播放服务在主线程采集后传入。
 */
data class ListeningTrackContext(
    val albumId: Long,
    val rjCode: String,
    val title: String,
    val cv: String,
    val albumTitle: String,
    val artworkUri: String?
)

/**
 * 会话级收听记录仓库。
 *
 * 维护一个内存中的"当前会话"，随播放累加时长/流量/音轨数，并按节流写回数据库。
 * 会话切换条件：作品变化、静默间隔超过 [SESSION_GAP_MS]、或跨过收听日边界。
 *
 * 与 [StatisticsRepository]（按天聚合）互补：这里保留每次会话的起止时刻，
 * 以支撑"垂直时间线"展示与未来的"年度收听报告"分析。
 */
@Singleton
class ListeningRecordRepository @Inject constructor(
    private val db: AppDatabase
) {
    private val sessionDao get() = db.listeningSessionDao()
    private val albumDao get() = db.albumDao()

    private val mutex = Mutex()

    /** 当前内存会话（含已分配的数据库 id）。 */
    private var current: ListeningSessionEntity? = null
    /** 当前会话对应作品的身份标识，用于判断是否需要开新会话。 */
    private var currentIdentity: String? = null
    /** 上次写回数据库的时间戳（elapsed 无关，用 wall clock 即可）。 */
    private var lastPersistMs: Long = 0L

    private fun identityOf(context: ListeningTrackContext): String =
        if (context.albumId > 0L) "id:${context.albumId}" else "rj:${context.rjCode}"

    /**
     * 记录一次收听心跳（通常每秒调用一次）。
     * @param deltaMs 本次心跳累加的收听时长。
     * @param nowMs 当前 wall-clock 时间戳。
     */
    suspend fun recordTick(
        context: ListeningTrackContext,
        deltaMs: Long,
        nowMs: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        if (deltaMs <= 0L) return@withContext
        mutex.withLock {
            val session = ensureSession(context, nowMs)
            val updated = session.copy(
                durationMs = session.durationMs + deltaMs,
                lastActiveAtMs = nowMs
            )
            current = updated
            persistIfNeeded(nowMs, force = false)
        }
    }

    /** 把一段音频网络流量归入当前会话（若存在）。 */
    suspend fun addTraffic(bytes: Long, nowMs: Long = System.currentTimeMillis()) =
        withContext(Dispatchers.IO) {
            if (bytes <= 0L) return@withContext
            mutex.withLock {
                val session = current ?: return@withLock
                current = session.copy(trafficBytes = session.trafficBytes + bytes)
                persistIfNeeded(nowMs, force = false)
            }
        }

    /** 当前会话音轨计数 +1（作品内切歌且达到有效收听比例时调用）。 */
    suspend fun incrementTrackCount(nowMs: Long = System.currentTimeMillis()) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val session = current ?: return@withLock
                current = session.copy(trackCount = session.trackCount + 1)
                persistIfNeeded(nowMs, force = true)
            }
        }

    /** 播放暂停/停止时调用，强制写回并保留当前会话（便于短暂暂停后延续）。 */
    suspend fun flush() = withContext(Dispatchers.IO) {
        mutex.withLock { persistIfNeeded(System.currentTimeMillis(), force = true) }
    }

    // ---- 内部：会话生命周期 ----

    private suspend fun ensureSession(context: ListeningTrackContext, nowMs: Long): ListeningSessionEntity {
        val identity = identityOf(context)
        val existing = current
        val date = ListeningDay.dateOf(nowMs)
        val gapExceeded = existing != null && (nowMs - existing.lastActiveAtMs) > SESSION_GAP_MS
        val sameSession = existing != null &&
            currentIdentity == identity &&
            existing.listeningDate == date &&
            !gapExceeded
        if (sameSession) return existing!!

        // 关闭旧会话前先落库（force）。
        if (existing != null) persistIfNeeded(nowMs, force = true)

        val snapshot = buildSnapshot(context)
        val fresh = snapshot.copy(
            listeningDate = date,
            startAtMs = nowMs,
            lastActiveAtMs = nowMs
        )
        val newId = sessionDao.insert(fresh)
        val stored = fresh.copy(id = newId)
        current = stored
        currentIdentity = identity
        lastPersistMs = nowMs
        return stored
    }

    /** 用作品上下文 + 本地作品表补齐 circle/tags/cover 等快照信息。 */
    private suspend fun buildSnapshot(context: ListeningTrackContext): ListeningSessionEntity {
        val album = if (context.albumId > 0L) runCatching { albumDao.getAlbumById(context.albumId) }.getOrNull() else null
        return ListeningSessionEntity(
            albumId = context.albumId,
            rjCode = context.rjCode.ifBlank { album?.rjCode.orEmpty() },
            title = album?.title?.ifBlank { context.albumTitle } ?: context.albumTitle.ifBlank { context.title },
            circle = album?.circle.orEmpty(),
            cv = album?.cv?.ifBlank { context.cv } ?: context.cv,
            tags = album?.tags.orEmpty(),
            coverUrl = album?.coverUrl?.ifBlank { context.artworkUri.orEmpty() } ?: context.artworkUri.orEmpty(),
            coverPath = album?.coverPath.orEmpty(),
            coverThumbPath = album?.coverThumbPath.orEmpty(),
            listeningDate = "",
            startAtMs = 0L,
            lastActiveAtMs = 0L
        )
    }

    /** 按节流（[PERSIST_INTERVAL_MS]）或强制写回当前会话。 */
    private suspend fun persistIfNeeded(nowMs: Long, force: Boolean) {
        val session = current ?: return
        if (!force && nowMs - lastPersistMs < PERSIST_INTERVAL_MS) return
        lastPersistMs = nowMs
        runCatching { sessionDao.update(session) }
    }

    // ---- 年度报告数据基础：只读聚合封装 ----

    fun observeSessionsForDate(date: String): Flow<List<ListeningSessionEntity>> =
        sessionDao.observeSessionsForDate(date)

    suspend fun distinctWorkCount(startDate: String, endDate: String): Int =
        withContext(Dispatchers.IO) { sessionDao.distinctWorkCount(startDate, endDate) }

    suspend fun activeDayCount(startDate: String, endDate: String): Int =
        withContext(Dispatchers.IO) { sessionDao.activeDayCount(startDate, endDate) }

    suspend fun totalDurationMs(startDate: String, endDate: String): Long =
        withContext(Dispatchers.IO) { sessionDao.totalDurationMs(startDate, endDate) }

    suspend fun topAlbums(startDate: String, endDate: String, limit: Int): List<AlbumListeningRow> =
        withContext(Dispatchers.IO) { sessionDao.topAlbums(startDate, endDate, limit) }

    suspend fun hourDurationTotals(startDate: String, endDate: String): List<HourDurationRow> =
        withContext(Dispatchers.IO) { sessionDao.hourDurationTotals(startDate, endDate) }

    /**
     * 区间内按单个 tag 聚合的累计时长（对逗号分隔的 tags 快照做拆分归并），降序。
     */
    suspend fun tagDurationTotals(startDate: String, endDate: String): List<Pair<String, Long>> =
        withContext(Dispatchers.IO) {
            val rows = sessionDao.tagDurationTotals(startDate, endDate)
            val acc = HashMap<String, Long>()
            for (row in rows) {
                for (raw in row.tags.split(',')) {
                    val tag = raw.trim()
                    if (tag.isEmpty()) continue
                    acc[tag] = (acc[tag] ?: 0L) + row.durationMs
                }
            }
            acc.entries.sortedByDescending { it.value }.map { it.key to it.value }
        }

    companion object {
        /** 静默超过此间隔视为新会话（30 分钟）。 */
        private const val SESSION_GAP_MS = 30L * 60L * 1000L
        /** 写回数据库的节流间隔（5 秒）。 */
        private const val PERSIST_INTERVAL_MS = 5_000L
    }
}
