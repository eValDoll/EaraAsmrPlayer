package com.asmr.player.ui.player

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class NowPlayingHomeLayoutCoverTest {
    @Test
    fun classicTrackInfoHeightOnlyExpandsWhenTitleWraps() {
        assertEquals(88.dp, nowPlayingClassicTrackInfoHeight(titleLineCount = 1))
        assertEquals(108.dp, nowPlayingClassicTrackInfoHeight(titleLineCount = 2))
        assertEquals(108.dp, nowPlayingClassicTrackInfoHeight(titleLineCount = 3))
    }

    @Test
    fun compactHeightUsesCompactPortraitMetrics() {
        val metrics = nowPlayingPortraitLayoutMetrics(
            screenHeight = 640.dp,
            widthClass = WindowWidthSizeClass.Compact
        )

        assertEquals(true, metrics.compact)
        assertEquals(8.dp, metrics.topPadding)
        assertEquals(4.dp, metrics.coverVerticalPadding)
        assertEquals(148.dp, metrics.minimumCoverWidth)
        assertEquals(65.dp, nowPlayingClassicTrackInfoHeight(titleLineCount = 1, metrics = metrics))
        assertEquals(80.dp, nowPlayingClassicTrackInfoHeight(titleLineCount = 2, metrics = metrics))
    }

    @Test
    fun normalHeightKeepsRegularPortraitMetrics() {
        val metrics = nowPlayingPortraitLayoutMetrics(
            screenHeight = 904.dp,
            widthClass = WindowWidthSizeClass.Compact
        )

        assertEquals(false, metrics.compact)
        assertEquals(24.dp, metrics.topPadding)
        assertEquals(16.dp, metrics.coverVerticalPadding)
        assertEquals(180.dp, metrics.minimumCoverWidth)
        assertEquals(88.dp, nowPlayingClassicTrackInfoHeight(titleLineCount = 1, metrics = metrics))
        assertEquals(108.dp, nowPlayingClassicTrackInfoHeight(titleLineCount = 2, metrics = metrics))
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
    fun compactClassicCoverReservesBothCvAndLyricsRowsWhenTopContentHeightIsShort() {
        val metrics = nowPlayingPortraitLayoutMetrics(
            screenHeight = 640.dp,
            widthClass = WindowWidthSizeClass.Compact
        )

        assertEquals(
            202.dp,
            nowPlayingHomeCoverWidth(
                expanded = false,
                availableWidth = 360.dp,
                availableHeight = 360.dp,
                widthClass = WindowWidthSizeClass.Compact,
                contentHorizontalPadding = metrics.contentHorizontalPadding,
                topPadding = metrics.topPadding,
                coverVerticalPadding = metrics.coverVerticalPadding,
                identityHeight = metrics.audienceHeight +
                    nowPlayingClassicTrackInfoHeight(titleLineCount = 2, metrics = metrics),
                lyricsReserveHeight = metrics.classicLyricsReserveHeight,
                minimumCoverWidth = metrics.minimumCoverWidth
            )
        )
    }

    @Test
    fun compactExpandedCoverOnlyReservesLyricsRoomWhenTopContentHeightIsShort() {
        val metrics = nowPlayingPortraitLayoutMetrics(
            screenHeight = 640.dp,
            widthClass = WindowWidthSizeClass.Compact
        )

        assertEquals(
            264.dp,
            nowPlayingHomeCoverWidth(
                expanded = true,
                availableWidth = 360.dp,
                availableHeight = 360.dp,
                widthClass = WindowWidthSizeClass.Compact,
                contentHorizontalPadding = metrics.contentHorizontalPadding,
                coverAspectRatio = 1f,
                topPadding = 0.dp,
                coverVerticalPadding = 0.dp,
                identityHeight = 0.dp,
                lyricsReserveHeight = metrics.expandedLyricsReserveHeight,
                minimumCoverWidth = metrics.minimumCoverWidth
            )
        )
    }
}
