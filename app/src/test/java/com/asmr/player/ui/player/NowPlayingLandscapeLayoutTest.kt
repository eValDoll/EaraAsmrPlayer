package com.asmr.player.ui.player

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NowPlayingLandscapeLayoutTest {

    @Test
    fun lyricTypographyKeepsPhoneHighlightConsistentAndScalesTabletCandidates() {
        val phonePortrait = nowPlayingLyricTypographyMetrics(largeTypography = false)
        val phoneLandscape = nowPlayingLyricTypographyMetrics(largeTypography = false)
        val tablet = nowPlayingLyricTypographyMetrics(largeTypography = true)

        assertEquals(phonePortrait.currentFontSizeSp, phoneLandscape.currentFontSizeSp)
        assertEquals(24, phoneLandscape.currentFontSizeSp)
        assertEquals(16, phoneLandscape.upcomingFontSizeSp)
        assertEquals(28, tablet.currentFontSizeSp)
        assertEquals(20, tablet.upcomingFontSizeSp)
        assertTrue(tablet.upcomingFontSizeSp > phoneLandscape.upcomingFontSizeSp)
    }

    @Test
    fun lyricTrackOnlyAnimatesSequentialForwardAdvance() {
        assertTrue(shouldAnimateLyricTrackAdvance(previousIndex = 8, nextIndex = 9))
        assertFalse(shouldAnimateLyricTrackAdvance(previousIndex = 8, nextIndex = 8))
        assertFalse(shouldAnimateLyricTrackAdvance(previousIndex = 8, nextIndex = 12))
        assertFalse(shouldAnimateLyricTrackAdvance(previousIndex = 8, nextIndex = 7))
    }

    @Test
    fun regularPhoneLandscapeReservesMoreWidthForLyrics() {
        val metrics = nowPlayingLandscapeLayoutMetrics(
            screenHeight = 411.dp,
            tabletLayout = false
        )

        assertFalse(metrics.compactHeight)
        assertFalse(metrics.tabletLayout)
        assertEquals(20.dp, metrics.horizontalPadding)
        assertEquals(8.dp, metrics.topPadding)
        assertEquals(16.dp, metrics.bottomPadding)
        assertEquals(24.dp, metrics.contentSpacing)
        assertEquals(0.40f, metrics.artworkWeight)
        assertEquals(0.60f, metrics.contentWeight)
        assertEquals(76.dp, metrics.identityMinHeight)
        assertEquals(16.dp, metrics.artworkCornerRadius)
        assertEquals(56.dp, metrics.lyricsTopPadding)
        assertEquals(62.dp, metrics.progressHeight)
        assertEquals(80.dp, metrics.controlsHeight)
        assertEquals(292.dp, metrics.artworkMaxSize)
        assertEquals(88.dp, metrics.spectrumHeight)
        assertEquals(1f, metrics.artworkWeight + metrics.contentWeight)
    }

    @Test
    fun shortPhoneLandscapeUsesCompactVerticalMetricsAtBoundary() {
        val metrics = nowPlayingLandscapeLayoutMetrics(
            screenHeight = 360.dp,
            tabletLayout = false
        )

        assertTrue(metrics.compactHeight)
        assertEquals(12.dp, metrics.horizontalPadding)
        assertEquals(4.dp, metrics.topPadding)
        assertEquals(14.dp, metrics.bottomPadding)
        assertEquals(0.39f, metrics.artworkWeight)
        assertEquals(0.61f, metrics.contentWeight)
        assertEquals(64.dp, metrics.identityMinHeight)
        assertEquals(48.dp, metrics.lyricsTopPadding)
        assertEquals(60.dp, metrics.progressHeight)
        assertEquals(72.dp, metrics.controlsHeight)
        assertEquals(260.dp, metrics.artworkMaxSize)
        assertEquals(88.dp, metrics.spectrumHeight)
        assertEquals(1f, metrics.artworkWeight + metrics.contentWeight)
    }

    @Test
    fun tabletLandscapeUsesLargerLyricsAndBoundedArtwork() {
        val metrics = nowPlayingLandscapeLayoutMetrics(
            screenHeight = 800.dp,
            tabletLayout = true
        )

        assertTrue(metrics.tabletLayout)
        assertFalse(metrics.compactHeight)
        assertEquals(20.dp, metrics.topPadding)
        assertEquals(20.dp, metrics.bottomPadding)
        assertEquals(0.45f, metrics.artworkWeight)
        assertEquals(0.55f, metrics.contentWeight)
        assertEquals(336.dp, metrics.artworkMaxSize)
        assertEquals(380.dp, metrics.progressMaxWidth)
        assertEquals(90.dp, metrics.identityMinHeight)
        assertEquals(76.dp, metrics.lyricsTopPadding)
        assertEquals(80.dp, metrics.controlsHeight)
        assertEquals(112.dp, metrics.spectrumHeight)
        assertEquals(1f, metrics.artworkWeight + metrics.contentWeight)
    }

    @Test
    fun landscapeIdentityUsesSmallerTitleAndSmallArtistInfo() {
        val compact = nowPlayingLandscapeIdentityTypography(
            compactHeight = true,
            tabletLayout = false
        )
        val regular = nowPlayingLandscapeIdentityTypography(
            compactHeight = false,
            tabletLayout = false
        )
        val tablet = nowPlayingLandscapeIdentityTypography(
            compactHeight = false,
            tabletLayout = true
        )

        assertEquals(16, compact.titleFontSizeSp)
        assertEquals(11, compact.artistInfoFontSizeSp)
        assertEquals(18, regular.titleFontSizeSp)
        assertEquals(12, regular.artistInfoFontSizeSp)
        assertEquals(20, tablet.titleFontSizeSp)
        assertEquals(13, tablet.artistInfoFontSizeSp)
    }

    @Test
    fun spectrumCenterStaysHalfwayBetweenArtistInfoAndHighlightedLyric() {
        assertEquals(
            210f,
            landscapeSpectrumCenterY(
                artistInfoBottom = 160f,
                currentLyricAnchorTop = 260f,
                fallbackCenterY = 120f
            )
        )
        assertEquals(
            120f,
            landscapeSpectrumCenterY(
                artistInfoBottom = Float.NaN,
                currentLyricAnchorTop = 260f,
                fallbackCenterY = 120f
            )
        )
    }

    @Test
    fun landscapeLyricsUsesMeasuredAvailableHeight() {
        assertEquals(
            5,
            fittingUpcomingLyricCount(
                availableHeightPx = 220,
                contentTopPaddingPx = 48,
                currentHeightPx = 21,
                dividerHeightPx = 2,
                verticalSpacingPx = 8,
                upcomingHeightsPx = List(6) { 19 },
                maxCount = 6
            )
        )
        assertEquals(
            2,
            fittingUpcomingLyricCount(
                availableHeightPx = 133,
                contentTopPaddingPx = 48,
                currentHeightPx = 21,
                dividerHeightPx = 2,
                verticalSpacingPx = 8,
                upcomingHeightsPx = List(6) { 19 },
                maxCount = 6
            )
        )
        assertEquals(
            4,
            fittingUpcomingLyricCount(
                availableHeightPx = 420,
                contentTopPaddingPx = 76,
                currentHeightPx = 58,
                dividerHeightPx = 2,
                verticalSpacingPx = 12,
                upcomingHeightsPx = List(8) { 44 },
                maxCount = 8
            )
        )
    }

    @Test
    fun portraitClassicLyricsUsesAvailableVerticalSpaceForUpcomingLines() {
        assertEquals(0, portraitClassicLyricsUpcomingCount(75.dp))
        assertEquals(1, portraitClassicLyricsUpcomingCount(76.dp))
        assertEquals(2, portraitClassicLyricsUpcomingCount(112.dp))
    }
}
