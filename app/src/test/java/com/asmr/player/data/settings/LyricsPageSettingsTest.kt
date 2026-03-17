package com.asmr.player.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsPageSettingsTest {
    @Test
    fun defaults_matchExpectedLyricsPagePresentation() {
        val settings = LyricsPageSettings()

        assertEquals(21f, settings.fontSizeSp, 0.001f)
        assertEquals(0.1f, settings.strokeWidthSp, 0.001f)
        assertEquals(1.5f, settings.lineHeightMultiplier, 0.001f)
        assertEquals(0, settings.align)
        assertEquals(0, settings.displayAreaMode)
    }
}
