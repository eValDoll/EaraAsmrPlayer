package com.asmr.player.ui.player

import androidx.compose.ui.text.style.TextAlign
import com.asmr.player.data.settings.LyricsPageSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsPageLayoutTest {
    @Test
    fun runtimeMaxVisibleLines_isBoundedByViewportHeight() {
        assertEquals(5, calculateRuntimeMaxVisibleLines(viewportHeightPx = 520f, lineBlockHeightPx = 100f))
        assertEquals(1, calculateRuntimeMaxVisibleLines(viewportHeightPx = 0f, lineBlockHeightPx = 100f))
        assertEquals(1, calculateRuntimeMaxVisibleLines(viewportHeightPx = 520f, lineBlockHeightPx = 0f))
    }

    @Test
    fun viewportLayout_clampsVisibleLinesAndTopOffset() {
        val layout = buildLyricsViewportLayout(
            settings = LyricsPageSettings(
                maxVisibleLines = 9,
                centerPositionFraction = 0.9f
            ),
            viewportHeightPx = 640f,
            lineBlockHeightPx = 100f
        )

        assertEquals(6, layout.runtimeMaxVisibleLines)
        assertEquals(6, layout.effectiveVisibleLines)
        assertEquals(600f, layout.viewportWindowHeightPx, 0.001f)
        assertTrue(layout.viewportTopOffsetPx in 0f..40f)
    }

    @Test
    fun lyricTextAlign_mapsStoredValues() {
        assertEquals(TextAlign.Start, lyricTextAlign(0))
        assertEquals(TextAlign.Center, lyricTextAlign(1))
        assertEquals(TextAlign.End, lyricTextAlign(2))
        assertEquals(TextAlign.Center, lyricTextAlign(999))
    }
}
