package com.asmr.player.ui.search

import com.asmr.player.data.local.datastore.LastSearchStateV1
import com.asmr.player.data.local.datastore.restoreCollectedSource
import com.asmr.player.data.remote.api.AsmrOneCollectedSearchItem
import com.asmr.player.data.remote.api.buildAsmrOneCollectedSearchUrl
import com.asmr.player.domain.model.CollectedSearchSource
import com.asmr.player.domain.model.Album
import com.asmr.player.ui.nav.Routes
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class JpAsmrSearchSupportTest {
    @Test fun numericResultsRetainIdentityAndDetailSource() {
        val item = AsmrOneCollectedSearchItem(workId = 138562, rj = "UN124393", title = "数字作品", pageUrl = "https://japaneseasmr.com/138562/")
        val album = item.toCollectedAlbum(source = CollectedSearchSource.JapaneseAsmr)
        assertEquals("UN124393", album.rjCode)
        assertEquals("UN124393", album.workId)
        assertFalse(album.hasAsmrOne)
        assertNull(album.asmrOneWorkId)
        assertEquals("album_detail_rj/UN124393?resourceSource=JapaneseAsmr", Routes.albumDetailByRj(album.rjCode, resourceSource = album.collectedSourceName))
        assertEquals("", item.resolvedWorkNo())
    }

    @Test fun sourceSelectionHasIndependentFiltersAndSorting() {
        val jp = SearchFilterOption.fromState(false, false, false, true, "JapaneseAsmr")
        assertEquals(SearchFilterOption.JapaneseAsmr, jp)
        assertTrue(jp.isCollectedOnly)
        assertTrue(jp.supportsWorkFilters)
        assertFalse(jp.supportsSubtitleFilter)
        assertEquals(listOf(SearchCollectedSortOption.ReleaseNew), jp.collectedSortOptions)
        assertEquals(SearchFilterOption.Collected, SearchFilterOption.fromState(false, false, false, true))
        assertEquals(SearchFilterOption.Standard, SearchFilterOption.fromState(false, false, false, false, "JapaneseAsmr"))
        assertEquals(SearchCollectedSortOption.ReleaseNew, normalizedCollectedSort("JapaneseAsmr", SearchCollectedSortOption.RatingHigh))
    }

    @Test fun sourceRequestsUseIndependentEndpointAndOnlyReleaseSort() {
        val url = buildAsmrOneCollectedSearchUrl("https://eara.example", "耳 -猫", 30, 60, "rating", true, true, CollectedSearchSource.JapaneseAsmr)
        assertEquals("/api/jp-asmr/search", url.encodedPath)
        assertEquals("release", url.queryParameter("sort"))
        assertEquals("60", url.queryParameter("offset"))
        assertEquals("耳 -猫", url.queryParameter("q"))
        assertNull(url.queryParameter("hasSubtitle"))
        assertEquals("true", url.queryParameter("allAges"))
    }

    @Test fun jpResultsNeverBecomeAsmrOneAvailabilityOrWorkIds() {
        val item = AsmrOneCollectedSearchItem(workId = 150867, rj = "RJ01707048", title = "测试", pageUrl = "https://japaneseasmr.com/150867/")
        val album = item.toCollectedAlbum(source = CollectedSearchSource.JapaneseAsmr)
        assertEquals("JapaneseAsmr", album.collectedSourceName)
        assertEquals(item.pageUrl, album.collectedPageUrl)
        assertNull(album.asmrOneWorkId)
        assertFalse(album.hasAsmrOne)
        val one = item.toCollectedAlbum()
        assertTrue(one.hasAsmrOne)
        assertEquals(150867, one.asmrOneWorkId)
    }

    @Test fun oldCacheDefaultsToAsmrOneAndNewCacheKeepsSource() {
        val gson = Gson()
        val old = gson.fromJson("""{"savedAtMs":1,"keyword":"耳","orderName":"Trend","purchasedOnly":false,"collectedOnly":true,"collectedSortName":"ReleaseNew","locale":"ja_JP","page":1,"canGoNext":false,"results":[]}""", LastSearchStateV1::class.java)
        assertEquals(CollectedSearchSource.AsmrOne, CollectedSearchSource.fromName(old.collectedSourceName))
        val jp = old.copy(collectedSourceName = "JapaneseAsmr", page = 3)
        val restored = gson.fromJson(gson.toJson(jp), LastSearchStateV1::class.java)
        assertEquals("JapaneseAsmr", restored.collectedSourceName)
        assertEquals(3, restored.page)
        val request = SearchAssistSearchRequest(collectedSourceName = "JapaneseAsmr", collectedSortName = "RatingHigh")
        assertEquals(SearchFilterOption.JapaneseAsmr, request.selectedFilter)
        assertEquals(SearchCollectedSortOption.ReleaseNew, request.selectedCollectedSort)

        // Legacy albums omit the new fields; copying them must remain safe.
        val albumJson = gson.toJsonTree(Album(title = "缓存作品", path = "", rjCode = "RJ01707048")).asJsonObject
        albumJson.remove("collectedSourceName")
        albumJson.remove("collectedPageUrl")
        val legacyAlbum = gson.fromJson(albumJson, Album::class.java)
        val oneCache = old.copy(results = listOf(legacyAlbum)).restoreCollectedSource()
        assertEquals("AsmrOne", oneCache.results.single().collectedSourceName)
        assertEquals("缓存作品", oneCache.results.single().copy(title = "缓存作品").title)
        val jpCache = jp.copy(results = listOf(legacyAlbum)).restoreCollectedSource()
        assertEquals("JapaneseAsmr", jpCache.results.single().collectedSourceName)
    }

    @Test fun resultScrollAndDetailNavigationCarrySource() {
        val state = SearchUiState.Success(results = emptyList(), keyword = "耳", page = 1,
            order = SearchSortOption.Trend, collectedSort = SearchCollectedSortOption.ReleaseNew,
            purchasedOnly = false, presaleOnly = false, chineseTranslatedOnly = false,
            collectedOnly = true, locale = "ja_JP", canGoPrev = false, canGoNext = false)
        assertNotEquals(searchResultScrollKey(state), searchResultScrollKey(state.copy(collectedSourceName = "JapaneseAsmr")))
        assertEquals("album_detail_rj/RJ01707048?resourceSource=JapaneseAsmr", Routes.albumDetailByRj("RJ01707048", resourceSource = "JapaneseAsmr"))
    }
}
