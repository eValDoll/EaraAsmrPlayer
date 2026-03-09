package com.asmr.player.data.local.db.dao

data class LibraryTrackAlbumHeaderRow(
    val albumId: Long,
    val albumTitle: String,
    val circle: String,
    val cv: String,
    val coverUrl: String,
    val coverPath: String,
    val workId: String,
    val rjCode: String
)
