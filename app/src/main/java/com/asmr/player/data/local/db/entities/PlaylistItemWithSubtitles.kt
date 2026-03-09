package com.asmr.player.data.local.db.entities

data class PlaylistItemWithSubtitles(
    val playlistId: Long,
    val mediaId: String,
    val title: String,
    val artist: String = "",
    val uri: String,
    val artworkUri: String = "",
    val playbackArtworkUri: String = "",
    val itemOrder: Int = 0,
    val hasSubtitles: Boolean
)

