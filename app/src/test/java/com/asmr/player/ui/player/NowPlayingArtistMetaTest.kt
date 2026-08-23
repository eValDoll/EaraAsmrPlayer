package com.asmr.player.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class NowPlayingArtistMetaTest {
    @Test
    fun separatesCircleAndEveryDistinctVoiceActor() {
        assertEquals(
            NowPlayingArtistMeta(
                circle = "妄想研究所",
                cvNames = listOf("一之瀬りと", "みもりあいの")
            ),
            parseNowPlayingArtistMeta("妄想研究所 / 一之瀬りと，みもりあいの、一之瀬りと")
        )
    }

    @Test
    fun treatsArtistWithoutCircleSeparatorAsVoiceActors() {
        assertEquals(
            NowPlayingArtistMeta(
                circle = "",
                cvNames = listOf("声優甲", "声優乙", "声優丙")
            ),
            parseNowPlayingArtistMeta("声優甲、声優乙；声優丙")
        )
    }

    @Test
    fun formatsExpandedSummaryWithCircleCvDividerAndReadableCvSeparator() {
        assertEquals(
            "妄想研究所 | 一之瀬りと、みもりあいの",
            formatExpandedArtistSummary(
                NowPlayingArtistMeta(
                    circle = "妄想研究所",
                    cvNames = listOf("一之瀬りと", "みもりあいの")
                )
            )
        )
    }

    @Test
    fun formatsClassicSummaryAsOnePlainTextLine() {
        assertEquals(
            "社团 妄想研究所 / CV 一之瀬りと、みもりあいの",
            formatClassicArtistSummary(
                NowPlayingArtistMeta(
                    circle = "妄想研究所",
                    cvNames = listOf("一之瀬りと", "みもりあいの")
                )
            )
        )
    }
}
