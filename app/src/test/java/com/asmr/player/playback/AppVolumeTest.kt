package com.asmr.player.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class AppVolumeTest {

    @Test
    fun volumePercentSnapsToTwoPercentSteps() {
        assertEquals(0, AppVolume.clampPercent(0))
        assertEquals(2, AppVolume.clampPercent(1))
        assertEquals(2, AppVolume.clampPercent(2))
        assertEquals(100, AppVolume.clampPercent(99))
    }

    @Test
    fun resolveSystemVolumeUsesSystemStepAndPlayerInterpolation() {
        val (systemVolume, playerVolume) = AppVolume.resolveSystemVolume(percent = 50, maxSystemVolume = 15)
        assertEquals(8, systemVolume)
        assertEquals(0.9375f, playerVolume, 0.0001f)
    }

    @Test
    fun percentFromSystemVolumeDoesNotRoundIntoHigherSystemStep() {
        val percent = AppVolume.percentFromSystemVolume(systemVolume = 5, maxSystemVolume = 15)

        val (systemVolume, playerVolume) = AppVolume.resolveSystemVolume(percent, maxSystemVolume = 15)
        assertEquals(5, systemVolume)
        assertEquals(0.96f, playerVolume, 0.0001f)
    }
}
