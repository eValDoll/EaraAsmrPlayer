package com.asmr.player.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class NowPlayingLyricsSettingsTest {
    @Test
    fun defaults_matchExistingNowPlayingHighlightSize() {
        assertEquals(24f, NowPlayingLyricsSettings().highlightFontSizeSp, 0.001f)
    }
}
