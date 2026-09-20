package com.asmr.player.translation

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test

class PageTranslationClientTest {
    @Test fun preservesBatchBoundariesAndMultilineText() {
        assertEquals(listOf("你好。\n晚安。", "社团"), parsePageTranslationBatch("""["你好。\n晚安。","社团"]""", 2))
        assertEquals(listOf("你好", "晚安"), parsePageTranslationBatch("""[["你好","ja"],["晚安","ja"]]""", 2))
    }

    @Test fun rejectsEmptyMalformedAndHtmlResponses() {
        listOf("", "<html>blocked</html>", "[]", "[[]]", "[null]", "[123]", "[\"\"]", "[\"多\",\"一项\"]").forEach {
            assertThrows(PageTranslationException::class.java) { parsePageTranslationBatch(it, 1) }
        }
    }

    @Test fun fileSuffixIsPreservedWithoutChangingTheOriginalName() {
        val original = "01.耳かき.FLAC"
        assertEquals(PageTranslationText("01.耳かき", ".FLAC"), pageTranslationText(original, true))
        assertEquals(PageTranslationText(original), pageTranslationText(original, false))
        assertEquals(PageTranslationText("ver.2"), pageTranslationText("ver.2", true))
        assertEquals(PageTranslationText("耳かき", ".srt"), pageTranslationText("耳かき.srt", true))
    }

    @Test fun skipsIdentifiersPunctuationAndOversizedInputs() {
        listOf("", "123", "...", "RJ12345678", "x".repeat(4001)).forEach { assertFalse(shouldTranslatePageText(it)) }
        listOf("耳かき", "耳朵", "Привет", "안녕하세요", "ASMR", "Hello").forEach { assertTrue(shouldTranslatePageText(it)) }
    }

    @Test fun sendsMultipleTextsInOnePostWithAutoDetectionAndSelectedTarget() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""["你好","晚安"]"""))
            val client = PageTranslationClient(OkHttpClient(), server.url("/translate").toString())
            val inputs = listOf("こんにちは & #+\n世界", "おやすみ")
            assertEquals(listOf("你好", "晚安"), client.translateBatch(inputs, "zh-CN"))
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("auto", request.requestUrl!!.queryParameter("sl"))
            assertEquals("zh-CN", request.requestUrl!!.queryParameter("tl"))
            assertNull(request.requestUrl!!.queryParameter("q"))
            assertEquals(inputs, request.pageTranslationTexts())
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun splitsOnlyAtItemAndCharacterLimitsWithoutDroppingTexts() {
        val byCount = (1..51).map { "标题$it" }
        assertEquals(listOf(50, 1), pageTranslationBatches(byCount).map { it.size })
        val bySize = listOf("日".repeat(2000), "文".repeat(2000), "余")
        assertEquals(listOf(2, 1), pageTranslationBatches(bySize).map { it.size })
        assertEquals(bySize, pageTranslationBatches(bySize).flatten())
        assertEquals(emptyList<List<String>>(), pageTranslationBatches(emptyList()))
    }

    @Test fun autoDetectTranslatesMixedLanguagesInOneBatch() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""[["你好","ja"],["晚安","en"],["晚上好","ru"],["你好","ko"]]"""))
            val client = PageTranslationClient(OkHttpClient(), server.url("/").toString())
            val inputs = listOf("こんにちは", "Good night", "Добрый вечер", "안녕하세요")
            assertEquals(listOf("你好", "晚安", "晚上好", "你好"), client.translateBatch(inputs, "zh-CN"))
            val request = server.takeRequest()
            assertEquals("auto", request.requestUrl!!.queryParameter("sl"))
            assertEquals(inputs, request.pageTranslationTexts())
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun rateLimitsAreErrorsRatherThanTranslations() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(429).setBody("Too many requests"))
            val client = PageTranslationClient(OkHttpClient(), server.url("/").toString())
            val error = runCatching { client.translateBatch(listOf("耳かき"), "zh-CN") }.exceptionOrNull()
            assertTrue(error is PageTranslationException)
            assertEquals(60_000L, (error as PageTranslationException).cooldownMillis)
        }
    }

    @Test fun networkTrafficBlocksHaveSpecificGuidanceAndLongerCooldown() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(429).setBody(
                "<html>Our systems have detected unusual traffic from your computer network.</html>"
            ))
            val client = PageTranslationClient(OkHttpClient(), server.url("/").toString())
            val error = runCatching { client.translateBatch(listOf("耳かき"), "zh-CN") }.exceptionOrNull() as PageTranslationException
            assertTrue(error.message!!.contains("当前网络异常流量"))
            assertEquals(300_000L, error.cooldownMillis)
        }
    }

    @Test fun leavingPageCancelsTheHttpCall() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val http = OkHttpClient()
            val client = PageTranslationClient(http, server.url("/").toString())
            val task = async(Dispatchers.Default) { client.translateBatch(listOf("耳かき"), "zh-CN") }
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            task.cancelAndJoin()
            assertTrue(task.isCancelled)
            assertTrue(http.dispatcher.runningCalls().all { it.isCanceled() })
        }
    }
}
