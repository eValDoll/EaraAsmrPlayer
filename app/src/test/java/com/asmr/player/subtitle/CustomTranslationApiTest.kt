package com.asmr.player.subtitle

import com.asmr.player.data.settings.DeepSeekTranslationSettings
import com.google.gson.Gson
import com.google.gson.JsonParser
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class CustomTranslationApiTest {
    private val gson = Gson()

    @Test
    fun endpoints_acceptBasePathsAndCompleteChatUrlsWithoutDuplicatingSuffix() {
        mapOf(
            "https://api.example.com" to "https://api.example.com/chat/completions",
            " https://api.example.com/v1/ " to "https://api.example.com/v1/chat/completions",
            "https://api.example.com/openai/v1" to "https://api.example.com/openai/v1/chat/completions",
            "https://api.example.com/v1/chat/completions/" to "https://api.example.com/v1/chat/completions",
            "http://127.0.0.1:8080/v1" to "http://127.0.0.1:8080/v1/chat/completions"
        ).forEach { (input, expected) -> assertEquals(expected, translationChatCompletionsUrl(input)) }
        listOf("", "example.com", "file:///tmp/api", "https://", "https://u:p@example.com/v1",
            "https://example.com/v1?key=secret", "https://example.com/v1#fragment").forEach { input ->
            assertThrows(IllegalArgumentException::class.java) { translationChatCompletionsUrl(input) }
        }
    }

    @Test
    fun defaultProvider_preservesDeepSeekEndpointModelAndThinking() {
        val config = TranslationApiConfig(apiKey = "legacy-key")
        assertEquals(TranslationProvider.DEEPSEEK, config.provider)
        assertEquals("https://api.deepseek.com/chat/completions", config.completionsUrl)
        val request = JsonParser.parseString(buildDeepSeekSubtitleAgentRequest(
            gson, listOf(DeepSeekChatMessage("user", "翻译")), DeepSeekTranslationSettings(thinkingEnabled = true)
        )).asJsonObject
        assertEquals(DEEPSEEK_SUBTITLE_MODEL, request["model"].asString)
        assertEquals("enabled", request["thinking"].asJsonObject["type"].asString)
        assertEquals(32_768, request["max_tokens"].asInt)
    }

    @Test
    fun allCustomRequests_useConfiguredModelAndOmitVendorSpecificFields() {
        val config = TranslationApiConfig(TranslationProvider.CUSTOM, "custom-key", "https://example.com/v1", "vendor/model", 65_536)
        val messages = listOf(DeepSeekChatMessage("assistant", "", reasoningContent = "私有思考字段"))
        val settings = DeepSeekTranslationSettings(thinkingEnabled = true)
        val requests = listOf(
            buildDeepSeekTitleTranslationRequest(gson, "作品", "社团", "声优", listOf(1L to "音轨"), settings, config),
            buildDeepSeekSubtitleAgentRequest(gson, messages, settings, apiConfig = config),
            buildPolishAgentRequest(gson, messages, settings, config)
        ).map { JsonParser.parseString(it).asJsonObject }
        requests.forEach { request ->
            assertEquals("vendor/model", request["model"].asString)
            assertEquals(65_536, request["max_tokens"].asInt)
            listOf("thinking", "reasoning_effort", "max_completion_tokens", "response_format").forEach { assertFalse(request.has(it)) }
            request["messages"].asJsonArray.forEach { assertFalse(it.asJsonObject.has("reasoning_content")) }
        }
        assertTrue(requests[1]["tools"].asJsonArray.size() >= 2)
        assertTrue(requests[2]["tools"].asJsonArray.size() >= 2)
    }

    @Test
    fun customOutputLimit_defaultsToAnExplicitValueAndRejectsNonPositiveValues() {
        val config = TranslationApiConfig(TranslationProvider.CUSTOM, "key", "https://example.com/v1", "model")
        val request = JsonParser.parseString(buildDeepSeekSubtitleAgentRequest(
            gson, listOf(DeepSeekChatMessage("user", "翻译")), apiConfig = config
        )).asJsonObject
        assertEquals(32_768, request["max_tokens"].asInt)
        listOf(0, -1).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) {
                TranslationApiConfig(apiKey = "key", maxOutputTokens = value)
            }
        }
    }

    @Test
    fun customThinkingChoices_applyToEveryRequestAndOverrideDeepSeekSettings() {
        CustomThinkingMode.entries.forEach { mode ->
            CustomReasoningEffort.entries.forEach { effort ->
                val config = TranslationApiConfig(TranslationProvider.CUSTOM, "key", "https://example.com/v1", "model",
                    65_536, mode, effort)
                val settings = DeepSeekTranslationSettings(thinkingEnabled = mode != CustomThinkingMode.ENABLED)
                val bodies = listOf(
                    buildDeepSeekTitleTranslationRequest(gson, "作品", "", "", listOf(1L to "音轨"), settings, config),
                    buildDeepSeekSubtitleAgentRequest(gson, listOf(DeepSeekChatMessage("user", "翻译")), settings, apiConfig = config),
                    buildPolishAgentRequest(gson, listOf(DeepSeekChatMessage("user", "润色")), settings, config)
                ).map { JsonParser.parseString(it).asJsonObject }
                bodies.forEach { body ->
                    assertFalse(body.has("thinking"))
                    assertEquals(65_536, body["max_tokens"].asInt)
                    when (mode) {
                        CustomThinkingMode.FOLLOW_SERVER -> assertFalse(body.has("reasoning_effort"))
                        CustomThinkingMode.DISABLED -> assertEquals("none", body["reasoning_effort"].asString)
                        CustomThinkingMode.ENABLED -> assertEquals(effort.wireValue, body["reasoning_effort"].asString)
                    }
                }
            }
        }
    }

    @Test
    fun unsupportedMaxTokens_switchesParameterOnceAndPreservesLimitAndRequest() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(400).setBody(
                """{"error":{"message":"Unsupported parameter: 'max_tokens'. Use 'max_completion_tokens' instead.","param":"max_tokens","code":"unsupported_parameter"}}"""
            ))
            val content = gson.toJson(mapOf("work_title" to "作品", "tracks" to listOf(mapOf("track_id" to 1, "title" to "音轨"))))
            repeat(2) { server.enqueue(chatResponse(mapOf("role" to "assistant", "content" to content))) }
            val config = TranslationApiConfig(TranslationProvider.CUSTOM, "key", server.url("/v1").toString(), "model",
                65_536, CustomThinkingMode.ENABLED, CustomReasoningEffort.HIGH)
            val client = SubtitleTranslationClient(OkHttpClient(), gson, config.apiKey, apiConfig = config)
            repeat(2) { client.translateDisplayNames("作品", "", "", listOf(1L to "音轨"), maxAttempts = 1) }
            val bodies = (0 until 3).map {
                JsonParser.parseString(requireNotNull(server.takeRequest(1, TimeUnit.SECONDS)).body.readUtf8()).asJsonObject
            }
            assertEquals(65_536, bodies[0]["max_tokens"].asInt)
            bodies.drop(1).forEach { body ->
                assertFalse(body.has("max_tokens"))
                assertEquals(65_536, body["max_completion_tokens"].asInt)
                assertEquals(bodies[0]["messages"], body["messages"])
                assertEquals(bodies[0]["model"], body["model"])
                assertEquals("high", body["reasoning_effort"].asString)
            }
            assertEquals(3, server.requestCount)
        }
    }

    @Test
    fun unsupportedReasoningEffort_isNotSilentlyOmitted() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(400).setBody(
                """{"error":{"message":"Unsupported value: reasoning_effort none","param":"reasoning_effort","code":"unsupported_value"}}"""
            ))
            val config = TranslationApiConfig(TranslationProvider.CUSTOM, "key", server.url("/v1").toString(), "model",
                thinkingMode = CustomThinkingMode.DISABLED)
            val client = SubtitleTranslationClient(OkHttpClient(), gson, config.apiKey, apiConfig = config)
            try {
                client.translateDisplayNames("作品", "", "", listOf(1L to "音轨"), maxAttempts = 1)
                fail("思考设置被拒绝时应返回错误")
            } catch (error: SubtitleTranslationException) {
                assertFalse(error.retryable)
                assertTrue(error.message.orEmpty().contains("reasoning_effort"))
            }
            val request = requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))
            assertEquals("none", JsonParser.parseString(request.body.readUtf8()).asJsonObject["reasoning_effort"].asString)
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun rejectedOutputLimit_doesNotLowerOrRemoveTheConfiguredLimit() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(400).setBody(
                """{"error":{"message":"max_tokens must be at most 8192","param":"max_tokens","code":"invalid_value"}}"""
            ))
            val config = TranslationApiConfig(TranslationProvider.CUSTOM, "key", server.url("/v1").toString(), "model", 65_536)
            val client = SubtitleTranslationClient(OkHttpClient(), gson, config.apiKey, apiConfig = config)
            try {
                client.translateDisplayNames("作品", "", "", listOf(1L to "音轨"), maxAttempts = 1)
                fail("输出上限被拒绝时应返回错误")
            } catch (error: SubtitleTranslationException) {
                assertFalse(error.retryable)
                assertTrue(error.message.orEmpty().contains("8192"))
            }
            val request = requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))
            assertEquals(65_536, JsonParser.parseString(request.body.readUtf8()).asJsonObject["max_tokens"].asInt)
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun rejectedParameterSwitch_stopsAfterOneCompatibilityRetry() = runBlocking {
        MockWebServer().use { server ->
            repeat(2) {
                server.enqueue(MockResponse().setResponseCode(400).setBody(
                    """{"error":{"message":"max_tokens is not supported; use max_completion_tokens instead","param":"max_tokens","code":"unsupported_parameter"}}"""
                ))
            }
            val config = TranslationApiConfig(TranslationProvider.CUSTOM, "key", server.url("/v1").toString(), "model", 65_536)
            val client = SubtitleTranslationClient(OkHttpClient(), gson, config.apiKey, apiConfig = config)
            try {
                client.translateDisplayNames("作品", "", "", listOf(1L to "音轨"), maxAttempts = 1)
                fail("两种参数均被拒绝时应停止")
            } catch (error: SubtitleTranslationException) {
                assertFalse(error.retryable)
            }
            assertEquals(2, server.requestCount)
            server.takeRequest(1, TimeUnit.SECONDS)
            val body = JsonParser.parseString(requireNotNull(server.takeRequest(1, TimeUnit.SECONDS)).body.readUtf8()).asJsonObject
            assertEquals(65_536, body["max_completion_tokens"].asInt)
            assertFalse(body.has("max_tokens"))
        }
    }

    @Test
    fun customTitleTranslation_sendsOnlyCustomCredentialsToConfiguredEndpoint() = runBlocking {
        MockWebServer().use { server ->
            val content = gson.toJson(mapOf("work_title" to "翻译作品", "tracks" to listOf(mapOf("track_id" to 1, "title" to "翻译音轨"))))
            server.enqueue(chatResponse(mapOf("role" to "assistant", "content" to content)))
            val config = TranslationApiConfig(TranslationProvider.CUSTOM, "custom-secret", server.url("/gateway/v1").toString(), "third-party-model")
            val client = SubtitleTranslationClient(OkHttpClient(), gson, config.apiKey, apiConfig = config)
            val result = client.translateDisplayNames("作品", "社团", "声优", listOf(1L to "音轨"), maxAttempts = 1)
            assertEquals("翻译作品", result.albumTitle)
            assertEquals("翻译音轨", result.trackTitles[1L])
            val request = requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))
            assertEquals("/gateway/v1/chat/completions", request.path)
            assertEquals("Bearer custom-secret", request.getHeader("Authorization"))
            val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
            assertEquals("third-party-model", body["model"].asString)
            assertEquals(32_768, body["max_tokens"].asInt)
        }
    }

    @Test
    fun customSubtitleTranslation_preservesToolRoundTrips() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(toolResponse("read", SUBTITLE_READ_TOOL_NAME, "{}"))
            server.enqueue(toolResponse("write", SUBTITLE_WRITE_TOOL_NAME,
                """{"captions":[{"source_indices":[0],"start_ms":0,"end_ms":900,"japanese":"おやすみ","chinese":"晚安"}]}"""))
            val config = TranslationApiConfig(TranslationProvider.CUSTOM, "custom-key", server.url("/v1").toString(), "custom-model")
            val client = SubtitleTranslationClient(OkHttpClient(), gson, config.apiKey, apiConfig = config)
            val result = client.translateSubtitles(
                listOf(GeneratedSubtitleSource(index = 0, startMs = 0, endMs = 900, text = "おやすみ")),
                allowMerging = false, onCaptionsConfirmed = {}
            )
            assertEquals("晚安", result.single().chineseText)
            server.takeRequest(1, TimeUnit.SECONDS)
            val request = requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))
            val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
            assertEquals(32_768, body["max_tokens"].asInt)
            val messages = body["messages"].asJsonArray
            val tool = messages.first { it.asJsonObject["role"].asString == "tool" }.asJsonObject
            assertEquals("read", tool["tool_call_id"].asString)
            messages.forEach { assertFalse(it.asJsonObject.has("reasoning_content")) }
        }
    }

    @Test
    fun customFailures_nameSelectedServiceAndKeepRetryPolicy() {
        val failure = SubtitleFailureMessages.deepSeekHttp(404, null, "自定义翻译服务", custom = true)
        assertTrue(failure.message.contains("地址或模型"))
        assertFalse(failure.message.contains("DeepSeek"))
        assertFalse(failure.retryable)
        assertTrue(SubtitleFailureMessages.deepSeekHttp(429, null, "自定义翻译服务", true).retryable)
        assertFalse(SubtitleFailureMessages.network(UnknownHostException(), "自定义翻译服务").contains("DeepSeek"))
    }

    private fun toolResponse(id: String, name: String, arguments: String): MockResponse = chatResponse(mapOf(
        "role" to "assistant", "content" to "", "reasoning_content" to "服务端附加字段",
        "tool_calls" to listOf(mapOf("id" to id, "type" to "function", "function" to mapOf("name" to name, "arguments" to arguments)))
    ))

    private fun chatResponse(message: Map<String, Any>): MockResponse = MockResponse().setBody(gson.toJson(
        mapOf("choices" to listOf(mapOf("finish_reason" to "stop", "message" to message)))
    ))
}
