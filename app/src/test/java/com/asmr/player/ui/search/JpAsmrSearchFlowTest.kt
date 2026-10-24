package com.asmr.player.ui.search

import android.app.Application
import android.os.Looper
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.ViewModelStore
import com.asmr.player.data.local.datastore.SearchCacheStore
import com.asmr.player.data.local.datastore.LastSearchState
import com.asmr.player.data.remote.api.Asmr100Api
import com.asmr.player.data.remote.api.Asmr200Api
import com.asmr.player.data.remote.api.Asmr300Api
import com.asmr.player.data.remote.api.AsmrOneApi
import com.asmr.player.data.remote.api.AsmrOneAvailabilityApi
import com.asmr.player.data.remote.crawler.AsmrOneCrawler
import com.asmr.player.data.remote.dlsite.DlsitePlayLibraryClient
import com.asmr.player.data.remote.dlsite.DlsiteProductInfoClient
import com.asmr.player.data.remote.scraper.DLSiteScraper
import com.asmr.player.data.settings.SettingsRepository
import com.asmr.player.domain.model.Album
import com.asmr.player.hotlistening.HotListeningApi
import com.asmr.player.util.MessageManager
import com.asmr.player.util.MessageType
import com.asmr.player.ui.library.collectedRibbonLabel
import com.asmr.player.ui.nav.Routes
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class JpAsmrSearchFlowTest {
    @Test
    fun restoredDlsiteResultsRefreshSourceLabelsRoutesAndPersistAvailability() {
        val server = MockWebServer().apply { start() }
        val context = RuntimeEnvironment.getApplication()
        val settingsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val settings = SettingsRepository(PreferenceDataStoreFactory.create(
            scope = settingsScope,
            produceFile = { context.cacheDir.resolve("collection-status.preferences_pb") }
        ))
        val cache = SearchCacheStore(InMemoryPreferencesDataStore())
        val viewModels = ViewModelStore()
        val client = OkHttpClient()
        val gson = Gson()
        val retrofit = Retrofit.Builder().baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create()).build()
        val vm = SearchViewModel(
            DLSiteScraper(context, DlsiteProductInfoClient(client)), DlsitePlayLibraryClient(client, context),
            AsmrOneAvailabilityApi(client, gson) { server.url("/").toString() },
            AsmrOneCrawler(retrofit.create(AsmrOneApi::class.java), retrofit.create(Asmr100Api::class.java),
                retrofit.create(Asmr200Api::class.java), retrofit.create(Asmr300Api::class.java), settings),
            settings, cache, HotListeningApi(client, gson), MessageManager()
        ).also { viewModels.put("availability", it) }
        try {
            val workNos = listOf("RJ123456", "RJ654321", "UN124393", "UND124393")
            runBlocking {
                cache.writeLast(LastSearchState(
                    savedAtMs = 1L, keyword = "耳", orderName = "Trend", purchasedOnly = false,
                    locale = "ja_JP", page = 1, canGoNext = false,
                    results = workNos.map { Album(title = "作品", path = "", rjCode = it, workId = it, hasAsmrOne = true) }
                ))
            }
            server.enqueue(MockResponse().setBody("""{"items":[
                {"rj":"RJ123456","collected":true,"sources":[
                    {"source":"asmr.one","rj":"RJ123456","workId":123456},
                    {"source":"jp-asmr","rj":"RJ123456","workId":150866}]},
                {"rj":"RJ654321","collected":true,"sources":[{"source":"asmr.one","rj":"RJ654321","workId":654321}]},
                {"rj":"UN124393","collected":true,"sources":[{"source":"jp-asmr","rj":"UN124393","workId":138562,"pageUrl":"https://japaneseasmr.com/138562/"}]},
                {"rj":"UND124393","collected":false,"sources":[]}
            ]}"""))
            vm.bootstrap("", false, "ja_JP", initialCollectedOnly = false)
            await { vm.uiState.value is SearchUiState.Success }
            assertEquals(0, server.requestCount)
            vm.refreshCollectionAvailability()
            await { (vm.uiState.value as? SearchUiState.Success)?.results?.all { it.collectionAvailability != null } == true }
            val albums = (vm.uiState.value as SearchUiState.Success).results
            assertEquals(listOf("收录", "asmr.one", "jp-asmr", null), albums.map { it.collectedRibbonLabel })
            assertEquals(listOf(true, true, false, false), albums.map { it.hasAsmrOne })
            assertEquals("album_detail_rj/UN124393?resourceSource=JapaneseAsmr",
                Routes.albumDetailByRj(albums[2].rjCode, resourceSource = albums[2].collectedSourceName))
            assertNull(albums[2].asmrOneWorkId)
            assertEquals("https://japaneseasmr.com/138562/", albums[2].collectedPageUrl)
            assertNull(albums[3].collectedSourceName)
            await { !(vm.uiState.value as SearchUiState.Success).isAsmrOneChecking }
            vm.refreshCollectionAvailability()
            await { !(vm.uiState.value as SearchUiState.Success).isAsmrOneChecking }
            await { runBlocking { cache.readLast() }?.results == albums }
            val restored = runBlocking { cache.readLast() }!!.results
            assertEquals(albums.map { it.collectedRibbonLabel }, restored.map { it.collectedRibbonLabel })
            assertEquals("JapaneseAsmr", restored[2].collectedSourceName)
            assertEquals(1, server.requestCount)
        } finally {
            viewModels.clear()
            settingsScope.cancel()
            server.shutdown()
        }
    }

    @Test
    fun switchingPagingRetryAndCacheRestorationKeepIndependentSource() {
        val server = MockWebServer().apply { start() }
        val context = RuntimeEnvironment.getApplication()
        val settingsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val settings = SettingsRepository(PreferenceDataStoreFactory.create(
            scope = settingsScope,
            produceFile = { context.cacheDir.resolve("jp-search.preferences_pb") }
        ))
        val cache = SearchCacheStore(InMemoryPreferencesDataStore())
        val messages = MessageManager()
        val viewModels = ViewModelStore()
        val client = OkHttpClient()
        val gson = Gson()
        val retrofit = Retrofit.Builder().baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create()).build()
        fun newViewModel(key: String): SearchViewModel = SearchViewModel(
            DLSiteScraper(context, DlsiteProductInfoClient(client)), DlsitePlayLibraryClient(client, context),
            AsmrOneAvailabilityApi(client, gson) { server.url("/").toString() },
            AsmrOneCrawler(retrofit.create(AsmrOneApi::class.java), retrofit.create(Asmr100Api::class.java),
                retrofit.create(Asmr200Api::class.java), retrofit.create(Asmr300Api::class.java), settings),
            settings, cache, HotListeningApi(client, gson), messages
        ).also { viewModels.put(key, it) }
        try {
            val vm = newViewModel("first")
            server.enqueue(result(0, "RJ01700001"))
            assertTrue(vm.search("耳", SearchSortOption.Trend, SearchCollectedSortOption.ReleaseNew,
                false, false, false, true, "AsmrOne", false, false, "ja_JP"))
            assertRequest(server, "/api/asmr-one/search", "0")
            await { (vm.uiState.value as? SearchUiState.Success)?.results?.singleOrNull()?.rjCode == "RJ01700001" }

            server.enqueue(result(30, "RJ01700002"))
            vm.nextPage()
            assertRequest(server, "/api/asmr-one/search", "30")
            await { (vm.uiState.value as? SearchUiState.Success)?.page == 2 }

            server.enqueue(result(0, "RJ01700003"))
            assertTrue(vm.updateSearchOptions(collectedSourceName = "JapaneseAsmr"))
            assertRequest(server, "/api/jp-asmr/search", "0")
            await { (vm.uiState.value as? SearchUiState.Success)?.collectedSourceName == "JapaneseAsmr" }
            assertEquals(1, (vm.uiState.value as SearchUiState.Success).page)

            server.enqueue(result(30, "UN124393"))
            vm.nextPage()
            assertRequest(server, "/api/jp-asmr/search", "30")
            await { (vm.uiState.value as? SearchUiState.Success)?.page == 2 }

            server.enqueue(MockResponse().setResponseCode(503))
            vm.refreshPage()
            assertRequest(server, "/api/jp-asmr/search", "30")
            await { (vm.uiState.value as? SearchUiState.Success)?.isBusy == false }
            assertEquals("JapaneseAsmr", (vm.uiState.value as SearchUiState.Success).collectedSourceName)
            assertEquals(MessageType.Error, runBlocking { messages.messages.first() }.type)

            server.enqueue(result(30, "UND353674"))
            vm.retry()
            assertRequest(server, "/api/jp-asmr/search", "30")
            await { (vm.uiState.value as? SearchUiState.Success)?.results?.singleOrNull()?.rjCode == "UND353674" }
            await { runBlocking { cache.readLast() }?.let { it.page == 2 && it.collectedSourceName == "JapaneseAsmr" } == true }
            val restored = newViewModel("restored")
            restored.bootstrap("", false, "ja_JP")
            await { restored.uiState.value is SearchUiState.Success }
            val state = restored.uiState.value as SearchUiState.Success
            assertEquals(2, state.page)
            assertEquals("JapaneseAsmr", state.collectedSourceName)
            assertEquals("JapaneseAsmr", state.results.single().collectedSourceName)
            assertEquals("UND353674", state.results.single().rjCode)
            assertEquals("UND353674", state.results.single().workId)
            assertEquals(6, server.requestCount)

            server.enqueue(MockResponse().setBody("""{"items":[],"total":0,"offset":0,"sort":"release"}"""))
            assertTrue(vm.search("RJ01700000"))
            assertRequest(server, "/api/jp-asmr/search", "0")
            await { (vm.uiState.value as? SearchUiState.Success)?.keyword == "RJ01700000" }
            assertTrue((vm.uiState.value as SearchUiState.Success).results.isEmpty())
        } finally {
            viewModels.clear()
            settingsScope.cancel()
            server.close()
        }
    }

    private fun result(offset: Int, rj: String) = MockResponse().setBody("""{
        "items":[{"workId":150867,"rj":"$rj","title":"测试","pageUrl":"https://japaneseasmr.com/150867/"}],
        "total":90,"offset":$offset,"sort":"release"
    }""")

    private fun assertRequest(server: MockWebServer, path: String, offset: String) {
        var request: okhttp3.mockwebserver.RecordedRequest? = null
        await {
            request = server.takeRequest(10, TimeUnit.MILLISECONDS)
            request != null
        }
        val url = checkNotNull(request).requestUrl!!
        assertEquals(path, url.encodedPath)
        assertEquals(offset, url.queryParameter("offset"))
        assertEquals("release", url.queryParameter("sort"))
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8)
        while (System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(10)
        }
        fail("search state did not settle")
    }

    private class InMemoryPreferencesDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        private val mutex = Mutex()
        override val data: StateFlow<Preferences> = state

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            mutex.withLock { transform(state.value).also { state.value = it } }
    }
}
