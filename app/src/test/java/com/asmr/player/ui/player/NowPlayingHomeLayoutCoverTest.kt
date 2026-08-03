package com.asmr.player.ui.player

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class NowPlayingHomeLayoutCoverTest {
    @Test
    fun classicTrackInfoHeightOnlyExpandsWhenTitleWraps() {
        assertEquals(82.dp, nowPlayingClassicTrackInfoHeight(titleLineCount = 1))
        assertEquals(102.dp, nowPlayingClassicTrackInfoHeight(titleLineCount = 2))
        assertEquals(102.dp, nowPlayingClassicTrackInfoHeight(titleLineCount = 3))
    }

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
            360.dp,
            nowPlayingHomeCoverWidth(
                expanded = false,
                availableWidth = 720.dp,
                widthClass = WindowWidthSizeClass.Medium,
                contentHorizontalPadding = 24.dp
            )
        )
    }

    @Test
    fun compactClassicCoverUsesReducedPaddedWidth() {
        assertEquals(
            287.04f,
            nowPlayingHomeCoverWidth(
                expanded = false,
                availableWidth = 360.dp,
                widthClass = WindowWidthSizeClass.Compact,
                contentHorizontalPadding = 24.dp
            ).value,
            0.001f
        )
    }

    @Test
    fun compactClassicCoverReservesIdentityHierarchyWhenTopContentHeightIsShort() {
        val screenHeight = 592.dp

        assertEquals(
            180.dp,
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
    fun compactClassicTopPaddingKeepsCoverAwayFromHeader() {
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
    fun compactExpandedCoverOnlyReservesLyricsRoomWhenTopContentHeightIsShort() {
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
