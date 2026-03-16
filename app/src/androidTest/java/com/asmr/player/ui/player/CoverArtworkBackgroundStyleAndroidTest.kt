package com.asmr.player.ui.player

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CoverArtworkBackgroundStyleAndroidTest {

    @Test
    fun clarity100_hasNoBlur_andLowerOverlayTint() {
        val lowClarity = coverArtworkBackdropStyle(clarity = 0f, isDark = true)
        val highClarity = coverArtworkBackdropStyle(clarity = 1f, isDark = true)

        assertEquals(0f, highClarity.blurDp.value, 0.001f)
        assertTrue(highClarity.overlayAlpha < lowClarity.overlayAlpha)
        assertTrue(highClarity.tintAlpha < lowClarity.tintAlpha)
        assertTrue(highClarity.scrimAlpha < lowClarity.scrimAlpha)
    }

    @Test
    fun artworkVisibility_increasesAsClarityIncreases() {
        val lowClarity = coverArtworkBackdropStyle(clarity = 0f, isDark = false)
        val midClarity = coverArtworkBackdropStyle(clarity = 0.5f, isDark = false)
        val highClarity = coverArtworkBackdropStyle(clarity = 1f, isDark = false)

        assertTrue(lowClarity.artworkAlpha < midClarity.artworkAlpha)
        assertTrue(midClarity.artworkAlpha < highClarity.artworkAlpha)
    }
}
