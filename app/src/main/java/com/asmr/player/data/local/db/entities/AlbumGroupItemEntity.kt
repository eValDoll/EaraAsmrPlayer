package com.asmr.player.data.local.db.entities

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "album_group_items",
    primaryKeys = ["groupId", "mediaId"],
    indices = [
        Index(value = ["groupId"]),
        Index(value = ["mediaId"])
    ]
)
data class AlbumGroupItemEntity(
    val groupId: Long,
    val mediaId: String,
    val itemOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
