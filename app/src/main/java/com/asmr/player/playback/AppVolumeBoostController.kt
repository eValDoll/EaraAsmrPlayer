package com.asmr.player.playback

import android.media.AudioManager
import android.util.Log

class AppVolumeBoostController(
    private val audioManager: AudioManager
) {
    fun applyVolumePercent(percent: Int): Float {
        val maxSystemVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (maxSystemVolume <= 0) return 0f
        val (targetSystemVolume, playerVolume) =
            AppVolume.resolveSystemVolume(percent, maxSystemVolume)
        val currentSystemVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

        if (currentSystemVolume != targetSystemVolume) {
            runCatching {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetSystemVolume, 0)
            }.onFailure {
                Log.w(TAG, "Failed to apply system media volume=$targetSystemVolume", it)
            }
        }
        return playerVolume
    }

    fun currentVolumePercent(): Int {
        val maxSystemVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentSystemVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        return AppVolume.percentFromSystemVolume(currentSystemVolume, maxSystemVolume)
    }

    fun release() = Unit

    private companion object {
        private const val TAG = "AppVolumeBoost"
    }
}
