package com.asmr.player.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextContrastDecisionTest {
    @Test
    fun pickBlackOrWhiteTextColor_lightBackground_picksBlack() {
        val picked = pickBlackOrWhiteTextColorArgbFromBackgroundLuminance(backgroundLuminance = 0.92)
        assertEquals(ArgbBlack, picked)
    }

    @Test
    fun pickBlackOrWhiteTextColor_darkBackground_picksWhite() {
        val picked = pickBlackOrWhiteTextColorArgbFromBackgroundLuminance(backgroundLuminance = 0.03)
        assertEquals(ArgbWhite, picked)
    }

    @Test
    fun pickBlackOrWhiteTextColor_nearThreshold_picksHigherContrast() {
        val tieLuminance = kotlin.math.sqrt(0.0525) - 0.05
        val pickedBelow = pickBlackOrWhiteTextColorArgbFromBackgroundLuminance(backgroundLuminance = tieLuminance - 0.0003)
        val pickedAbove = pickBlackOrWhiteTextColorArgbFromBackgroundLuminance(backgroundLuminance = tieLuminance + 0.0003)

        assertEquals(ArgbWhite, pickedBelow)
        assertEquals(ArgbBlack, pickedAbove)
    }

    @Test
    fun pickBlackOrWhiteTextColor_samplingFailed_returnsNull() {
        val picked = pickBlackOrWhiteTextColorArgbFromBackgroundLuminance(backgroundLuminance = null)
        assertNull(picked)
    }
}
