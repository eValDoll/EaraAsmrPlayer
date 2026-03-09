package com.asmr.player.data.local.db.entities

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
    val uri: String,
    val artworkUri: String = "",
    val itemOrder: Int = 0
)

