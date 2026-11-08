package com.asmr.player.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StaticArtworkBackgroundStyleTest {
    @Test
    fun zeroClarityKeepsTheSolidTintWithoutTheCoverImage() {
        for (isDark in listOf(false, true)) {
            val style = staticArtworkBackdropStyle(0f, isDark)
            assertEquals(0f, style.artworkAlpha, 0f)
            assertTrue(style.baseTintAlpha > 0f)
        }
    }

    @Test
    fun fullClarityRestoresTheSharpCover() {
        for (isDark in listOf(false, true)) {
            val style = staticArtworkBackdropStyle(1f, isDark)
            assertEquals(0f, style.blurDp.value, 0f)
            assertTrue(style.artworkAlpha >= 0.98f)
        }
    }

    @Test
    fun increasingClarityRevealsTheCoverAndReducesBlur() {
        for (isDark in listOf(false, true)) {
            val styles = listOf(0f, 0.05f, 0.35f, 0.5f, 0.8f, 1f)
                .map { staticArtworkBackdropStyle(it, isDark) }
            assertTrue(styles.zipWithNext().all { (low, high) -> low.artworkAlpha < high.artworkAlpha })
            assertTrue(styles.zipWithNext().all { (low, high) -> low.blurDp > high.blurDp })
        }
    }

    @Test
    fun invalidStoredClarityFallsBackToValidEndpoints() {
        for (isDark in listOf(false, true)) {
            val hidden = staticArtworkBackdropStyle(0f, isDark)
            val visible = staticArtworkBackdropStyle(1f, isDark)
            for (value in listOf(-1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
                assertEquals(hidden, staticArtworkBackdropStyle(value, isDark))
            }
            assertEquals(visible, staticArtworkBackdropStyle(2f, isDark))
        }
    }
}
