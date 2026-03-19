package com.asmr.player.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class AppVolumeTest {

    @Test
    fun baseVolumeCapsAtOneHundredPercent() {
        assertEquals(0.8f, AppVolume.basePlayerVolume(80), 0.0001f)
        assertEquals(1.0f, AppVolume.basePlayerVolume(100), 0.0001f)
        assertEquals(1.0f, AppVolume.basePlayerVolume(300), 0.0001f)
    }

    @Test
    fun boostGainIsOnlyAppliedAboveOneHundredPercent() {
        assertEquals(0, AppVolume.boostGainMb(0))
        assertEquals(0, AppVolume.boostGainMb(100))
        assertEquals(602, AppVolume.boostGainMb(200))
        assertEquals(954, AppVolume.boostGainMb(300))
    }
}
