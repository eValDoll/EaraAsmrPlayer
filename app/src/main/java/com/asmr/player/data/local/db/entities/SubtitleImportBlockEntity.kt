package com.asmr.player.data.local.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 用户删除字幕后，按音频路径阻止自动重新导入；重新扫描分配新 trackId 时仍然有效。 */
@Entity(tableName = "subtitle_import_blocks")
data class SubtitleImportBlockEntity(@PrimaryKey val mediaPath: String)
