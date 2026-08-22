package com.asmr.player.data.remote.api

import org.junit.Assert.assertEquals
import org.junit.Test

class AsmrOneRecommendationSupportTest {
    @Test
    fun collectedSearchUrl_addsEnabledSubtitleAndAllAgesFilters() {
        val url = buildAsmrOneCollectedSearchUrl(
            baseUrl = "https://eara.example/",
            keyword = " 治愈 ",
            limit = 30,
            offset = 60,
            sort = "rating",
            hasSubtitle = true,
            allAges = true
        )

        assertEquals("治愈", url.queryParameter("q"))
        assertEquals("30", url.queryParameter("limit"))
        assertEquals("60", url.queryParameter("offset"))
        assertEquals("rating", url.queryParameter("sort"))
        assertEquals("true", url.queryParameter("hasSubtitle"))
        assertEquals("true", url.queryParameter("allAges"))
    }

    @Test
    fun collectedSearchUrl_omitsDisabledFilters() {
        val url = buildAsmrOneCollectedSearchUrl(
            baseUrl = "https://eara.example",
            keyword = "",
            limit = 30,
            offset = 0,
            sort = "release",
            hasSubtitle = false,
            allAges = false
        )

        assertEquals(null, url.queryParameter("hasSubtitle"))
        assertEquals(null, url.queryParameter("allAges"))
    }

    @Test
    fun normalizeRecommendationRjsKeepsRecentValidUniqueValuesWithinLimit() {
        val result = normalizeRecommendationRjs(
            values = listOf(
                " rj123456 ",
                "RJ123456",
                "not-an-rj",
                "BJ02370869",
                "vj01005620",
                "RJ12345",
                "rj7777777"
            ),
            limit = 3
        )

        assertEquals(listOf("RJ123456", "BJ02370869", "VJ01005620"), result)
    }

    @Test
    fun normalizeRecommendationRjsReturnsEmptyForNonPositiveLimit() {
        assertEquals(emptyList<String>(), normalizeRecommendationRjs(listOf("RJ123456"), 0))
    }
}
