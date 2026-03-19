package com.asmr.player.playback

import android.media.audiofx.LoudnessEnhancer
import android.util.Log

class AppVolumeBoostController {
    private var audioSessionId: Int = 0
    private var boostGainMb: Int = 0
    private var enhancer: LoudnessEnhancer? = null

    fun setAudioSessionId(audioSessionId: Int) {
        if (this.audioSessionId == audioSessionId) return
        this.audioSessionId = audioSessionId
        releaseEnhancer()
        ensureEnhancer()
    }

    fun setVolumePercent(percent: Int) {
        val nextBoostGainMb = AppVolume.boostGainMb(percent)
        if (boostGainMb == nextBoostGainMb) return
        boostGainMb = nextBoostGainMb
        applyBoost()
    }

    fun release() {
        releaseEnhancer()
    }

    private fun ensureEnhancer() {
        if (audioSessionId <= 0 || boostGainMb <= 0 || enhancer != null) return
        enhancer = runCatching { LoudnessEnhancer(audioSessionId) }
            .onFailure {
                Log.w(TAG, "Failed to create LoudnessEnhancer for session=$audioSessionId", it)
            }
            .getOrNull()
        applyBoost()
    }

    private fun applyBoost() {
        if (boostGainMb <= 0) {
            runCatching { enhancer?.enabled = false }
            return
        }
        ensureEnhancer()
        val currentEnhancer = enhancer ?: return
        runCatching {
            currentEnhancer.setTargetGain(boostGainMb)
            currentEnhancer.enabled = true
        }.onFailure {
            Log.w(TAG, "Failed to apply loudness boost=${boostGainMb}mB", it)
            releaseEnhancer()
        }
    }

    private fun releaseEnhancer() {
        enhancer?.release()
        enhancer = null
    }

    private companion object {
        private const val TAG = "AppVolumeBoost"
    }
}
