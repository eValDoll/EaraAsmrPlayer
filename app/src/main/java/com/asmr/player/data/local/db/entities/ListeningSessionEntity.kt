package com.asmr.player.data.local.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 一次"收听会话"记录：用户对某个作品的一段连续收听。
 *
 * 采用会话级粒度（而非按天聚合），以便：
 * - 用"垂直时间线"忠实还原用户当日在某时刻的收听行为；
 * - 为未来的"年度收听报告"沉淀足够详尽的数据（tags 偏好、时间段偏好、时长、作品数、活跃天数等）。
 *
 * 作品的展示信息（标题/社团/CV/封面/tags）以快照形式冗余保存，
 * 这样即使作品被删除或来自在线库，历史时间线仍能正常渲染。
 */
@Entity(
    tableName = "listening_sessions",
    indices = [
        Index(value = ["listeningDate"]),
        Index(value = ["albumId"])
    ]
)
data class ListeningSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,

    /** 关联的本地作品 id；在线/未入库作品可能为 -1。 */
    val albumId: Long = -1L,
    val rjCode: String = "",

    // ---- 作品信息快照（用于时间线/报告展示，避免依赖 albums 表存活） ----
    val title: String = "",
    val circle: String = "",
    val cv: String = "",
    /** 逗号分隔的 tags 快照。 */
    val tags: String = "",
    val coverUrl: String = "",
    val coverPath: String = "",
    val coverThumbPath: String = "",

    // ---- 时间口径（凌晨 5 点重置的"收听日"） ----
    /** 该会话归属的收听日，"yyyy-MM-dd"。 */
    val listeningDate: String,
    /** 会话开始的真实时间戳（epoch ms），用于时间线排序与时段偏好分析。 */
    val startAtMs: Long,
    /** 会话最近一次活跃的时间戳（epoch ms），用于判断是否延续同一会话。 */
    val lastActiveAtMs: Long,

    // ---- 累计指标 ----
    /** 该会话累计的收听时长（ms）。 */
    val durationMs: Long = 0L,
    /** 该会话累计的音频流量（bytes），仅统计网络播放产生的音频字节。 */
    val trafficBytes: Long = 0L,
    /** 该会话内被有效计入的音轨数量。 */
    val trackCount: Int = 0
)
