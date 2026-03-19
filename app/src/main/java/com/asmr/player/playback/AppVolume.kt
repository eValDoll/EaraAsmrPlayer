package com.asmr.player.playback

import kotlin.math.log10
import kotlin.math.roundToInt

object AppVolume {
    const val MinPercent = 0
    const val NormalMaxPercent = 100
    const val MaxPercent = 300
    const val DefaultPercent = 100
    const val StepPercent = 5

    fun clampPercent(percent: Int): Int = percent.coerceIn(MinPercent, MaxPercent)

    fun adjustPercent(percent: Int, deltaPercent: Int): Int = clampPercent(percent + deltaPercent)

    fun basePlayerVolume(percent: Int): Float {
        return (clampPercent(percent).coerceAtMost(NormalMaxPercent) / 100f).coerceIn(0f, 1f)
    }

    fun gainMultiplier(percent: Int): Float {
        val clamped = clampPercent(percent)
        return if (clamped <= NormalMaxPercent) 1f else (clamped / 100f).coerceIn(1f, 3f)
    }

    fun boostGainMb(percent: Int): Int {
        val multiplier = gainMultiplier(percent)
        if (multiplier <= 1f) return 0
        return (20f * log10(multiplier) * 100f).roundToInt().coerceAtLeast(0)
    }

    fun visualFraction(percent: Int): Float {
        return (clampPercent(percent).coerceAtMost(NormalMaxPercent) / 100f).coerceIn(0f, 1f)
    }
}
