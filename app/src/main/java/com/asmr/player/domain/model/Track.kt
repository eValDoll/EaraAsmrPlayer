package com.asmr.player.domain.model

data class Track(
    val id: Long = 0L,
    val albumId: Long,
    val title: String,
    val path: String,
    val duration: Double = 0.0,
    val group: String = ""
)

