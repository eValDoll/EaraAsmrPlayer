package com.asmr.player.data.local.datastore

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchCacheStoreTest {
    @Test
    fun mergeSearchHistory_trimsDeduplicatesAndKeepsRecentFirst() {
        val result = mergeSearchHistory(
            keyword = "  Rain  ",
            existing = listOf("rain", "ASMR", " ", "CV A"),
            limit = 3
        )

        assertEquals(listOf("Rain", "ASMR", "CV A"), result)
    }

    @Test
    fun mergeSearchHistory_limitsExistingHistoryWhenKeywordIsBlank() {
        val result = mergeSearchHistory(
            keyword = "",
            existing = listOf("one", "two", "ONE", "three"),
            limit = 2
        )

        assertEquals(listOf("one", "two"), result)
    }

    @Test
    fun mergeSearchHistory_defaultLimitKeepsFiftyItems() {
        val existing = (1..60).map { "item$it" }

        val result = mergeSearchHistory(
            keyword = "new",
            existing = existing
        )

        assertEquals(50, result.size)
        assertEquals("new", result.first())
        assertEquals("item49", result.last())
    }

    @Test
    fun mergeSearchHistory_zeroLimitReturnsEmptyList() {
        val result = mergeSearchHistory(
            keyword = "new",
            existing = listOf("old"),
            limit = 0
        )

        assertEquals(emptyList<String>(), result)
    }
}
