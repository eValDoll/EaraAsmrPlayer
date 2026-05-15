package com.asmr.player.data.local.db.entities

data class PlaylistItemWithSubtitles(
    val playlistId: Long,
    val mediaId: String,
    val title: String,
    val artist: String = "",
    val albumTitle: String = "",
    val albumCv: String = "",
    val uri: String,
    val duration: Double = 0.0,
    val artworkUri: String = "",
    val playbackArtworkUri: String = "",
    val albumId: Long = 0L,
    val trackId: Long = 0L,
    val rjCode: String = "",
    val albumWorkId: String = "",
    val trackGroup: String = "",
    val lyricsRelativePathNoExt: String = "",
    val mimeType: String = "",
    val isVideo: Boolean = false,
    val itemOrder: Int = 0,
    val hasSubtitles: Boolean
)

