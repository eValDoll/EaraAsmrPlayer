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

    @Test
    fun compactClassicCoverShrinksWhenTopContentHeightIsShort() {
        val screenHeight = 592.dp

        assertEquals(
            224.dp,
            nowPlayingHomeCoverWidth(
                expanded = false,
                availableWidth = 360.dp,
                availableHeight = 360.dp,
                widthClass = WindowWidthSizeClass.Compact,
                contentHorizontalPadding = 24.dp,
                topPadding = nowPlayingHomeTopPadding(
                    expanded = false,
                    screenHeight = screenHeight,
                    widthClass = WindowWidthSizeClass.Compact
                ),
                coverVerticalPadding = nowPlayingHomeCoverVerticalPadding(
                    expanded = false,
                    screenHeight = screenHeight,
                    widthClass = WindowWidthSizeClass.Compact
                )
            )
        )
    }

    @Test
    fun compactClassicTopPaddingKeepsAudienceLineVisible() {
        assertEquals(
            20.dp,
            nowPlayingHomeTopPadding(
                expanded = false,
                screenHeight = 592.dp,
                widthClass = WindowWidthSizeClass.Compact
            )
        )
    }

    @Test
    fun compactExpandedCoverReservesLyricsRoomWhenTopContentHeightIsShort() {
        val screenHeight = 592.dp

        assertEquals(
            242.dp,
            nowPlayingHomeCoverWidth(
                expanded = true,
                availableWidth = 360.dp,
                availableHeight = 360.dp,
                widthClass = WindowWidthSizeClass.Compact,
                contentHorizontalPadding = 24.dp,
                coverAspectRatio = 1f,
                topPadding = nowPlayingHomeTopPadding(
                    expanded = true,
                    screenHeight = screenHeight,
                    widthClass = WindowWidthSizeClass.Compact
                ),
                coverVerticalPadding = nowPlayingHomeCoverVerticalPadding(
                    expanded = true,
                    screenHeight = screenHeight,
                    widthClass = WindowWidthSizeClass.Compact
                )
            )
        )
    }
}
