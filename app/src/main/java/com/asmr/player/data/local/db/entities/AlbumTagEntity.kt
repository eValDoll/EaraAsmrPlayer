package com.asmr.player.data.local.db.entities

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "album_tag",
    primaryKeys = ["albumId", "tagId"],
    indices = [
        Index(value = ["albumId"]),
        Index(value = ["tagId"]),
        Index(value = ["source"])
    ]
)
data class AlbumTagEntity(
    val albumId: Long,
    val tagId: Long,
    val source: Int
)

