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
                "RJ654321",
                "RJ12345",
                "rj7777777"
            ),
            limit = 2
        )

        assertEquals(listOf("RJ123456", "RJ654321"), result)
    }

    @Test
    fun normalizeRecommendationRjsReturnsEmptyForNonPositiveLimit() {
        assertEquals(emptyList<String>(), normalizeRecommendationRjs(listOf("RJ123456"), 0))
    }
}
