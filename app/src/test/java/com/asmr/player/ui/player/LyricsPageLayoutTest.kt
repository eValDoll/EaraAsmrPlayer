package com.asmr.player.ui.player

import androidx.compose.ui.text.style.TextAlign
import com.asmr.player.data.settings.LyricsPageSettings
import org.junit.Assert.assertEquals
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
                displayAreaMode = 2
            ),
            viewportHeightPx = 640f,
            nominalItemHeightPx = 100f
        )

        assertEquals(100f, layout.nominalItemHeightPx, 0.001f)
        assertEquals(160f, layout.viewportWindowHeightPx, 0.001f)
        assertEquals(240f, layout.viewportTopOffsetPx, 0.001f)
    }

    @Test
    fun viewportLayout_respectsMeasuredWindowWithinRequestedBounds() {
        val layout = buildLyricsViewportLayout(
            settings = LyricsPageSettings(displayAreaMode = 1),
            viewportHeightPx = 640f,
            nominalItemHeightPx = 100f,
            measuredWindowHeightPx = 140f
        )

        assertEquals(140f, layout.viewportWindowHeightPx, 0.001f)
        assertEquals(0f, layout.viewportTopOffsetPx, 0.001f)
    }

    @Test
    fun viewportLayout_ignoresMeasuredWindowForFullDisplayArea() {
        val layout = buildLyricsViewportLayout(
            settings = LyricsPageSettings(displayAreaMode = 0),
            viewportHeightPx = 640f,
            nominalItemHeightPx = 100f,
            measuredWindowHeightPx = 140f
        )

        assertEquals(640f, layout.viewportWindowHeightPx, 0.001f)
        assertEquals(0f, layout.viewportTopOffsetPx, 0.001f)
    }

    @Test
    fun lyricTextAlign_mapsStoredValues() {
        assertEquals(TextAlign.Start, lyricTextAlign(0))
        assertEquals(TextAlign.Center, lyricTextAlign(1))
        assertEquals(TextAlign.End, lyricTextAlign(2))
        assertEquals(TextAlign.Center, lyricTextAlign(999))
    }

    @Test
    fun centeredLyricIndexForTimeline_picksNearestVisibleItemCenter() {
        val frames = listOf(
            LyricVisibleItemFrame(index = 0, offsetPx = -20, sizePx = 60),
            LyricVisibleItemFrame(index = 1, offsetPx = 40, sizePx = 80),
            LyricVisibleItemFrame(index = 2, offsetPx = 120, sizePx = 60)
        )

        assertEquals(1, centeredLyricIndexForTimeline(frames, viewportCenterPx = 100f, totalCount = 3))
    }

    @Test
    fun centeredLyricIndexForTimeline_ignoresInvalidFrames() {
        val frames = listOf(
            LyricVisibleItemFrame(index = -1, offsetPx = 0, sizePx = 100),
            LyricVisibleItemFrame(index = 4, offsetPx = 10, sizePx = 100),
            LyricVisibleItemFrame(index = 1, offsetPx = 80, sizePx = 0),
            LyricVisibleItemFrame(index = 2, offsetPx = 80, sizePx = 40)
        )

        assertEquals(2, centeredLyricIndexForTimeline(frames, viewportCenterPx = 100f, totalCount = 3))
    }

    @Test
    fun centeredLyricIndexForTimeline_returnsMinusOneWhenUnavailable() {
        assertEquals(-1, centeredLyricIndexForTimeline(emptyList(), viewportCenterPx = 100f, totalCount = 3))
        assertEquals(-1, centeredLyricIndexForTimeline(emptyList(), viewportCenterPx = 100f, totalCount = 0))
    }
}
