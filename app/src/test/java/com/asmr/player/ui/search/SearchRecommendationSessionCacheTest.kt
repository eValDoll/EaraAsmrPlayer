package com.asmr.player.ui.search

import com.asmr.player.data.remote.api.AsmrOneRecommendationItem
import com.asmr.player.data.remote.api.AsmrOneRecommendationResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchRecommendationSessionCacheTest {
    private val seedRjs = listOf("RJ123456")
    private val excludeRjs = listOf("RJ123456", "RJ654321")

    @Test
    fun sameNormalizedInputsRestoreCachedRecommendationSession() {
        val cache = SearchRecommendationSessionCache()
        val response = recommendationResponse("RJ777777", "next-cursor")

        cache.write(
            seedRjs = listOf(" rj123456 "),
            excludeRjs = listOf("rj123456", "RJ654321"),
            response = response,
            nowElapsedMs = 100L
        )

        assertEquals(
            response,
            cache.read(seedRjs, excludeRjs, nowElapsedMs = 200L)
        )
    }

    @Test
    fun changedSeedsOrExcludesDoNotReuseCachedSession() {
        val cache = SearchRecommendationSessionCache()
        cache.write(
            seedRjs = seedRjs,
            excludeRjs = excludeRjs,
            response = recommendationResponse("RJ777777", "next-cursor"),
            nowElapsedMs = 100L
        )

        assertNull(
            cache.read(
                seedRjs = listOf("RJ999999"),
                excludeRjs = excludeRjs,
                nowElapsedMs = 200L
            )
        )
        assertNull(
            cache.read(
                seedRjs = seedRjs,
                excludeRjs = excludeRjs + "RJ888888",
                nowElapsedMs = 200L
            )
        )
    }

    @Test
    fun latestRecommendationPageReplacesEarlierPage() {
        val cache = SearchRecommendationSessionCache()
        cache.write(
            seedRjs = seedRjs,
            excludeRjs = excludeRjs,
            response = recommendationResponse("RJ777777", "next-cursor"),
            nowElapsedMs = 100L
        )
        val latest = recommendationResponse("RJ888888", "next-cursor")

        cache.write(
            seedRjs = seedRjs,
            excludeRjs = excludeRjs,
            response = latest,
            nowElapsedMs = 200L
        )

        assertEquals(latest, cache.read(seedRjs, excludeRjs, nowElapsedMs = 300L))
    }

    @Test
    fun expiredSessionIsNotRestored() {
        val cache = SearchRecommendationSessionCache()
        cache.write(
            seedRjs = seedRjs,
            excludeRjs = excludeRjs,
            response = recommendationResponse("RJ777777", "next-cursor"),
            nowElapsedMs = 100L
        )

        assertNull(
            cache.read(
                seedRjs = seedRjs,
                excludeRjs = excludeRjs,
                nowElapsedMs = 100L + RECOMMENDATION_SESSION_CACHE_TTL_MS
            )
        )
    }

    private fun recommendationResponse(
        rj: String,
        cursor: String
    ): AsmrOneRecommendationResponse = AsmrOneRecommendationResponse(
        items = listOf(AsmrOneRecommendationItem(rj = rj)),
        nextCursor = cursor,
        hasMore = cursor.isNotBlank()
    )
}
