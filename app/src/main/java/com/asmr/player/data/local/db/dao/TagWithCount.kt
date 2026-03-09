package com.asmr.player.data.local.db.dao

data class TagWithCount(
    val id: Long,
    val name: String,
    val nameNormalized: String,
    val albumCount: Long,
    val userAlbumCount: Long
)

