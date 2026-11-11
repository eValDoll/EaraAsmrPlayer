package com.asmr.player.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.asmr.player.domain.model.Album
import com.asmr.player.ui.common.albumCoverImageModel
import com.asmr.player.ui.common.albumStableKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SearchCacheStoreTest {
    @Test
    fun legacySearchCacheRestoresConstructorDefaultsBeforeRenderingCover() = runBlocking {
        val store = storeWithCache("""{
            "savedAtMs":1,"keyword":"雨音","orderName":"Trend","collectedSortName":"","purchasedOnly":false,
            "page":2,"canGoNext":true,"results":[{
                "title":"旧版作品","path":"","rjCode":"RJ123456",
                "coverUrl":"https://example.com/cover.jpg"
            }]
        }""")

        val state = requireNotNull(store.readLast())
        val album = state.results.single()
        assertEquals("https://example.com/cover.jpg", albumCoverImageModel(album))
        assertEquals("RJ123456", albumStableKey(album))
        assertEquals(Album(
            title = "旧版作品", path = "", rjCode = "RJ123456",
            coverUrl = "https://example.com/cover.jpg"
        ), album)
        assertEquals("", state.collectedSortName)
        assertEquals("雨音", state.keyword)
        assertEquals(2, state.page)
        assertEquals(true, state.canGoNext)
    }

    @Test
    fun explicitNullCacheFieldsUseDefaultsAndKeepRemoteCover() = runBlocking {
        val state = requireNotNull(storeWithCache("""{
            "keyword":null,"collectedSortName":null,"results":[{
                "title":"作品","path":"","manualCoverPath":null,
                "coverThumbPath":null,"coverPath":null,"rjCode":null,"tags":null,
                "tracks":null,"tagDetails":null,"coverUrl":"https://example.com/null-cover.jpg"
            }]
        }""").readLast())

        assertEquals("https://example.com/null-cover.jpg", albumCoverImageModel(state.results.single()))
        assertEquals("", state.keyword)
        assertEquals(emptyList<String>(), state.results.single().tags)
        assertNull(state.results.single().tagDetails)
        assertNotNull(albumStableKey(state.results.single()))
    }

    @Test
    fun legacyCollectedCacheCanCopyAlbumAndRestoreSource() = runBlocking {
        val state = requireNotNull(storeWithCache("""{
            "collectedOnly":true,"results":[{"title":"作品","path":"","rjCode":"RJ123456"}]
        }""").readLast())

        assertEquals(state.collectedSourceName, state.results.single().collectedSourceName)
        assertNull(albumCoverImageModel(state.results.single()))
    }

    @Test
    fun legacyCachedTracksRestoreTheirDefaults() = runBlocking {
        val album = requireNotNull(storeWithCache("""{
            "results":[{"title":"作品","path":"","tracks":[{"albumId":1,"title":"音声","path":"track.mp3"}]}]
        }""").readLast()).results.single()

        assertEquals("", album.tracks.single().group)
        assertEquals(emptyList<Any>(), album.tracks.single().remoteSubtitleSources)
    }

    @Test
    fun currentSearchCacheRoundTripKeepsManualCoverAndSearchFilters() = runBlocking {
        val manual = "/albums/manual.jpg"
        val state = LastSearchState(
            savedAtMs = 123L, keyword = "検索", orderName = "Trend", purchasedOnly = true,
            locale = "ja", page = 3, canGoNext = true, hasSubtitle = true, allAges = true,
            collectedSortName = "ReleaseNew",
            results = listOf(Album(
                title = "作品", path = "", displayTitle = "翻译作品", manualCoverPath = manual,
                coverThumbPath = "/thumbs/a_1_${manual.hashCode()}_v2.jpg",
                coverPath = "/albums/automatic.jpg", tags = listOf("ASMR"),
                coverUrl = "https://example.com/cover.jpg", asmrOneWorkId = 123
            ))
        )
        val store = storeWithCache("")
        store.writeLast(state)
        val restored = requireNotNull(store.readLast())

        assertEquals(state.restoreCollectedSource(), restored)
        assertEquals(state.results.single().coverThumbPath, albumCoverImageModel(restored.results.single()))
    }

    @Test
    fun invalidSearchCacheIsIgnoredWithoutRemovingHistory() = runBlocking {
        for (raw in listOf("{", "null", "[]", "{\"results\":[null]}", "{\"results\":[42]}")) {
            val store = storeWithCache(raw)
            store.addHistory("雨音")
            assertNull(store.readLast())
            assertEquals(listOf("雨音"), store.readHistory())
        }
    }

    private fun storeWithCache(raw: String): SearchCacheStore = SearchCacheStore(
        object : DataStore<Preferences> {
            override val data = MutableStateFlow(preferencesOf(stringPreferencesKey("last_search_state_v1") to raw))

            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                transform(data.value).also { data.value = it }
        }
    )

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
