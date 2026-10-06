package com.asmr.player.subtitle

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test

class TranslationModelsClientTest {
    @Test
    fun endpoint_preservesGatewayPathAndAcceptsCompleteChatUrl() {
        mapOf(
            "https://example.com" to "https://example.com/models",
            " https://example.com/v1/ " to "https://example.com/v1/models",
            "https://example.com/gateway/v1" to "https://example.com/gateway/v1/models",
            "https://example.com/gateway/v1/chat/completions/" to "https://example.com/gateway/v1/models"
        ).forEach { (input, expected) -> assertEquals(expected, translationModelsUrl(input)) }
        assertThrows(IllegalArgumentException::class.java) {
            translationModelsUrl("https://example.com/v1?key=secret")
        }
    }

    @Test
    fun list_usesBearerKeyAndReturnsSortedUniqueModelIds() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"data":[{"id":"z-model"},{"id":"a/model"},{"id":"z-model"},{"id":""},{"id":42},{}]}"""))
            val models = TranslationModelsClient(OkHttpClient()).loadModels(
                server.url("/gateway/v1/chat/completions").toString(), " custom-key "
            )
            assertEquals(listOf("a/model", "z-model"), models)
            val request = requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))
            assertEquals("GET", request.method)
            assertEquals("/gateway/v1/models", request.path)
            assertEquals("Bearer custom-key", request.getHeader("Authorization"))
            assertEquals(0L, request.bodySize)
        }
    }

    @Test
    fun unsupportedEndpoint_reportsManualEntryAndDoesNotRetry() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(404).setBody("secret-key"))
            try {
                TranslationModelsClient(OkHttpClient()).loadModels(server.url("/v1").toString(), "key")
                fail("不支持列表时应返回错误")
            } catch (error: IOException) {
                assertTrue(error.message.orEmpty().contains("手动输入"))
                assertFalse(error.message.orEmpty().contains("secret-key"))
            }
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun cancellation_cancelsPendingHttpCall() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val cancelled = CountDownLatch(1)
            val http = OkHttpClient.Builder().eventListener(object : EventListener() {
                override fun canceled(call: Call) { cancelled.countDown() }
            }).build()
            val job = launch(Dispatchers.Default) {
                TranslationModelsClient(http).loadModels(server.url("/v1").toString(), "key")
            }
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            job.cancelAndJoin()
            assertTrue(cancelled.await(1, TimeUnit.SECONDS))
        }
    }

    @Test
    fun emptyAndMalformedLists_areRejected() {
        listOf("{}", "null", "[]", "not json", """{"data":[]}""", """{"data":[{"id":" "}]}""")
            .forEach { raw -> assertThrows(IOException::class.java) { parseTranslationModels(raw) } }
    }
}
