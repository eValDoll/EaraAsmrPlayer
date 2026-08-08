package com.asmr.player.data.local.db.entities

/**
 * 作品显示名：优先使用翻译后的显示名，否则回退到原始名称。
 *
 * 物理文件名与数据库中的原始 title 始终保持不变；displayTitle 仅作为显示层的
 * 覆盖值，由字幕翻译任务写入，并在任务被取消/删除时清空回退。
 */
val AlbumEntity.titleForDisplay: String
    get() = displayTitle.ifBlank { title }

val TrackEntity.titleForDisplay: String
    get() = displayTitle.ifBlank { title }
