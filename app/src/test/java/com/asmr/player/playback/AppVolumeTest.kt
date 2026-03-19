package com.asmr.player.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class AppVolumeTest {

    @Test
    fun baseVolumeCapsAtOneHundredPercent() {
        assertEquals(0.8f, AppVolume.basePlayerVolume(80), 0.0001f)
        assertEquals(1.0f, AppVolume.basePlayerVolume(100), 0.0001f)
        assertEquals(1.0f, AppVolume.basePlayerVolume(120), 0.0001f)
    }

    @Test
    fun volumePercentSnapsToTwoPercentSteps() {
        assertEquals(0, AppVolume.clampPercent(0))
        assertEquals(2, AppVolume.clampPercent(1))
        assertEquals(2, AppVolume.clampPercent(2))
        assertEquals(100, AppVolume.clampPercent(99))
    }

    @Test
    fun gainMultiplierAlwaysStaysNeutral() {
        assertEquals(1.0f, AppVolume.gainMultiplier(0), 0.0001f)
        assertEquals(1.0f, AppVolume.gainMultiplier(100), 0.0001f)
        assertEquals(1.0f, AppVolume.gainMultiplier(200), 0.0001f)
    }

    @Test
    fun resolveSystemVolumeUsesSystemStepAndPlayerInterpolation() {
        val (systemVolume, playerVolume) = AppVolume.resolveSystemVolume(percent = 50, maxSystemVolume = 15)
        assertEquals(8, systemVolume)
        assertEquals(0.9375f, playerVolume, 0.0001f)
    }
}
