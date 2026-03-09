package com.asmr.player.data.local.db.entities

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "track_tag",
    primaryKeys = ["trackId", "tagId"],
    indices = [
        Index(value = ["trackId"]),
        Index(value = ["tagId"]),
        Index(value = ["source"])
    ]
)
data class TrackTagEntity(
    val trackId: Long,
    val tagId: Long,
    val source: Int
)

