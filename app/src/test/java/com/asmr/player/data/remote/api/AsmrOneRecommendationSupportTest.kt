package com.asmr.player.data.remote.api

import org.junit.Assert.assertEquals
import org.junit.Test

class AsmrOneRecommendationSupportTest {
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
