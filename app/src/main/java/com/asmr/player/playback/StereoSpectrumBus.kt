package com.asmr.player.playback

import androidx.media3.common.util.UnstableApi

@UnstableApi
object StereoSpectrumBus {
    const val DefaultBinCount: Int = 128
    val store: StereoSpectrumStore = StereoSpectrumStore(DefaultBinCount)
    @Volatile
    var playbackActive: Boolean = false
}
