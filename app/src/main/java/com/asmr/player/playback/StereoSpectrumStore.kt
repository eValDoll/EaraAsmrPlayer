package com.asmr.player.playback

import androidx.media3.common.util.UnstableApi

@UnstableApi
class StereoSpectrumStore(
    val binCount: Int
) {
    private val left: Array<FloatArray> = Array(2) { FloatArray(binCount) }
    private val right: Array<FloatArray> = Array(2) { FloatArray(binCount) }

    @Volatile
    private var publishedIndex: Int = 0

    fun beginWrite(): Int = 1 - publishedIndex

    fun leftWriteBuffer(index: Int): FloatArray = left[index]

    fun rightWriteBuffer(index: Int): FloatArray = right[index]

    fun publish(index: Int) {
        publishedIndex = index
    }

    fun copyLatestLeft(out: FloatArray) {
        left[publishedIndex].copyInto(out)
    }

    fun copyLatestRight(out: FloatArray) {
        right[publishedIndex].copyInto(out)
    }
}
