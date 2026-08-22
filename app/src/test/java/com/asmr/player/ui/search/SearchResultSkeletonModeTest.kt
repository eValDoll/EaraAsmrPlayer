package com.asmr.player.ui.search

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchResultSkeletonModeTest {
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
