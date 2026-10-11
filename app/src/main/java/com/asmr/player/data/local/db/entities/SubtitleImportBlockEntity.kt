package com.asmr.player.data.local.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 用户删除字幕后，按音频路径阻止后台自动导入；主动重扫成功导入后解除。 */
@Entity(tableName = "subtitle_import_blocks")
data class SubtitleImportBlockEntity(@PrimaryKey val mediaPath: String)
