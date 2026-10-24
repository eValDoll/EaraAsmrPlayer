package com.asmr.player.subtitle

import android.app.Application
import com.google.gson.Gson
import com.google.gson.JsonParser
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34], manifest = Config.NONE)
class SubtitleDisplayNameTranslationTest {
    private val gson = Gson()

    @Test
    fun customTitleTranslation_retriesMissingIdsWithToolSchemaAndMapsTitlesById() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(chatResponse(mapOf("role" to "assistant", "content" to
                """{"work_title":"陪睡","tracks":[{"track_id":101,"title":"耳语"}]}""")))
            server.enqueue(toolResponse("titles", TITLE_WRITE_TOOL_NAME,
                """{"work_title":"晚安陪睡","tracks":[{"track_id":509,"title":"舔耳"},{"track_id":101,"title":"晚安耳语"}]}"""))
            val config = TranslationApiConfig(TranslationProvider.CUSTOM, "key", server.url("/v1").toString(), "model")
            val client = SubtitleTranslationClient(OkHttpClient(), gson, config.apiKey, apiConfig = config)

            val result = client.translateDisplayNames("添い寝", "社团", "声优", listOf(101L to "囁き", 509L to "耳舐め"))

            assertEquals("晚安陪睡", result.albumTitle)
            assertEquals(mapOf(101L to "晚安耳语", 509L to "舔耳"), result.trackTitles)
            assertEquals(2, server.requestCount)
            val bodies = (0 until 2).map {
                JsonParser.parseString(requireNotNull(server.takeRequest(1, TimeUnit.SECONDS)).body.readUtf8()).asJsonObject
            }
            assertFalse(bodies[0].has("tools"))
            assertFalse(bodies[0].has("tool_choice"))
            val retry = bodies[1]
            assertFalse(retry.has("tool_choice"))
            val payload = JsonParser.parseString(
                retry["messages"].asJsonArray.last().asJsonObject["content"].asString
            ).asJsonObject
            assertTrue(payload["validation_error"].asString.contains("509"))
            assertEquals(listOf(101L, 509L), payload["tracks"].asJsonArray.map { it.asJsonObject["track_id"].asLong })
            val tool = retry["tools"].asJsonArray.single().asJsonObject["function"].asJsonObject
            assertEquals(TITLE_WRITE_TOOL_NAME, tool["name"].asString)
            val parameters = tool["parameters"].asJsonObject
            assertEquals(setOf("work_title", "tracks"), parameters["required"].asJsonArray.map { it.asString }.toSet())
            val trackSchema = parameters["properties"].asJsonObject["tracks"].asJsonObject["items"].asJsonObject
            assertEquals(setOf("track_id", "title"), trackSchema["required"].asJsonArray.map { it.asString }.toSet())
        }
    }

    @Test
    fun customTitleTranslation_acceptsContentJsonWhenToolFallbackIsAvailable() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(chatResponse(mapOf("role" to "assistant", "content" to "翻译已经完成。")))
            server.enqueue(chatResponse(mapOf("role" to "assistant", "content" to
                """{"work_title":"陪睡","tracks":[{"track_id":101,"title":"耳语"},{"track_id":509,"title":"舔耳"}]}""")))
            val config = TranslationApiConfig(TranslationProvider.CUSTOM, "key", server.url("/v1").toString(), "model")
            val client = SubtitleTranslationClient(OkHttpClient(), gson, config.apiKey, apiConfig = config)

            val result = client.translateDisplayNames("添い寝", "", "", listOf(101L to "囁き", 509L to "耳舐め"))

            assertEquals("陪睡", result.albumTitle)
            assertEquals(mapOf(101L to "耳语", 509L to "舔耳"), result.trackTitles)
            assertEquals(2, server.requestCount)
            server.takeRequest(1, TimeUnit.SECONDS)
            val retry = JsonParser.parseString(requireNotNull(server.takeRequest(1, TimeUnit.SECONDS)).body.readUtf8()).asJsonObject
            assertEquals(TITLE_WRITE_TOOL_NAME, retry["tools"].asJsonArray.single().asJsonObject["function"].asJsonObject["name"].asString)
            assertFalse(retry.has("tool_choice"))
        }
    }

    @Test
    fun customTitleTranslation_networkRetryKeepsContentJsonWithoutToolsOrValidationError() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503).setBody(
                """{"error":{"message":"Service temporarily unavailable"}}"""
            ))
            server.enqueue(chatResponse(mapOf("role" to "assistant", "content" to
                """{"work_title":"陪睡","tracks":[{"track_id":101,"title":"耳语"},{"track_id":509,"title":"舔耳"}]}""")))
            val config = TranslationApiConfig(TranslationProvider.CUSTOM, "key", server.url("/v1").toString(), "model")
            val client = SubtitleTranslationClient(OkHttpClient(), gson, config.apiKey, apiConfig = config)

            val result = client.translateDisplayNames("添い寝", "", "", listOf(101L to "囁き", 509L to "耳舐め"))

            assertEquals(mapOf(101L to "耳语", 509L to "舔耳"), result.trackTitles)
            assertEquals(2, server.requestCount)
            val bodies = (0 until 2).map {
                JsonParser.parseString(requireNotNull(server.takeRequest(1, TimeUnit.SECONDS)).body.readUtf8()).asJsonObject
            }
            bodies.forEach { body ->
                assertFalse(body.has("tools"))
                assertFalse(body.has("tool_choice"))
                val payload = JsonParser.parseString(
                    body["messages"].asJsonArray.last().asJsonObject["content"].asString
                ).asJsonObject
                assertFalse(payload.has("validation_error"))
            }
            assertEquals(bodies[0]["messages"], bodies[1]["messages"])
        }
    }

    @Test
    fun customTitleTranslation_stopsAfterTwoInvalidResponsesWithoutMatchingTitlesByPosition() = runBlocking {
        listOf(
            "",
            """{"work_title":"陪睡","tracks":[{"track_id":0,"title":"错误耳语"},{"track_id":1,"title":"错误舔耳"}]}"""
        ).forEach { arguments ->
            MockWebServer().use { server ->
                server.enqueue(chatResponse(mapOf("role" to "assistant", "content" to
                    """{"work_title":"陪睡","tracks":[{"track_id":101,"title":"耳语"}]}""")))
                server.enqueue(toolResponse("titles", TITLE_WRITE_TOOL_NAME, arguments))
                server.enqueue(chatResponse(mapOf("role" to "assistant", "content" to
                    """{"work_title":"不应请求","tracks":[{"track_id":101,"title":"耳语"},{"track_id":509,"title":"舔耳"}]}""")))
                val config = TranslationApiConfig(TranslationProvider.CUSTOM, "key", server.url("/v1").toString(), "model")
                val client = SubtitleTranslationClient(OkHttpClient(), gson, config.apiKey, apiConfig = config)

                try {
                    client.translateDisplayNames("添い寝", "", "", listOf(101L to "囁き", 509L to "耳舐め"))
                    fail("无效音轨标题不能按列表位置套用或触发第三次请求")
                } catch (error: SubtitleTranslationException) {
                    assertNotNull(error.cause)
                }
                assertEquals(2, server.requestCount)
            }
        }
    }

    @Test
    fun displayNameRetry_reportsMissingIdsWithoutChangingDeepSeekProtocol() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(chatResponse(mapOf("role" to "assistant", "content" to
                """{"work_title":"晚安","tracks":[{"track_id":13,"title":"耳语"}]}""")))
            server.enqueue(chatResponse(mapOf("role" to "assistant", "content" to
                """{"work_title":"晚安","tracks":[{"track_id":59,"title":"陪睡"},{"track_id":13,"title":"耳语"}]}""")))
            val client = SubtitleTranslationClient(OkHttpClient(), gson, "test-key", apiUrl = server.url("/chat").toString())

            val result = client.translateDisplayNames("おやすみ", "", "", listOf(13L to "囁き", 59L to "添い寝"))

            assertEquals(mapOf(13L to "耳语", 59L to "陪睡"), result.trackTitles)
            assertEquals(2, server.requestCount)
            val requests = (0 until 2).map {
                JsonParser.parseString(requireNotNull(server.takeRequest(1, TimeUnit.SECONDS)).body.readUtf8()).asJsonObject
            }
            requests.forEach { request ->
                assertFalse(request.has("tools"))
                assertEquals("json_object", request.getAsJsonObject("response_format")["type"].asString)
            }
            val retryPayload = JsonParser.parseString(requests[1].getAsJsonArray("messages")[1]
                .asJsonObject["content"].asString).asJsonObject
            assertTrue(retryPayload["validation_error"].asString.contains("59"))
        }
    }

    private fun toolResponse(id: String, name: String, arguments: String): MockResponse = chatResponse(mapOf(
        "role" to "assistant", "content" to "", "reasoning_content" to "服务端附加字段",
        "tool_calls" to listOf(mapOf("id" to id, "type" to "function", "function" to mapOf("name" to name, "arguments" to arguments)))
    ))

    private fun chatResponse(message: Map<String, Any>): MockResponse = MockResponse().setBody(gson.toJson(
        mapOf("choices" to listOf(mapOf("finish_reason" to "stop", "message" to message)))
    ))
}
