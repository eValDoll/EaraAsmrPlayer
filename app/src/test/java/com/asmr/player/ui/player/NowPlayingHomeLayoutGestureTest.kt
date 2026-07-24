package com.asmr.player.ui.player

import com.asmr.player.data.settings.NowPlayingHomeLayoutMode
import org.junit.Assert.assertEquals
import org.junit.Test

class NowPlayingHomeLayoutGestureTest {
    @Test
    fun upwardSwipeSwitchesToExpanded() {
        val result = resolveNowPlayingHomeLayoutModeAfterSwipe(
            currentMode = NowPlayingHomeLayoutMode.Classic,
            totalDragX = 12f,
            totalDragY = -72f,
            thresholdPx = 56f
        )

        assertEquals(NowPlayingHomeLayoutMode.Expanded, result)
    }

    @Test
    fun downwardSwipeSwitchesToClassic() {
        val result = resolveNowPlayingHomeLayoutModeAfterSwipe(
            currentMode = NowPlayingHomeLayoutMode.Expanded,
            totalDragX = 10f,
            totalDragY = 80f,
            thresholdPx = 56f
        )

        assertEquals(NowPlayingHomeLayoutMode.Classic, result)
    }

    @Test
    fun shortSwipeKeepsCurrentMode() {
        val result = resolveNowPlayingHomeLayoutModeAfterSwipe(
            currentMode = NowPlayingHomeLayoutMode.Expanded,
            totalDragX = 0f,
            totalDragY = 24f,
            thresholdPx = 56f
        )

        assertEquals(NowPlayingHomeLayoutMode.Expanded, result)
    }

    @Test
    fun mostlyHorizontalSwipeKeepsCurrentMode() {
        val result = resolveNowPlayingHomeLayoutModeAfterSwipe(
            currentMode = NowPlayingHomeLayoutMode.Classic,
            totalDragX = 90f,
            totalDragY = -70f,
            thresholdPx = 56f
        )

        assertEquals(NowPlayingHomeLayoutMode.Classic, result)
    }
}
