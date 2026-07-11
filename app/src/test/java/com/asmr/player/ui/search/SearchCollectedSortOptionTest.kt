package com.asmr.player.ui.search

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchCollectedSortOptionTest {
    @Test
    fun newestCollectedSortMapsToBackendCreateDateSort() {
        assertEquals(SearchCollectedSortOption.ReleaseNew, SearchCollectedSortOption.fromName(null))
        assertEquals(SearchCollectedSortOption.ReleaseNew, SearchCollectedSortOption.fromName("missing"))
        assertEquals(SearchCollectedSortOption.CollectedNew, SearchCollectedSortOption.fromName("CollectedNew"))
        assertEquals("create_date", SearchCollectedSortOption.CollectedNew.backendSort)
    }
}
