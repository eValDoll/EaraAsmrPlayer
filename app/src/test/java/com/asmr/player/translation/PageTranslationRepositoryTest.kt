package com.asmr.player.translation

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class PageTranslationRepositoryTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private fun repository(server: MockWebServer) =
        PageTranslationRepository(context, PageTranslationClient(OkHttpClient(), server.url("/").toString()))

    @Test fun defaultsAndLanguageChoicesMatchPageTranslationRequirements() {
        assertEquals("zh-CN", PageTranslationSettings().target)
        assertFalse(PageTranslationSettings().automatic)
        assertEquals(setOf("zh-CN", "zh-TW", "ja", "en", "ru", "ko"), PageTranslationLanguages.keys)
    }

    @Test fun twentyPageLabelsAndDuplicatesUseOneRequest() = runBlocking {
        MockWebServer().use { server ->
            server.echoPageTranslations()
            val repo = repository(server)
            repo.clearCache()
            val texts = (1..20).map { "标题$it" }
            val result = repo.translateBatch(texts + texts.take(5), "zh-CN")
            assertNull(result.failure)
            assertEquals(texts.associateWith { "译文：$it" }, result.translations)
            assertEquals(texts, server.takeRequest().pageTranslationTexts())
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun overlappingBatchesAndReopenedRepositoryUseCache() = runBlocking {
        MockWebServer().use { server ->
            server.echoPageTranslations()
            val repo = repository(server)
            repo.clearCache()
            val first = async { repo.translateBatch(listOf("耳かき", "眠り"), "zh-CN") }
            val second = async { repo.translateBatch(listOf("耳かき", "眠り"), "zh-CN") }
            assertEquals(first.await(), second.await())
            assertEquals(first.await(), repository(server).translateBatch(listOf("耳かき", "眠り"), "zh-CN"))
            assertEquals(1, server.requestCount)
            repo.translateBatch(listOf("耳かき", "眠り", "囁き"), "zh-CN")
            server.takeRequest()
            assertEquals(listOf("囁き"), server.takeRequest().pageTranslationTexts())
            assertEquals(2, server.requestCount)
        }
    }

    @Test fun cacheSeparatesTargetsAndClearInvalidatesMemoryAndDisk() = runBlocking {
        MockWebServer().use { server ->
            val repo = repository(server)
            repo.clearCache()
            for ((target, output) in listOf("zh-CN" to "甲", "ru" to "Привет")) {
                server.enqueue(MockResponse().setBody("[\"$output\"]"))
                assertEquals(output, repo.translateBatch(listOf("test"), target).translations["test"])
            }
            assertEquals("甲", repo.translateBatch(listOf("test"), "zh-CN").translations["test"])
            assertEquals("Привет", repository(server).translateBatch(listOf("test"), "ru").translations["test"])
            assertEquals(2, server.requestCount)
            repo.clearCache()
            assertNull(repo.cached("test", "zh-CN"))
            server.enqueue(MockResponse().setBody("[\"新译文\"]"))
            assertEquals("新译文", repo.translateBatch(listOf("test"), "zh-CN").translations["test"])
            assertEquals(3, server.requestCount)
        }
    }

    @Test fun workCodesAndEmptyPagesNeverUseNetwork() = runBlocking {
        MockWebServer().use { server ->
            val repo = repository(server)
            assertEquals(mapOf("RJ123456" to "RJ123456"), repo.translateBatch(listOf("RJ123456"), "zh-CN").translations)
            assertEquals(emptyMap<String, String>(), repo.translateBatch(emptyList(), "zh-CN").translations)
            assertEquals(0, server.requestCount)
        }
    }

    @Test fun ignoresLegacyFixedSourceCacheAndReusesAutoDetectionCache() = runBlocking {
        MockWebServer().use { server ->
            val repo = repository(server)
            repo.clearCache()
            context.openOrCreateDatabase("page_translation_cache.db", android.content.Context.MODE_PRIVATE, null).use { db ->
                db.execSQL("INSERT INTO translations VALUES (?, ?, ?, ?)", arrayOf("耳かき", "ja:zh-CN", "旧的指定源语言译文", 1L))
                db.execSQL("INSERT INTO translations VALUES (?, ?, ?, ?)", arrayOf("眠り", "auto:zh-CN", "睡眠", 1L))
            }
            server.enqueue(MockResponse().setBody("""[["掏耳","ja"]]"""))
            assertEquals(mapOf("耳かき" to "掏耳", "眠り" to "睡眠"),
                repo.translateBatch(listOf("耳かき", "眠り"), "zh-CN").translations)
            val request = server.takeRequest()
            assertEquals("auto", request.requestUrl!!.queryParameter("sl"))
            assertEquals(listOf("耳かき"), request.pageTranslationTexts())
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun oversizedPageSplitsIntoBoundedRequests() = runBlocking {
        MockWebServer().use { server ->
            server.echoPageTranslations()
            val repo = repository(server)
            repo.clearCache()
            val texts = (1..51).map { "目录$it" }
            val result = repo.translateBatch(texts, "zh-CN")
            assertNull(result.failure)
            assertEquals(51, result.translations.size)
            assertEquals(50, server.takeRequest().pageTranslationTexts().size)
            assertEquals(1, server.takeRequest().pageTranslationTexts().size)
            assertEquals(2, server.requestCount)
        }
    }

    @Test fun malformedBatchNeverPoisonsCacheOrFallsBackToSingleRequests() = runBlocking {
        MockWebServer().use { server ->
            val repo = repository(server)
            repo.clearCache()
            server.enqueue(MockResponse().setBody("[\"只有一条\"]"))
            val result = repo.translateBatch(listOf("耳かき", "眠り"), "zh-CN")
            assertNotNull(result.failure)
            assertTrue(result.translations.isEmpty())
            assertNull(repo.cached("耳かき", "zh-CN"))
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun laterBatchFailureRetainsCompletedBatchAndStopsFurtherRequests() = runBlocking {
        MockWebServer().use { server ->
            val repo = repository(server)
            repo.clearCache()
            val first = "日".repeat(2000)
            val second = "文".repeat(2000)
            server.enqueue(MockResponse().setBody("[\"甲\",\"乙\"]"))
            server.enqueue(MockResponse().setResponseCode(429))
            val result = repo.translateBatch(listOf(first, second, "新目录"), "zh-CN")
            assertNotNull(result.failure)
            assertEquals(mapOf(first to "甲", second to "乙"), result.translations)
            val duringCooldown = repo.translateBatch(listOf(first, "另一个目录"), "zh-CN")
            assertNotNull(duringCooldown.failure)
            assertEquals(mapOf(first to "甲"), duringCooldown.translations)
            assertEquals(2, server.requestCount)
        }
    }
}
