package com.asmr.player.ui.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SearchResultScrollKeyTest {
    @Test
    fun searchResultScrollKey_changesWhenSamePageGetsNewResults() {
        val before = success(resultRevision = 1L)
        val after = success(resultRevision = 2L)

        assertNotEquals(searchResultScrollKey(before), searchResultScrollKey(after))
    }

    @Test
    fun searchResultScrollKey_changesWhenSearchQueryChangesOnSamePage() {
        val before = success(keyword = "RJ123456", resultRevision = 1L)
        val after = success(keyword = "", resultRevision = 1L)

        assertNotEquals(searchResultScrollKey(before), searchResultScrollKey(after))
    }

    @Test
    fun searchResultScrollKey_ignoresBackgroundLoadingProgress() {
        val before = success(resultRevision = 3L)
        val after = before.copy(
            isEnriching = true,
            enrichingRjCodes = setOf("RJ123456"),
            isAsmrOneChecking = true,
            asmrOneChecked = 8,
            asmrOneTotal = 30
        )

        assertEquals(searchResultScrollKey(before), searchResultScrollKey(after))
    }

    private fun success(
        keyword: String = "RJ123456",
        page: Int = 1,
        resultRevision: Long = 1L
    ): SearchUiState.Success {
        return SearchUiState.Success(
            results = emptyList(),
            keyword = keyword,
            page = page,
            order = SearchSortOption.Trend,
            collectedSort = SearchCollectedSortOption.ReleaseNew,
            purchasedOnly = false,
            presaleOnly = false,
            chineseTranslatedOnly = false,
            collectedOnly = true,
            locale = "ja_JP",
            canGoPrev = page > 1,
            canGoNext = false,
            resultRevision = resultRevision
        )
    }
}
