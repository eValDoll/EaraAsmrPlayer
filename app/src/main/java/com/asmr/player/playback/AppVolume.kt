package com.asmr.player.playback

import kotlin.math.ceil
import kotlin.math.roundToInt

object AppVolume {
    const val MinPercent = 0
    const val NormalMaxPercent = 100
    const val MaxPercent = 100
    const val DefaultPercent = 100
    const val StepPercent = 2

    fun clampPercent(percent: Int): Int {
        val clamped = percent.coerceIn(MinPercent, MaxPercent)
        return ((clamped + StepPercent / 2) / StepPercent * StepPercent).coerceIn(MinPercent, MaxPercent)
    }

    fun adjustPercent(percent: Int, deltaPercent: Int): Int = clampPercent(percent + deltaPercent)

    fun basePlayerVolume(percent: Int): Float {
        return (clampPercent(percent) / MaxPercent.toFloat()).coerceIn(0f, 1f)
    }

    fun gainMultiplier(@Suppress("UNUSED_PARAMETER") percent: Int): Float {
        return 1f
    }

    fun resolveSystemVolume(percent: Int, maxSystemVolume: Int): Pair<Int, Float> {
        if (maxSystemVolume <= 0) return 0 to 0f
        val clamped = clampPercent(percent)
        if (clamped <= 0) return 0 to 0f

        val scaledSystemVolume = (clamped / MaxPercent.toFloat()) * maxSystemVolume.toFloat()
        val systemVolume = ceil(scaledSystemVolume.toDouble()).toInt().coerceIn(1, maxSystemVolume)
        val playerVolume = (scaledSystemVolume / systemVolume.toFloat()).coerceIn(0f, 1f)
        return systemVolume to playerVolume
    }

    fun percentFromSystemVolume(systemVolume: Int, maxSystemVolume: Int): Int {
        if (maxSystemVolume <= 0 || systemVolume <= 0) return 0
        val percent = (systemVolume.toFloat() / maxSystemVolume.toFloat() * MaxPercent).roundToInt()
        return clampPercent(percent)
    }

    fun visualFraction(percent: Int): Float {
        return (clampPercent(percent) / MaxPercent.toFloat()).coerceIn(0f, 1f)
    }
}
