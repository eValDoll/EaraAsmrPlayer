package com.asmr.player.ui.search

import com.asmr.player.data.remote.api.AsmrOneRecommendationItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchAssistRecommendationSupportTest {
    @Test
    fun recommendationMapsTitleCvAndCover() {
        val recommendation = AsmrOneRecommendationItem(
            rj = "rj123456",
            title = "安眠作品",
            cvs = listOf("声优 A", "", "声优 B"),
            mainCoverUrl = "https://example.com/cover.jpg"
        ).toSearchAssistRecommendation()

        assertEquals("RJ123456", recommendation.album.rjCode)
        assertEquals("安眠作品", recommendation.album.title)
        assertEquals("声优 A / 声优 B", recommendation.album.cv)
        assertEquals("https://example.com/cover.jpg", recommendation.album.coverUrl)
    }

    @Test
    fun recommendationFallsBackToMatchedRj() {
        val recommendation = AsmrOneRecommendationItem(
            rj = "",
            matchedRjs = listOf("invalid", "RJ765432")
        ).toSearchAssistRecommendation()

        assertEquals("RJ765432", recommendation.album.rjCode)
    }

    @Test
    fun recommendationExclusionsContainEveryMatchedLanguageEdition() {
        val exclusions = AsmrOneRecommendationItem(
            rj = "RJ123456",
            originalWorkno = "RJ654321",
            matchedRjs = listOf("RJ123456", "RJ777777", "rj888888")
        ).recommendationExclusionRjs()

        assertEquals(
            listOf("RJ123456", "RJ654321", "RJ777777", "RJ888888"),
            exclusions
        )
    }

    @Test
    fun exclusionPlanKeepsExactLimitAndResetsOnlyAfterOverflow() {
        val listened = (100000 until 100190).map { "RJ$it" }
        val seenAtLimit = (200000 until 200010).map { "RJ$it" }

        val exactLimitPlan = planRecommendationExclusions(
            listenedRjs = listened,
            recommendationSeenRjs = seenAtLimit,
            maxExcludes = 200
        )
        assertFalse(exactLimitPlan.resetRecommendationSeen)
        assertEquals(200, exactLimitPlan.excludeRjs.size)

        val overflowPlan = planRecommendationExclusions(
            listenedRjs = listened,
            recommendationSeenRjs = seenAtLimit + "RJ200010",
            maxExcludes = 200
        )
        assertTrue(overflowPlan.resetRecommendationSeen)
        assertEquals(listened, overflowPlan.excludeRjs)
    }
}
