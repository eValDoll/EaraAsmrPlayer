package com.asmr.player.service

sealed class PlaybackState {
    data object Idle : PlaybackState()
    data class Playing(val position: Long) : PlaybackState()
    data class Paused(val position: Long) : PlaybackState()
}

