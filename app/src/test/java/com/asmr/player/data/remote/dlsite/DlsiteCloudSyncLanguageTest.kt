package com.asmr.player.data.remote.dlsite

import com.asmr.player.LocalizedRobolectricTest
import com.asmr.player.data.remote.scraper.buildDlsitePageConnection
import com.asmr.player.data.remote.scraper.parseDlsiteTags
import com.asmr.player.domain.model.Album
import com.asmr.player.domain.model.AlbumTagCodec
import com.asmr.player.domain.model.localizedTags
import com.asmr.player.i18n.AppLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@Config(sdk = [32])
class DlsiteCloudSyncLanguageTest : LocalizedRobolectricTest() {
    @After fun restoreLanguage() { AppLanguage.select("") }

    @Test fun englishCloudSyncFetchesAndPersistsLocalizedTagsDespiteStoredChineseLocale() = runBlocking {
        AppLanguage.select("en")
        MockWebServer().use { server ->
            server.enqueue(page("en-US", "Ear cleaning"))
            server.enqueue(page("zh-CN", "掏耳"))
            val result = resolveSelectedDlsiteCloudSync("RJ254481") { workno, locale ->
                val doc = buildDlsitePageConnection(
                    server.url("/work/$workno.html?locale=zh_CN&translation=$workno").toString(),
                    baseCookie = "session=test; locale=zh_CN",
                    locale = locale,
                ).get()
                Album(title = "Album", path = "", rjCode = workno,
                    tags = doc.select(".main_genre a").map { it.text() },
                    tagDetails = parseDlsiteTags(doc.select(".main_genre a")))
            } as DlsiteCloudSyncResolveResult.Success

            assertEquals("en_US", result.locale)
            val first = checkNotNull(server.takeRequest(1, TimeUnit.SECONDS))
            assertEquals(listOf("en_US"), first.requestUrl!!.queryParameterValues("locale"))
            assertEquals("RJ254481", first.requestUrl!!.queryParameter("translation"))
            assertTrue(first.getHeader("Accept-Language")!!.startsWith("en-US"))
            assertEquals("session=test; locale=en_US; adultchecked=1", first.getHeader("Cookie"))
            val second = checkNotNull(server.takeRequest(1, TimeUnit.SECONDS))
            assertEquals("zh_CN", second.requestUrl!!.queryParameter("locale"))

            // Local albums persist raw tags and multilingual details separately.
            val stored = result.details.copy(
                tags = result.details.tags.joinToString(",").split(","),
                tagDetails = AlbumTagCodec.decode(AlbumTagCodec.encode(result.details.tagDetails)),
                metadataLocale = null,
            )
            assertEquals(listOf("Ear cleaning"), stored.localizedTags())
            AppLanguage.select("zh-CN")
            assertEquals(listOf("掏耳"), stored.localizedTags())
        }
    }

    @Test fun pageRequestsFollowLanguageChangesWhenNoExplicitLocaleIsPassed() {
        MockWebServer().use { server ->
            val url = server.url("/work/RJ254481.html").toString()
            AppLanguage.select("en")
            val english = buildDlsitePageConnection(url, "locale=zh_CN").request()
            assertTrue(english.url().query.contains("locale=en_US"))
            assertTrue(english.header("Cookie")!!.contains("locale=en_US"))
            AppLanguage.select("zh-CN")
            val chinese = buildDlsitePageConnection(url, "locale=en_US").request()
            assertTrue(chinese.url().query.contains("locale=zh_CN"))
            assertTrue(chinese.header("Accept-Language")!!.startsWith("zh-CN"))
        }
    }

    @Test fun fallbackSearchKeepsOriginalRequestedLanguageForDetails() = runBlocking {
        AppLanguage.select("en")
        val searches = mutableListOf<String>()
        val details = mutableListOf<String>()
        val result = resolveDlsiteCloudSync(
            keyword = "Album", baseWorkno = "",
            search = { _, locale ->
                searches += locale
                AppLanguage.select("zh-CN")
                if (locale == "en_US") emptyList() else listOf(album())
            },
            fetchDetails = { _, locale -> details += locale; album() },
        ) as DlsiteCloudSyncResolveResult.Success
        assertEquals(listOf("en_US", "zh_CN"), searches)
        assertEquals(listOf("en_US", "zh_CN"), details)
        assertEquals("en_US", result.locale)
    }

    @Test fun englishDetailsRemainUsableWhenChineseSupplementFails() = runBlocking {
        AppLanguage.select("en")
        val attempts = mutableListOf<String>()
        val result = resolveSelectedDlsiteCloudSync("RJ254481") { _, locale ->
            attempts += locale
            if (locale != "en_US") error("unavailable")
            album().copy(tags = listOf("Ear cleaning"))
        } as DlsiteCloudSyncResolveResult.Success
        assertEquals(listOf("en_US", "zh_CN"), attempts)
        assertEquals(listOf("Ear cleaning"), result.details.localizedTags())
    }

    @Test fun cancelledEnglishRequestDoesNotContinueWithOtherLanguages() = runBlocking {
        AppLanguage.select("en")
        val attempts = mutableListOf<String>()
        val failure = runCatching {
            resolveSelectedDlsiteCloudSync("RJ254481") { _, locale ->
                attempts += locale
                throw CancellationException("cancelled")
            }
        }.exceptionOrNull()
        assertTrue(failure is CancellationException)
        assertEquals(listOf("en_US"), attempts)
    }

    private fun album() = Album(title = "Album", path = "", rjCode = "RJ254481")

    private fun page(language: String, tag: String) = MockResponse()
        .setHeader("Content-Type", "text/html; charset=utf-8")
        .setBody("""<html lang="$language"><div class="main_genre"><a href="/genre/123/">$tag</a></div></html>""")
}
