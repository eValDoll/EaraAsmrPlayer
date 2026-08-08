package com.asmr.player.data.local.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * 记录"某个字幕翻译任务翻译了哪些本地库作品的显示名"。
 *
 * 当字幕翻译任务被创建时，会为任务涉及的作品（album）与音轨（track）各登记一行；
 * 服务端把标题翻译完成后把结果写入 albums.displayTitle / tracks.displayTitle，
 * 同时回写这里的 displayTitle 作为"由本任务写入的值"。
 *
 * - 任务成功完成：任务被删除，本表行随外键级联删除，displayTitle 列保留（显示名生效）。
 * - 任务被取消/删除：先按本表逐行核对当前 displayTitle 是否仍等于本任务写入的值，
 *   若是则清空回退为原始名称，随后删除任务。
 */
@Entity(
    tableName = "subtitle_title_owners",
    primaryKeys = ["taskId", "kind", "targetId"],
    foreignKeys = [
        ForeignKey(
            entity = SubtitleTaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["kind", "targetId"]),
        Index(value = ["taskId"])
    ]
)
data class SubtitleTitleOwnerEntity(
    val taskId: String,
    val kind: String,
    val targetId: Long,
    val displayTitle: String,
    val createdAt: Long
)

internal object SubtitleTitleOwnerKind {
    const val ALBUM = "ALBUM"
    const val TRACK = "TRACK"
}
