package com.asmr.player.ui.player

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class NowPlayingHomeLayoutCoverTest {
    @Test
    fun expandedCoverUsesFullAvailableWidth() {
        assertEquals(
            720.dp,
            nowPlayingHomeCoverWidth(
                expanded = true,
                availableWidth = 720.dp,
                widthClass = WindowWidthSizeClass.Medium,
                contentHorizontalPadding = 24.dp
            )
        )
    }

    @Test
    fun classicCoverKeepsComfortableWidthOnWidePortrait() {
        assertEquals(
            400.dp,
            nowPlayingHomeCoverWidth(
                expanded = false,
                availableWidth = 720.dp,
                widthClass = WindowWidthSizeClass.Medium,
                contentHorizontalPadding = 24.dp
            )
        )
    }
}
