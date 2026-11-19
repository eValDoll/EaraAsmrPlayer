package com.asmr.player.ui.search

import com.asmr.player.domain.model.Album
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchResultSkeletonModeTest {
    @Test
    fun dlsiteListKeepsAvailableFieldsVisibleDuringDetailEnrichment() {
        val album = Album(title = "作品", path = "", tags = listOf("治愈"), cv = "甲", priceJpy = 1232)
        val mode = SearchResultSkeletonMode.DetailMetadata
        assertFalse(mode.hideStats(album, collectedOnly = false))
        assertFalse(mode.hideCv(album, collectedOnly = false))
        assertFalse(mode.hideTags(album, collectedOnly = false))
        assertTrue(mode.hideTags(album.copy(tags = emptyList(), tagDetails = null), collectedOnly = false))
        assertTrue(SearchResultSkeletonMode.LocalizedText.hideTags(album, collectedOnly = false))
    }

    @Test
    fun localeRefresh_showsLocalizedTextSkeleton() {
        assertEquals(
            SearchResultSkeletonMode.LocalizedText,
            searchResultSkeletonMode(
                onlineDetailLoading = true,
                isRefreshingLocalizedText = true
            )
        )
    }

    @Test
    fun normalEnrichment_showsDetailMetadataSkeleton() {
        assertEquals(
            SearchResultSkeletonMode.DetailMetadata,
            searchResultSkeletonMode(
                onlineDetailLoading = true,
                isRefreshingLocalizedText = false
            )
        )
    }

    @Test
    fun completedItem_hidesSkeleton() {
        assertEquals(
            SearchResultSkeletonMode.None,
            searchResultSkeletonMode(
                onlineDetailLoading = false,
                isRefreshingLocalizedText = true
            )
        )
    }
}
