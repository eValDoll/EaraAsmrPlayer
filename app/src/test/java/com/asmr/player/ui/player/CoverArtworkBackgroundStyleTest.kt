package com.asmr.player.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverArtworkBackgroundStyleTest {
    @Test
    fun zeroClarityShowsFlowWithoutTheStaticCover() {
        val style = coverArtworkBackdropStyle(0f)
        assertTrue(style.flowingVisible)
        assertEquals(0f, style.artworkAlpha, 0f)
    }

    @Test
    fun fullClarityShowsAnOpaqueCoverAndStopsFlowRendering() {
        val style = coverArtworkBackdropStyle(1f)
        assertFalse(style.flowingVisible)
        assertEquals(1f, style.artworkAlpha, 0f)
    }

    @Test
    fun increasingClarityGraduallyRevealsTheCover() {
        val values = listOf(0f, 0.05f, 0.35f, 0.5f, 0.8f, 1f)
            .map { coverArtworkBackdropStyle(it).artworkAlpha }
        assertTrue(values.zipWithNext().all { (low, high) -> low < high })
    }

    @Test
    fun invalidStoredValuesCannotProduceAnInvalidAlpha() {
        assertEquals(0f, coverArtworkBackdropStyle(-1f).artworkAlpha, 0f)
        assertEquals(1f, coverArtworkBackdropStyle(2f).artworkAlpha, 0f)
        assertEquals(0f, coverArtworkBackdropStyle(Float.NaN).artworkAlpha, 0f)
    }
}
