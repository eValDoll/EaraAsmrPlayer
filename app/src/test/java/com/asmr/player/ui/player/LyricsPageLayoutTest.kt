package com.asmr.player.ui.player

import androidx.compose.ui.text.style.TextAlign
import com.asmr.player.data.settings.LyricsPageSettings
import com.asmr.player.util.SubtitleEntry
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
    fun centeredLyricFocusTop_usesActiveHeightByDefault() {
        assertEquals(
            210f,
            centeredLyricFocusTop(
                viewportWindowHeightPx = 520f,
                activeItemHeightPx = 100f,
                nominalItemHeightPx = 60f,
                stableFocusAnchor = false
            ),
            0.001f
        )
    }

    @Test
    fun centeredLyricFocusTop_canKeepStableAnchorForVariableLineCounts() {
        val singleLineTop = centeredLyricFocusTop(
            viewportWindowHeightPx = 520f,
            activeItemHeightPx = 60f,
            nominalItemHeightPx = 60f,
            stableFocusAnchor = true
        )
        val multiLineTop = centeredLyricFocusTop(
            viewportWindowHeightPx = 520f,
            activeItemHeightPx = 132f,
            nominalItemHeightPx = 60f,
            stableFocusAnchor = true
        )

        assertEquals(singleLineTop, multiLineTop, 0.001f)
        assertEquals(230f, multiLineTop, 0.001f)
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
    fun lyricFocusVisualEffect_disabledOrActiveLineHasNoEffect() {
        assertEquals(0f, lyricFocusVisualEffectForLine(index = 2, activeIndex = 2, enabled = true).blurDp, 0.001f)
        assertEquals(0f, lyricFocusVisualEffectForLine(index = 1, activeIndex = 2, enabled = false).blurDp, 0.001f)
    }

    @Test
    fun lyricFocusVisualEffect_addsDirectionalDispersionAwayFromCenter() {
        val above = lyricFocusVisualEffectForLine(index = 3, activeIndex = 5, enabled = true)
        val below = lyricFocusVisualEffectForLine(index = 7, activeIndex = 5, enabled = true)
        val farBelow = lyricFocusVisualEffectForLine(index = 9, activeIndex = 5, enabled = true)

        assertTrue(above.blurDp > 0f)
        assertTrue(farBelow.blurDp > below.blurDp)
        assertTrue(above.dispersionOffsetYDp < 0f)
        assertTrue(below.dispersionOffsetYDp > 0f)
    }

    @Test
    fun lyricContentKey_changesWhenAudioLyricsChange() {
        val first = listOf(
            SubtitleEntry(startMs = 0L, endMs = 1000L, text = "第一首"),
            SubtitleEntry(startMs = 1200L, endMs = 2000L, text = "尾句")
        )
        val second = listOf(
            SubtitleEntry(startMs = 0L, endMs = 1000L, text = "第二首"),
            SubtitleEntry(startMs = 1200L, endMs = 2000L, text = "尾句")
        )

        assertTrue(lyricContentKey(first) != lyricContentKey(second))
        assertTrue(lyricContentKey(first, contentKey = "track-a") != lyricContentKey(first, contentKey = "track-b"))
    }

    @Test
    fun lyricDisplayActiveIndex_fallsBackToFirstLineBeforePlaybackStarts() {
        assertEquals(0, lyricDisplayActiveIndex(activeIndex = -1, totalCount = 4))
        assertEquals(3, lyricDisplayActiveIndex(activeIndex = 7, totalCount = 4))
        assertEquals(-1, lyricDisplayActiveIndex(activeIndex = -1, totalCount = 0))
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
