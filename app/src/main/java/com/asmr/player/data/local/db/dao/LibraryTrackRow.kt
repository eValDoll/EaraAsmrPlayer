package com.asmr.player.data.local.db.dao

data class LibraryTrackRow(
    val trackId: Long,
    val albumId: Long,
    val trackTitle: String,
    val trackPath: String,
    val duration: Double,
    val hasSubtitles: Boolean,
    val trackGroup: String,
    val albumTitle: String,
    val circle: String,
    val cv: String,
    val coverUrl: String,
    val coverPath: String,
    val workId: String,
    val rjCode: String
)
