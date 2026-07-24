package com.asmr.player.domain.model

import androidx.compose.runtime.Immutable
import com.asmr.player.util.RemoteSubtitleSource

@Immutable
data class Track(
    val id: Long = 0L,
    val albumId: Long,
    val title: String,
    val path: String,
    val duration: Double = 0.0,
    val group: String = "",
    val lyricsRelativePathNoExt: String = "",
    val remoteSubtitleSources: List<RemoteSubtitleSource> = emptyList()
)

