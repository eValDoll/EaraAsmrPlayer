package com.asmr.player.ui.library

import com.asmr.player.data.remote.api.AsmrOneRecommendationItem
import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumDetailSimilarWorksTest {
    @Test
    fun buildSimilarWorks_excludesSeedAndDeduplicatesAliases() {
        val works = buildAlbumDetailSimilarWorks(
            seedRjCode = "rj01000001",
            items = listOf(
                AsmrOneRecommendationItem(rj = "RJ01000001", title = "当前作品"),
                AsmrOneRecommendationItem(
                    rj = "RJ01000002",
                    title = "相似作品",
                    cvs = listOf("声优甲", "声优甲", "声优乙"),
                    mainCoverUrl = "https://example.com/cover.jpg"
                ),
                AsmrOneRecommendationItem(
                    originalWorkno = "rj01000002",
                    title = "重复作品"
                )
            )
        )

        assertEquals(1, works.size)
        assertEquals("RJ01000002", works.single().rjCode)
        assertEquals("相似作品", works.single().title)
        assertEquals("声优甲 / 声优乙", works.single().cv)
    }

    @Test
    fun buildSimilarWorks_usesMatchedRjAndFallsBackToWorkNumberTitle() {
        val works = buildAlbumDetailSimilarWorks(
            seedRjCode = "RJ01000001",
            items = listOf(
                AsmrOneRecommendationItem(
                    matchedRjs = listOf("invalid", "BJ02370869")
                )
            )
        )

        assertEquals(1, works.size)
        assertEquals("BJ02370869", works.single().rjCode)
        assertEquals("BJ02370869", works.single().title)
    }
}
