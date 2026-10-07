package com.asmr.player.ui.search

import android.app.Application
import android.os.Looper
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelStore
import com.asmr.player.data.local.datastore.SearchCacheStore
import com.asmr.player.data.remote.api.Asmr100Api
import com.asmr.player.data.remote.api.Asmr200Api
import com.asmr.player.data.remote.api.Asmr300Api
import com.asmr.player.data.remote.api.AsmrOneApi
import com.asmr.player.data.remote.api.AsmrOneAvailabilityApi
import com.asmr.player.data.remote.crawler.AsmrOneCrawler
import com.asmr.player.data.remote.dlsite.DlsitePlayLibraryClient
import com.asmr.player.data.remote.scraper.DLSiteScraper
import com.asmr.player.data.settings.SettingsRepository
import com.asmr.player.hotlistening.HotListeningApi
import com.asmr.player.util.MessageManager
import com.asmr.player.util.MessageType
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
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
    fun switchingPagingRetryAndCacheRestorationKeepIndependentSource() {
        val server = MockWebServer().apply { start() }
        val context = RuntimeEnvironment.getApplication()
        val settingsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val settings = SettingsRepository(PreferenceDataStoreFactory.create(
            scope = settingsScope,
            produceFile = { context.cacheDir.resolve("jp-search.preferences_pb") }
        ))
        val cache = SearchCacheStore(context)
        val messages = MessageManager()
        val viewModels = ViewModelStore()
        val client = OkHttpClient()
        val gson = Gson()
        val retrofit = Retrofit.Builder().baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create()).build()
        fun newViewModel(key: String): SearchViewModel = SearchViewModel(
            DLSiteScraper(context), DlsitePlayLibraryClient(client, context),
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

            server.enqueue(result(30, "UN124394"))
            vm.retry()
            assertRequest(server, "/api/jp-asmr/search", "30")
            await { (vm.uiState.value as? SearchUiState.Success)?.results?.singleOrNull()?.rjCode == "UN124394" }
            await { runBlocking { cache.readLast() }?.let { it.page == 2 && it.collectedSourceName == "JapaneseAsmr" } == true }
            val restored = newViewModel("restored")
            restored.bootstrap("", false, "ja_JP")
            await { restored.uiState.value is SearchUiState.Success }
            val state = restored.uiState.value as SearchUiState.Success
            assertEquals(2, state.page)
            assertEquals("JapaneseAsmr", state.collectedSourceName)
            assertEquals("JapaneseAsmr", state.results.single().collectedSourceName)
            assertEquals("UN124394", state.results.single().rjCode)
            assertEquals("UN124394", state.results.single().workId)
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
}
