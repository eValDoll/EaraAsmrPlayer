package com.asmr.player.data.local.db.entities

import androidx.room.Entity

@Entity(
    tableName = "playlist_track_cross_ref",
    primaryKeys = ["playlistId", "trackId"]
)
data class PlaylistTrackCrossRef(
    val playlistId: Long,
    val trackId: Long,
    val trackOrder: Int = 0
)

