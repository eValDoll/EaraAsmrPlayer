package com.asmr.player.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.C

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
data class PlaybackSnapshot(
    val isConnected: Boolean = false,
    val isPlaying: Boolean = false,
    val playbackState: Int = 0,
    val repeatMode: Int = 0,
    val shuffleEnabled: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val playbackPitch: Float = 1.0f,
    val currentMediaItem: MediaItem? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val audioSessionId: Int = C.AUDIO_SESSION_ID_UNSET
)
