package com.asmr.player.ui.search

import com.asmr.player.data.remote.api.AsmrOneRecommendationItem
import com.asmr.player.data.remote.api.AsmrOneRecommendationResponse
import org.junit.Assert.assertEquals
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
            matchedRjs = listOf("invalid", "BJ02370869")
        ).toSearchAssistRecommendation()

        assertEquals("BJ02370869", recommendation.album.rjCode)
    }

    @Test
    fun continuationCursorRequiresHasMore() {
        val activeCursor = AsmrOneRecommendationResponse(
            nextCursor = "  next-page  ",
            hasMore = true
        ).recommendationContinuationCursor()
        val exhaustedCursor = AsmrOneRecommendationResponse(
            nextCursor = "stale-cursor",
            hasMore = false
        ).recommendationContinuationCursor()

        assertEquals("next-page", activeCursor)
        assertEquals("", exhaustedCursor)
    }

    @Test
    fun continuationCursorRejectsBlankValue() {
        val cursor = AsmrOneRecommendationResponse(
            nextCursor = "   ",
            hasMore = true
        ).recommendationContinuationCursor()

        assertEquals("", cursor)
    }
}
