package com.asmr.player.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsPageSettingsTest {
    @Test
    fun defaults_matchExpectedLyricsPagePresentation() {
        val settings = LyricsPageSettings()

        assertEquals(24f, settings.fontSizeSp, 0.001f)
        assertEquals(0f, settings.strokeWidthSp, 0.001f)
        assertEquals(1.4f, settings.lineHeightMultiplier, 0.001f)
        assertEquals(6, settings.maxVisibleLines)
        assertEquals(1, settings.align)
        assertEquals(0.5f, settings.centerPositionFraction, 0.001f)
    }
}
