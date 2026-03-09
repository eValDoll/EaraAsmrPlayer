package com.asmr.player.data.local.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

@Fts4
@Entity(tableName = "album_fts")
data class AlbumFtsEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid") val albumId: Long,
    val title: String,
    val circle: String,
    val cv: String,
    val rjCode: String,
    val workId: String,
    val tagsToken: String
)
