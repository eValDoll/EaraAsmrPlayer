package com.asmr.player.data.local.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "playlist_items",
    primaryKeys = ["playlistId", "mediaId"],
    indices = [
        Index(value = ["playlistId"]),
        Index(value = ["mediaId"])
    ]
)
data class PlaylistItemEntity(
    val playlistId: Long,
    val mediaId: String,
    val title: String,
    val artist: String = "",
    val albumTitle: String = "",
    val uri: String,
    val artworkUri: String = "",
    val albumId: Long = 0L,
    val trackId: Long = 0L,
    val rjCode: String = "",
    val albumWorkId: String = "",
    val trackGroup: String = "",
    val lyricsRelativePathNoExt: String = "",
    @ColumnInfo(defaultValue = "''")
    val remoteSubtitleSources: String = "",
    val mimeType: String = "",
    val isVideo: Boolean = false,
    val itemOrder: Int = 0
)

