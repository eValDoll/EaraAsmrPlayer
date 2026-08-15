package com.asmr.player.subtitle

import com.asmr.player.data.settings.DeepSeekReasoningEffort
import com.asmr.player.data.settings.DeepSeekTranslationSettings
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SubtitleTranslationClientTest {
    @Test
    fun retryAfter_acceptsSecondsAndHttpDate() {
        assertEquals(4_000L, parseRetryAfterMillis("4", nowMs = 0L))
        assertEquals(
            5_000L,
            parseRetryAfterMillis("Thu, 01 Jan 1970 00:00:10 GMT", nowMs = 5_000L)
        )
        assertNull(parseRetryAfterMillis("稍后重试", nowMs = 0L))
    }

    @Test
    fun deepSeekError_extractsServiceMessage() {
        assertEquals(
            "Thinking mode does not support this tool_choice",
            parseDeepSeekErrorMessage(
                """{"error":{"message":"Thinking mode does not support this tool_choice"}}"""
            )
        )
        assertNull(parseDeepSeekErrorMessage("not-json"))
    }

    @Test
    fun prompt_allowsOnDemandReadAndWriteToolCalls() {
        val prompt = subtitleToolTranslationSystemPrompt(listOf(0, 1, 2), allowMerging = true)
        val noMerge = subtitleToolTranslationSystemPrompt(listOf(0, 1, 2), allowMerging = false)

        assert(prompt.contains(SUBTITLE_READ_TOOL_NAME))
        assert(prompt.contains(SUBTITLE_WRITE_TOOL_NAME))
        assert(prompt.contains("next_untranslated_index"))
        assert(prompt.contains("source_indices"))
        assert(prompt.contains("start_ms"))
        assert(prompt.contains("end_ms"))
        assert(prompt.contains("japanese"))
        assert(prompt.contains("chinese"))
        assert(prompt.contains("3"))
        assert(prompt != noMerge)
        assert(!prompt.contains("{{"))
        assert(prompt.length > 1_000)
    }

    @Test
    fun prompt_injectsStaticWorkContextAndKeepsNameConsistencyRule() {
        val context = SubtitleWorkContext(
            workTitleJapanese = "架空のサンプル作品タイトル",
            workTitleChinese = "虚构的示例作品标题",
            trackTitleJapanese = "サンプルトラック",
            trackTitleChinese = "示例音轨",
            circle = "サンプルサークル",
            cv = "サンプル声優"
        )
        val prompt = subtitleToolTranslationSystemPrompt(
            listOf(0, 1, 2),
            allowMerging = true,
            workContext = context
        )
        val withoutContext = subtitleToolTranslationSystemPrompt(listOf(0, 1, 2), allowMerging = true)

        assert(prompt.contains("作品标题（日文）：架空のサンプル作品タイトル"))
        assert(prompt.contains("作品标题（中文）：虚构的示例作品标题"))
        assert(prompt.contains("当前音轨标题（日文）：サンプルトラック"))
        assert(prompt.contains("当前音轨标题（中文）：示例音轨"))
        assert(prompt.contains("社团：サンプルサークル"))
        assert(prompt.contains("声优：サンプル声優"))
        assert(prompt.contains("同一角色在整轨及跨轨使用同一中文译名"))
        assert(withoutContext.contains("作品上下文：无"))
        assert(!withoutContext.contains("作品标题（日文）"))
    }

    @Test
    fun workContextSection_fallsBackToNoContextMessage() {
        val section = buildSubtitleWorkContextSection(null)
        assert(section.contains("作品上下文：无"))
        val empty = buildSubtitleWorkContextSection(SubtitleWorkContext())
        assert(empty.contains("作品上下文（用于统一人名"))
        assert(!empty.contains("作品标题（日文）"))
    }

    @Test
    fun displayNameParser_acceptsUnchangedJapaneseProperNames() {
        val result = parseDisplayNameTranslationResponse(
            content = """{"work_title":"Omega01","tracks":[{"track_id":13,"title":"ASMRパート"}]}""",
            expectedTrackIds = listOf(13L)
        )

        assertEquals("Omega01", result.albumTitle)
        assertEquals("ASMRパート", result.trackTitles.getValue(13L))
    }

    @Test
    fun successfulResponse_reportsTotalTokenUsage() = runBlocking {
        val responseBody = Gson().toJson(
            mapOf(
                "choices" to listOf(
                    mapOf(
                        "finish_reason" to "stop",
                        "message" to mapOf(
                            "role" to "assistant",
                            "content" to """{"work_title":"晚安","tracks":[{"track_id":13,"title":"耳语"}]}"""
                        )
                    )
                ),
                "usage" to mapOf("total_tokens" to 1_234L)
            )
        )
        val httpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(responseBody.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()
        val tokenUsage = mutableListOf<Long>()
        val client = SubtitleTranslationClient(
            okHttpClient = httpClient,
            gson = Gson(),
            apiKey = "test-key",
            apiUrl = "https://example.test/chat",
            onTokenUsage = tokenUsage::add
        )

        client.translateDisplayNames(
            albumTitle = "おやすみ",
            circle = "",
            cv = "",
            trackTitles = listOf(13L to "囁き")
        )

        assertEquals(listOf(1_234L), tokenUsage)
    }

    @Test
    fun manualPrompt_forbidsMergingImportedSubtitleLines() {
        val prompt = subtitleToolTranslationSystemPrompt(listOf(0, 2), allowMerging = false)

        assert(prompt.contains("source_indices"))
        assert(prompt != subtitleToolTranslationSystemPrompt(listOf(0, 2), allowMerging = true))
        assert(prompt.isNotBlank())
    }

    @Test
    fun request_exposesReadAndWriteToolsWithoutForcingToolChoice() {
        val sources = sources(3)
        val confirmed = listOf(
            GeneratedSubtitleCaption(listOf(0), 0L, 900L, "おやすみ", "晚安")
        )
        val root = JsonParser.parseString(
            buildDeepSeekSubtitleTranslationRequest(
                gson = Gson(),
                sources = sources,
                allowMerging = true,
                confirmedCaptions = confirmed
            )
        ).asJsonObject
        val messages = root.getAsJsonArray("messages")
        val payload = JsonParser.parseString(messages[1].asJsonObject.get("content").asString).asJsonObject
        val tools = root.getAsJsonArray("tools")
        val writeTool = tools.first { element ->
            element.asJsonObject.getAsJsonObject("function").get("name").asString == SUBTITLE_WRITE_TOOL_NAME
        }.asJsonObject
        val toolParameters = writeTool
            .getAsJsonObject("function")
            .getAsJsonObject("parameters")
        val captionProperties = toolParameters.getAsJsonObject("properties")
            .getAsJsonObject("captions")
            .getAsJsonObject("items")
            .getAsJsonObject("properties")

        assertEquals(DEEPSEEK_SUBTITLE_MODEL, root.get("model").asString)
        assertEquals("disabled", root.getAsJsonObject("thinking").get("type").asString)
        assertEquals(false, root.has("reasoning_effort"))
        assertEquals(false, root.has("response_format"))
        assertEquals(false, root.has("tool_choice"))
        assertEquals("translate_current_audio_track_subtitles", payload.get("task").asString)
        assertEquals(3, payload.get("source_count").asInt)
        assertEquals(1, payload.get("completed_source_count").asInt)
        assertEquals(false, payload.has("segments"))
        assertEquals(
            listOf("source_indices", "start_ms", "end_ms", "japanese", "chinese"),
            captionProperties.keySet().toList()
        )
        assertEquals(
            listOf(SUBTITLE_READ_TOOL_NAME, SUBTITLE_WRITE_TOOL_NAME),
            tools.map { it.asJsonObject.getAsJsonObject("function").get("name").asString }
        )
    }

    @Test
    fun agentRequest_appendsAssistantReadCallAndCurrentSubtitleState() {
        val gson = Gson()
        val sources = sources(3)
        val confirmed = listOf(
            GeneratedSubtitleCaption(listOf(0), 0L, 900L, "目を閉じて", "闭上眼睛")
        )
        val messages = buildSubtitleAgentInitialMessages(
            gson = gson,
            sources = sources,
            allowMerging = false
        ).toMutableList()
        messages += DeepSeekChatMessage(
            role = "assistant",
            content = "",
            reasoningContent = "先读取当前状态。",
            toolCalls = listOf(
                DeepSeekToolCall(
                    id = "call-1",
                    function = DeepSeekToolCallFunction(
                        name = SUBTITLE_READ_TOOL_NAME,
                        arguments = "{}"
                    )
                )
            )
        )
        messages += buildSubtitleReadToolResultMessage(
            gson = gson,
            toolCallId = "call-1",
            sources = sources,
            confirmedCaptions = confirmed
        )

        val root = JsonParser.parseString(
            buildDeepSeekSubtitleAgentRequest(
                gson = gson,
                messages = messages,
                settings = DeepSeekTranslationSettings(thinkingEnabled = true)
            )
        ).asJsonObject
        val serializedMessages = root.getAsJsonArray("messages")
        val assistant = serializedMessages[2].asJsonObject
        val tool = serializedMessages[3].asJsonObject
        val toolResult = JsonParser.parseString(tool.get("content").asString).asJsonObject

        assertEquals(4, serializedMessages.size())
        assertEquals("assistant", assistant.get("role").asString)
        assertEquals("先读取当前状态。", assistant.get("reasoning_content").asString)
        assertEquals("call-1", assistant.getAsJsonArray("tool_calls")[0].asJsonObject.get("id").asString)
        assertEquals("tool", tool.get("role").asString)
        assertEquals("call-1", tool.get("tool_call_id").asString)
        assertEquals(3, toolResult.getAsJsonArray("japanese_subtitles").size())
        assertEquals(1, toolResult.getAsJsonArray("completed_chinese_subtitles").size())
        assertEquals(1, toolResult.get("completed_source_count").asInt)
        assertEquals(1, toolResult.get("next_untranslated_index").asInt)
        assert(toolResult.get("next_action").asString.isNotBlank())
    }

    @Test
    fun translateSubtitles_allowsReadAndOrdinaryTurnsBeforeWritingSubtitles() = runBlocking {
        val gson = Gson()
        val requestBodies = mutableListOf<String>()
        val responses = ArrayDeque(
            listOf(
                toolCallResponse(
                    id = "call-1",
                    name = SUBTITLE_READ_TOOL_NAME,
                    reasoning = "先读取完整字幕。",
                    arguments = "{}"
                ),
                assistantResponse(content = "我会结合完整上下文处理字幕。"),
                toolCallResponse(
                    id = "call-2",
                    name = SUBTITLE_WRITE_TOOL_NAME,
                    reasoning = "现在写入完成的中文字幕。",
                    arguments = """{"captions":[{"source_indices":[0],"start_ms":0,"end_ms":900,"japanese":"目を閉じて","chinese":"闭上眼睛"},{"source_indices":[1],"start_ms":1000,"end_ms":1900,"japanese":"おやすみ","chinese":"晚安"}]}"""
                )
            )
        )
        val httpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                requestBodies += Buffer().also { chain.request().body?.writeTo(it) }.readUtf8()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(responses.removeFirst().toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()
        val confirmedBatches = mutableListOf<List<GeneratedSubtitleCaption>>()
        val client = SubtitleTranslationClient(
            okHttpClient = httpClient,
            gson = gson,
            apiKey = "test-key",
            settings = DeepSeekTranslationSettings(thinkingEnabled = true),
            apiUrl = "https://example.test/chat"
        )

        val result = client.translateSubtitles(
            sources = sources(2),
            allowMerging = false,
            onCaptionsConfirmed = { confirmedBatches += it }
        )

        assertEquals(3, requestBodies.size)
        assertEquals(listOf("闭上眼睛", "晚安"), result.map(GeneratedSubtitleCaption::chineseText))
        assertEquals(1, confirmedBatches.size)
        val secondMessages = JsonParser.parseString(requestBodies[1]).asJsonObject.getAsJsonArray("messages")
        assertEquals(4, secondMessages.size())
        assertEquals(
            "先读取完整字幕。",
            secondMessages[2].asJsonObject.get("reasoning_content").asString
        )
        assertEquals("call-1", secondMessages[3].asJsonObject.get("tool_call_id").asString)
        val readResult = JsonParser.parseString(secondMessages[3].asJsonObject.get("content").asString).asJsonObject
        assertEquals(2, readResult.getAsJsonArray("japanese_subtitles").size())
        val thirdMessages = JsonParser.parseString(requestBodies[2]).asJsonObject.getAsJsonArray("messages")
        assertEquals("user", thirdMessages.last().asJsonObject.get("role").asString)
    }

    @Test
    fun translateSubtitles_returnsInvalidWriteAsToolErrorForAgentToRepair() = runBlocking {
        val gson = Gson()
        val requestBodies = mutableListOf<String>()
        val responses = ArrayDeque(
            listOf(
                toolCallResponse(
                    id = "bad-write",
                    name = SUBTITLE_WRITE_TOOL_NAME,
                    reasoning = "尝试写入。",
                    arguments = """{"captions":"invalid"}"""
                ),
                toolCallResponse(
                    id = "fixed-write",
                    name = SUBTITLE_WRITE_TOOL_NAME,
                    reasoning = "根据工具错误修正参数。",
                    arguments = """{"captions":[{"source_indices":[0],"start_ms":0,"end_ms":900,"japanese":"目を閉じて","chinese":"闭上眼睛"}]}"""
                )
            )
        )
        val httpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                requestBodies += Buffer().also { chain.request().body?.writeTo(it) }.readUtf8()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(responses.removeFirst().toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()
        val client = SubtitleTranslationClient(
            okHttpClient = httpClient,
            gson = gson,
            apiKey = "test-key",
            apiUrl = "https://example.test/chat"
        )

        val result = client.translateSubtitles(
            sources = sources(1),
            allowMerging = false,
            onCaptionsConfirmed = {}
        )

        assertEquals("闭上眼睛", result.single().chineseText)
        val secondMessages = JsonParser.parseString(requestBodies[1]).asJsonObject.getAsJsonArray("messages")
        val toolResult = JsonParser.parseString(secondMessages.last().asJsonObject.get("content").asString).asJsonObject
        assertEquals("error", toolResult.get("status").asString)
        assert(toolResult.get("message").asString.contains("captions 不是数组"))
    }

    @Test
    fun request_supportsThinkingMaxAndDisabledModes() {
        val source = sources(1)
        val maxRequest = JsonParser.parseString(
            buildDeepSeekSubtitleTranslationRequest(
                gson = Gson(),
                sources = source,
                allowMerging = false,
                settings = DeepSeekTranslationSettings(
                    thinkingEnabled = true,
                    reasoningEffort = DeepSeekReasoningEffort.MAX
                )
            )
        ).asJsonObject
        val disabledRequest = JsonParser.parseString(
            buildDeepSeekSubtitleTranslationRequest(
                gson = Gson(),
                sources = source,
                allowMerging = false,
                settings = DeepSeekTranslationSettings(
                    thinkingEnabled = false,
                    reasoningEffort = DeepSeekReasoningEffort.MAX
                )
            )
        ).asJsonObject

        assertEquals("max", maxRequest.get("reasoning_effort").asString)
        assertEquals("disabled", disabledRequest.getAsJsonObject("thinking").get("type").asString)
        assertEquals(false, disabledRequest.has("reasoning_effort"))
    }

    @Test
    fun toolArguments_acceptContiguousConfirmedPrefixAndRetainJapanese() {
        val result = parseSubtitleWriteToolArguments(
            arguments = """
                {"captions":[
                  {"source_indices":[0,1],"start_ms":0,"end_ms":1900,"japanese":"目を閉じて、おやすみ","chinese":"闭上眼睛，晚安"}
                ]}
            """.trimIndent(),
            expectedRemainingSources = sources(3),
            allowMerging = true
        )

        assertEquals(listOf(0, 1), result.single().sourceIndices)
        assertEquals("目を閉じて、おやすみ", result.single().correctedJapanese)
        assertEquals("闭上眼睛，晚安", result.single().chineseText)
    }

    @Test
    fun toolArguments_acceptManualLinesWithoutMerging() {
        val result = parseSubtitleWriteToolArguments(
            arguments = """
                {"captions":[
                  {"source_indices":[0],"start_ms":0,"end_ms":900,"japanese":"目を閉じて","chinese":"闭上眼睛"},
                  {"source_indices":[1],"start_ms":1000,"end_ms":1900,"japanese":"おやすみ","chinese":"晚安"}
                ]}
            """.trimIndent(),
            expectedRemainingSources = sources(3),
            allowMerging = false
        )

        assertEquals(listOf(listOf(0), listOf(1)), result.map(GeneratedSubtitleCaption::sourceIndices))
    }

    @Test
    fun toolArguments_keepStructuralChecksButNormalizeTimeline() {
        val skipped = """{"captions":[{"source_indices":[1],"start_ms":1000,"end_ms":1900,"japanese":"おやすみ","chinese":"晚安"}]}"""
        val wrongTimeline = """{"captions":[{"source_indices":[0],"start_ms":1,"end_ms":900,"japanese":"目を閉じて","chinese":"闭上眼睛"}]}"""
        val merged = """{"captions":[{"source_indices":[0,1],"start_ms":0,"end_ms":1900,"japanese":"目を閉じて、おやすみ","chinese":"闭上眼睛，晚安"}]}"""

        assertThrows(IllegalArgumentException::class.java) {
            parseSubtitleWriteToolArguments(skipped, sources(3), allowMerging = true)
        }
        val normalized = parseSubtitleWriteToolArguments(wrongTimeline, sources(3), allowMerging = true)
        assertEquals(0L, normalized.single().startMs)
        assertEquals(900L, normalized.single().endMs)
        assertThrows(IllegalArgumentException::class.java) {
            parseSubtitleWriteToolArguments(merged, sources(3), allowMerging = false)
        }
    }

    @Test
    fun toolArguments_leaveTranslationQualityToAgent() {
        val arguments = """{"captions":[{"source_indices":[0],"start_ms":0,"end_ms":900,"japanese":"おやすみ","chinese":"おやすみ"}]}"""

        val result = parseSubtitleWriteToolArguments(arguments, sources(1), allowMerging = false)

        assertEquals("おやすみ", result.single().chineseText)
    }

    @Test
    fun toolArguments_reportTruncatedToolPayloadPrecisely() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            parseSubtitleWriteToolArguments(
                arguments = """{"captions":[{"source_indices":[0],"chinese"""",
                expectedRemainingSources = sources(1),
                allowMerging = false
            )
        }

        assertEquals("字幕工具调用参数被截断，未形成完整 JSON 对象", error.message)
    }

    @Test
    fun retry_retriesRecoverableFailuresAndStopsAfterSuccess() = runBlocking {
        val attempts = mutableListOf<Int>()
        val delays = mutableListOf<Long>()
        val previousErrors = mutableListOf<String?>()
        var invocationCount = 0

        val result = retrySubtitleTranslation(
            maxAttempts = 4,
            onAttempt = { attempt, _ -> attempts += attempt },
            delayProvider = { delays += it }
        ) { previousError ->
            previousErrors += previousError?.message
            invocationCount += 1
            if (invocationCount < 3) {
                throw SubtitleTranslationException("暂时失败", retryable = true)
            }
            "完成"
        }

        assertEquals("完成", result)
        assertEquals(listOf(1, 2, 3), attempts)
        assertEquals(listOf(1_000L, 2_000L), delays)
        assertEquals(listOf(null, "暂时失败", "暂时失败"), previousErrors)
    }

    @Test
    fun retry_doesNotRetryPermanentFailure() {
        var invocationCount = 0

        assertThrows(SubtitleTranslationException::class.java) {
            runBlocking {
                retrySubtitleTranslation(delayProvider = {}) { _ ->
                    invocationCount += 1
                    throw SubtitleTranslationException("请求无效", retryable = false)
                }
            }
        }
        assertEquals(1, invocationCount)
    }

    @Test
    fun encodedPrompts_decodeToExpectedContent() {
        assert(TranslationPrompts.displayNameSystemPrompt().isNotBlank())
        assert(TranslationPrompts.subtitleStyleGuide().isNotBlank())
        assert(TranslationPrompts.subtitleReferenceTable().isNotBlank())
        assert(TranslationPrompts.subtitleProgressInstruction().isNotBlank())
        assert(TranslationPrompts.subtitleSegmentationRulesMerge().isNotBlank())
        assert(TranslationPrompts.subtitleSegmentationRulesNoMerge().isNotBlank())
        assert(TranslationPrompts.subtitleContinueMessageLengthLimited().isNotBlank())
        assert(TranslationPrompts.subtitleContinueMessageGeneric().isNotBlank())
        assert(TranslationPrompts.subtitleInitialUserMessage().isNotBlank())
        assert(TranslationPrompts.subtitleToolErrorInstruction().isNotBlank())
        assert(TranslationPrompts.subtitleToolReadDescription().isNotBlank())
        assert(TranslationPrompts.subtitleToolWriteDescription().isNotBlank())
        assert(TranslationPrompts.subtitleToolChineseFieldDescription().isNotBlank())
        assert(TranslationPrompts.subtitleScriptToolsSection().isNotBlank())
        assert(TranslationPrompts.subtitleScriptListToolDescription().isNotBlank())
        assert(TranslationPrompts.subtitleScriptReadToolDescription().isNotBlank())
        assert(TranslationPrompts.subtitleAgentSystemPromptTemplate().contains("{{SCRIPT_TOOLS}}"))
        assert(TranslationPrompts.subtitleAgentSystemPromptTemplate().contains("{{SOURCE_COUNT}}"))
        assert(TranslationPrompts.subtitleAgentSystemPromptTemplate().contains("{{REFERENCE_TABLE}}"))
        // 翻译提示词包含结构级翻译腔归化约束
        assert(TranslationPrompts.subtitleAgentSystemPromptTemplate().contains("修饰语＋人名"))
        assert(TranslationPrompts.subtitleAgentSystemPromptTemplate().contains("〜してしまう"))
        assert(TranslationPrompts.subtitleAgentSystemPromptTemplate().contains("被字句"))
        // 润色提示词包含同等的结构级精修项
        assert(TranslationPrompts.subtitlePolishSystemPromptTemplate().contains("形容词＋人名"))
        assert(TranslationPrompts.subtitlePolishSystemPromptTemplate().contains("被字句"))
        assert(TranslationPrompts.subtitlePolishSystemPromptTemplate().isNotBlank())
        assert(TranslationPrompts.subtitlePolishSystemPromptTemplate().contains("{{TRACK_COUNT}}"))
        assert(TranslationPrompts.subtitlePolishToolReadDescription().isNotBlank())
        assert(TranslationPrompts.subtitlePolishToolWriteDescription().isNotBlank())
        assert(TranslationPrompts.subtitlePolishInitialUserMessage().isNotBlank())
        assert(TranslationPrompts.subtitlePolishProgressInstruction().isNotBlank())
        assert(TranslationPrompts.subtitlePolishContinueMessageGeneric().isNotBlank())
    }

    @Test
    fun polishInitialMessages_injectTrackCountAndWorkContext() {
        val tracks = listOf(
            PolishTrackInput(
                trackIndex = 0,
                trackTitleJapanese = "サンプルトラックA",
                trackTitleChinese = "示例音轨A",
                captions = listOf(
                    PolishCaptionInput(captionId = 1L, sourceIndex = 0, japanese = "あ", chinese = "啊"),
                    PolishCaptionInput(captionId = 2L, sourceIndex = 1, japanese = "い", chinese = "咦")
                )
            ),
            PolishTrackInput(
                trackIndex = 1,
                trackTitleJapanese = "サンプルトラックB",
                trackTitleChinese = "示例音轨B",
                captions = listOf(PolishCaptionInput(captionId = 3L, sourceIndex = 0, japanese = "う", chinese = "嗯"))
            )
        )
        val messages = buildPolishAgentInitialMessages(
            gson = Gson(),
            tracks = tracks,
            workContext = SubtitleWorkContext(
                workTitleJapanese = "架空のサンプル作品タイトル",
                workTitleChinese = "虚构的示例作品标题"
            )
        )
        val system = messages[0].content.orEmpty()
        assert(system.contains("2 个音轨、3 条已翻译字幕"))
        assert(system.contains("作品标题（日文）：架空のサンプル作品タイトル"))
        assert(system.contains(POLISH_READ_TOOL_NAME))
        assert(system.contains(POLISH_WRITE_TOOL_NAME))
        // 润色 agent 必须拿到与翻译 agent 相同的风格指南与词汇对照表
        assert(system.contains("共同翻译风格"))
        assert(system.contains("ちんぽ / ちんこ / ちんちん"))
        assert(system.contains("寝取らせ"))
        assert(!system.contains("{{"))
        val user = messages[1].content.orEmpty()
        assert(user.contains("polish_translated_chinese_subtitles"))
    }

    @Test
    fun polishRead_pagesByOffsetAndMarksCompletion() {
        val tracks = listOf(
            PolishTrackInput(
                trackIndex = 0,
                captions = (1L..3L).map { id ->
                    PolishCaptionInput(captionId = id, sourceIndex = id.toInt() - 1, japanese = "ja$id", chinese = "zh$id")
                }
            )
        )
        val gson = Gson()
        val first = buildPolishReadToolResultMessage(gson, "c1", tracks, emptyMap(), offset = 0)
        val firstJson = JsonParser.parseString(first.content).asJsonObject
        assertEquals(3, firstJson.getAsJsonArray("subtitles").size())
        assertEquals(0, firstJson.get("offset").asInt)
        assertEquals(true, firstJson.get("completed").asBoolean)

        val polished = mapOf(1L to "改过了")
        val second = buildPolishReadToolResultMessage(gson, "c2", tracks, polished, offset = 3)
        val secondJson = JsonParser.parseString(second.content).asJsonObject
        assertEquals(0, secondJson.getAsJsonArray("subtitles").size())
        assertEquals(true, secondJson.get("completed").asBoolean)
        assertEquals(true, firstJson.getAsJsonArray("subtitles").get(0).asJsonObject.get("japanese").asString == "ja1")
    }

    @Test
    fun polishRead_returnsPolishedChineseWhenAvailable() {
        val tracks = listOf(
            PolishTrackInput(
                trackIndex = 0,
                captions = listOf(
                    PolishCaptionInput(captionId = 7L, sourceIndex = 0, japanese = "元", chinese = "原译文")
                )
            )
        )
        val gson = Gson()
        val msg = buildPolishReadToolResultMessage(gson, "c1", tracks, mapOf(7L to "精修后"), offset = 0)
        val json = JsonParser.parseString(msg.content).asJsonObject
        val subtitle = json.getAsJsonArray("subtitles").get(0).asJsonObject
        assertEquals("精修后", subtitle.get("chinese").asString)
    }

    @Test
    fun polishWrite_rejectsUnknownOrDuplicateCaptionIds() {
        val expected = listOf(
            PolishCaptionInput(captionId = 1L, sourceIndex = 0, japanese = "a", chinese = "b")
        )
        assertThrows(IllegalArgumentException::class.java) {
            parsePolishWriteToolArguments(
                """{"captions":[{"caption_id":999,"chinese":"x"}]}""",
                expected
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            parsePolishWriteToolArguments(
                """{"captions":[{"caption_id":1,"chinese":"x"},{"caption_id":1,"chinese":"y"}]}""",
                expected
            )
        }
        val ok = parsePolishWriteToolArguments(
            """{"captions":[{"caption_id":1,"chinese":"精修后的中文"}]}""",
            expected
        )
        assertEquals(1, ok.size)
        assertEquals(1L, ok[0].captionId)
        assertEquals("精修后的中文", ok[0].chinese)
    }

    @Test
    fun composedPrompt_hasNoLeftoverPlaceholders() {
        val merge = subtitleToolTranslationSystemPrompt(listOf(0, 1, 2), allowMerging = true)
        val noMerge = subtitleToolTranslationSystemPrompt(listOf(0, 1, 2), allowMerging = false)

        assert(!merge.contains("{{"))
        assert(!noMerge.contains("{{"))
        assert(merge != noMerge)
        assert(merge.length > 1_000)
        assert(merge.contains("3"))
    }

    @Test
    fun base64_decodesKnownVectors() {
        assertEquals("你好", String(TranslationPromptsCodec.decodeBase64("5L2g5aW9"), Charsets.UTF_8))
        assertEquals("Hello", String(TranslationPromptsCodec.decodeBase64("SGVsbG8="), Charsets.UTF_8))
        assertEquals("a", String(TranslationPromptsCodec.decodeBase64("YQ=="), Charsets.UTF_8))
        assertEquals("", String(TranslationPromptsCodec.decodeBase64(""), Charsets.UTF_8))
    }

    @Test
    fun scriptTools_exposedOnlyWhenContextHasFiles() {
        val gson = Gson()
        val source = sources(1)
        val base = JsonParser.parseString(
            buildDeepSeekSubtitleTranslationRequest(gson, source, allowMerging = false)
        ).asJsonObject
        assertEquals(
            listOf(SUBTITLE_READ_TOOL_NAME, SUBTITLE_WRITE_TOOL_NAME),
            base.getAsJsonArray("tools").map { it.asJsonObject.getAsJsonObject("function").get("name").asString }
        )

        val context = SubtitleScriptContext(
            files = listOf(SubtitleScriptFile(index = 0, name = "台本.txt")),
            reader = SubtitleScriptReader { _, _, _ -> null }
        )
        val withScript = JsonParser.parseString(
            buildDeepSeekSubtitleTranslationRequest(
                gson,
                source,
                allowMerging = false,
                scriptContext = context
            )
        ).asJsonObject
        assertEquals(
            listOf(SUBTITLE_READ_TOOL_NAME, SUBTITLE_WRITE_TOOL_NAME, SCRIPT_LIST_TOOL_NAME, SCRIPT_READ_TOOL_NAME),
            withScript.getAsJsonArray("tools").map { it.asJsonObject.getAsJsonObject("function").get("name").asString }
        )
    }

    @Test
    fun prompt_injectsScriptToolsSectionWhenContextPresent() {
        val context = SubtitleScriptContext(
            files = listOf(SubtitleScriptFile(index = 0, name = "台本.txt")),
            reader = SubtitleScriptReader { _, _, _ -> null }
        )
        val withScript = subtitleToolTranslationSystemPrompt(
            listOf(0),
            allowMerging = true,
            scriptContext = context
        )
        val withoutScript = subtitleToolTranslationSystemPrompt(listOf(0), allowMerging = true)

        assert(withScript.contains(SCRIPT_LIST_TOOL_NAME))
        assert(withScript.contains(SCRIPT_READ_TOOL_NAME))
        assert(withScript.contains("台本"))
        assert(!withScript.contains("{{"))
        assert(!withoutScript.contains(SCRIPT_LIST_TOOL_NAME))
        assert(!withoutScript.contains("{{"))
    }

    @Test
    fun scriptListTool_listsFiles() {
        val gson = Gson()
        val context = SubtitleScriptContext(
            files = listOf(
                SubtitleScriptFile(index = 0, name = "台本.txt"),
                SubtitleScriptFile(index = 1, name = "plot.md")
            ),
            reader = SubtitleScriptReader { _, _, _ -> null }
        )
        val msg = buildScriptListToolResultMessage(gson, "c1", context)
        val json = JsonParser.parseString(msg.content).asJsonObject
        assertEquals(2, json.get("total_files").asInt)
        assertEquals("台本.txt", json.getAsJsonArray("files")[0].asJsonObject.get("name").asString)
    }

    @Test
    fun scriptReadTool_pagesAndMarksCompletion() = runBlocking {
        val gson = Gson()
        val full = "abcdefghij"
        val context = SubtitleScriptContext(
            files = listOf(SubtitleScriptFile(index = 0, name = "台本.txt")),
            reader = SubtitleScriptReader { index, offset, limit ->
                val end = (offset + limit).coerceAtMost(full.length)
                SubtitleScriptReadResult(
                    fileIndex = index,
                    name = "台本.txt",
                    offset = offset,
                    totalChars = full.length,
                    content = full.substring(offset, end),
                    truncated = end < full.length
                )
            }
        )

        val first = buildScriptReadToolResultMessage(gson, "c1", context, 0, 0, 4)
        val firstJson = JsonParser.parseString(first.content).asJsonObject
        assertEquals("abcd", firstJson.get("content").asString)
        assertEquals(10, firstJson.get("total_chars").asInt)
        assertEquals(false, firstJson.get("completed").asBoolean)

        val last = buildScriptReadToolResultMessage(gson, "c2", context, 0, 8, 4)
        val lastJson = JsonParser.parseString(last.content).asJsonObject
        assertEquals("ij", lastJson.get("content").asString)
        assertEquals(true, lastJson.get("completed").asBoolean)
    }

    @Test
    fun scriptReadTool_parsesArgumentsAndRejectsOutOfRangeIndex() {
        val args = parseScriptReadToolArguments("""{"file_index":1}""", fileCount = 3)
        assertEquals(1, args.fileIndex)
        assertEquals(0, args.offset)
        assert(args.limit > 0)

        assertThrows(IllegalArgumentException::class.java) {
            parseScriptReadToolArguments("""{"file_index":5}""", fileCount = 3)
        }
    }

    private fun sources(count: Int): List<GeneratedSubtitleSource> = List(count) { index ->
        GeneratedSubtitleSource(
            index = index,
            startMs = index * 1_000L,
            endMs = index * 1_000L + 900L,
            text = when (index) {
                0 -> "目を閉じて"
                1 -> "おやすみ"
                else -> "また明日"
            }
        )
    }

    private fun toolCallResponse(
        id: String,
        name: String,
        reasoning: String,
        arguments: String
    ): String =
        Gson().toJson(
            mapOf(
                "choices" to listOf(
                    mapOf(
                        "finish_reason" to "tool_calls",
                        "message" to mapOf(
                            "role" to "assistant",
                            "content" to "",
                            "reasoning_content" to reasoning,
                            "tool_calls" to listOf(
                                mapOf(
                                    "id" to id,
                                    "type" to "function",
                                    "function" to mapOf(
                                        "name" to name,
                                        "arguments" to arguments
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

    private fun assistantResponse(content: String): String = Gson().toJson(
        mapOf(
            "choices" to listOf(
                mapOf(
                    "finish_reason" to "stop",
                    "message" to mapOf(
                        "role" to "assistant",
                        "content" to content
                    )
                )
            )
        )
    )
}
