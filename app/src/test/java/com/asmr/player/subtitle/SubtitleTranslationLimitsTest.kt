package com.asmr.player.subtitle

import android.app.Application
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34], manifest = Config.NONE)
class SubtitleTranslationLimitsTest {
    private val gson = Gson()
    private val sources = (0 until 3).map { source(it) }

    @Test
    fun repeatedScriptListing_stopsWithoutAutomaticRetry() = runBlocking {
        val fixture = Fixture { tool(SCRIPT_LIST_TOOL_NAME, "{}", it) }
        expectStalled { fixture.client.translateSubtitles(sources, false, scriptContext = scriptContext()) {} }
        assertEquals(8, fixture.requests.size)
    }

    @Test
    fun failedScriptReads_stillCountAsStalledTurns() = runBlocking {
        val fixture = Fixture { tool(SCRIPT_READ_TOOL_NAME, """{"file_index":0}""", it) }
        expectStalled { fixture.client.translateSubtitles(sources, false, scriptContext = scriptContext()) {} }
        assertEquals(8, fixture.requests.size)
    }

    @Test
    fun successfulScriptReads_cannotReplaceSubtitleProgressForever() = runBlocking {
        // 即使每次读不同位置且成功返回，也必须及时写入字幕。
        val fixture = Fixture { tool(SCRIPT_READ_TOOL_NAME, """{"file_index":0,"offset":$it}""", it) }
        expectStalled { fixture.client.translateSubtitles(sources, false, scriptContext = scriptContext(readable = true)) {} }
        assertEquals(8, fixture.requests.size)
    }

    @Test
    fun ordinaryReplies_stopAfterFourTurns() = runBlocking {
        val fixture = Fixture {
            """{"choices":[{"message":{"role":"assistant","content":"正在处理"}}]}"""
        }
        expectStalled { fixture.client.translateSubtitles(sources, false) {} }
        assertEquals(4, fixture.requests.size)
    }

    @Test
    fun scriptPreparationAndIncrementalWrites_completeNormally() = runBlocking {
        val fixture = Fixture { turn ->
            when (turn) {
                1 -> tool(SCRIPT_LIST_TOOL_NAME, "{}", turn)
                2 -> tool(SCRIPT_READ_TOOL_NAME, """{"file_index":0}""", turn)
                3, 5, 7 -> tool(SUBTITLE_READ_TOOL_NAME, "{}", turn)
                else -> subtitleWrite(listOf(sources[(turn - 4) / 2]), turn)
            }
        }
        val result = fixture.client.translateSubtitles(sources, false, scriptContext = scriptContext(true)) {}
        assertEquals(3, result.size)
        assertEquals(8, fixture.requests.size)
    }

    @Test
    fun repeatedSubtitleReads_keepOneSnapshotAndPreserveHistoryPrefix() = runBlocking {
        val transcript = (0 until 1000).map { source(it) }
        val fixture = Fixture { turn ->
            if (turn % 2 == 1) tool(SUBTITLE_READ_TOOL_NAME, "{}", turn)
            else subtitleWrite(transcript.drop((turn / 2 - 1) * 100).take(100), turn)
        }
        val result = fixture.client.translateSubtitles(transcript, false) {}
        assertEquals(1000, result.size)
        assertEquals(20, fixture.requests.size)
        fixture.requests.zipWithNext().forEach { (previous, next) ->
            val prefix = previous.getAsJsonArray("messages").toList()
            assertEquals(prefix, next.getAsJsonArray("messages").take(prefix.size))
        }
        val readResults = fixture.requests.last().getAsJsonArray("messages").map { it.asJsonObject }
            .filter { it["role"].asString == "tool" }
            .map { JsonParser.parseString(it["content"].asString).asJsonObject }
            .filter { it.has("japanese_subtitles") || it.has("subtitle_context_tool_call_id") }
        assertEquals(10, readResults.size)
        assertEquals(1, readResults.count { it.has("japanese_subtitles") })
        assertEquals(1, readResults.count { it.has("completed_chinese_subtitles") })
        assertEquals(1000, readResults.first().getAsJsonArray("japanese_subtitles").size())
        assertEquals("call-1", readResults.last()["subtitle_context_tool_call_id"].asString)
        assertEquals(900, readResults.last()["next_untranslated_index"].asInt)
        assertEquals(100, readResults.last()["remaining_source_count"].asInt)
    }

    @Test
    fun resumedTranslation_includesCommittedCaptionsInItsFirstSnapshot() = runBlocking {
        val committed = listOf(GeneratedSubtitleCaption(listOf(0), 0, 900, "おやすみ", "晚安"))
        val fixture = Fixture { turn ->
            if (turn == 1) tool(SUBTITLE_READ_TOOL_NAME, "{}", turn)
            else subtitleWrite(sources.drop(1), turn)
        }
        val result = fixture.client.translateSubtitles(sources, false, confirmedCaptions = committed) {}
        assertEquals(3, result.size)
        val snapshot = lastToolResult(fixture.requests[1])
        assertEquals(3, snapshot.getAsJsonArray("japanese_subtitles").size())
        assertEquals(1, snapshot.getAsJsonArray("completed_chinese_subtitles").size())
        assertEquals(1, snapshot["next_untranslated_index"].asInt)
    }

    @Test
    fun emptyPolishWrites_returnToolErrorsAndStop() = runBlocking {
        val fixture = Fixture { turn ->
            if (turn == 1) tool(POLISH_READ_TOOL_NAME, "{}", turn)
            else tool(POLISH_WRITE_TOOL_NAME, """{"captions":[]}""", turn)
        }
        expectStalled { fixture.client.polishSubtitles(tracks()) { fail("空写入不能提交") } }
        assertEquals(5, fixture.requests.size)
        assertTrue(lastToolResult(fixture.requests[2])["message"].asString.contains("captions 不能为空"))
    }

    @Test
    fun duplicatePolishWrites_doNotKeepTaskAliveOrRepeatDatabaseWrites() = runBlocking {
        val fixture = Fixture { turn ->
            if (turn == 1) tool(POLISH_READ_TOOL_NAME, "{}", turn)
            else polishWrite(1, "晚安，好好休息", turn)
        }
        val committed = mutableListOf<PolishCaptionResult>()
        expectStalled { fixture.client.polishSubtitles(tracks(2)) { committed += it } }
        assertEquals(6, fixture.requests.size)
        assertEquals(listOf(PolishCaptionResult(1, "晚安，好好休息")), committed)
    }

    @Test
    fun changingTheSameCaptionRepeatedly_isStillBounded() = runBlocking {
        val fixture = Fixture { turn ->
            if (turn == 1) tool(POLISH_READ_TOOL_NAME, "{}", turn)
            else polishWrite(1, "晚安$turn", turn)
        }
        expectStalled { fixture.client.polishSubtitles(tracks(2)) {} }
        assertEquals(6, fixture.requests.size)
    }

    @Test
    fun polishCompletesImmediatelyWhenEveryCaptionHasBeenWritten() = runBlocking {
        val fixture = Fixture { turn ->
            if (turn == 1) tool(POLISH_READ_TOOL_NAME, "{}", turn)
            else polishWrite(1, "已经精修完毕", turn)
        }
        val result = fixture.client.polishSubtitles(tracks()) {}
        assertEquals(listOf(PolishCaptionResult(1, "已经精修完毕")), result)
        assertEquals(2, fixture.requests.size)
    }

    @Test
    fun polishProcessesLastPageBeforeCompleting() = runBlocking {
        val fixture = Fixture { turn ->
            when (turn) {
                2 -> polishWrite(1, "第一页改好了", turn)
                4 -> polishWrite(41, "最后一页改好了", turn)
                else -> tool(POLISH_READ_TOOL_NAME, "{}", turn)
            }
        }
        val result = fixture.client.polishSubtitles(tracks(41)) {}
        assertEquals(5, fixture.requests.size)
        assertEquals("第一页改好了", result.first().chinese)
        assertEquals("最后一页改好了", result.last().chinese)
        assertEquals(1, lastToolResult(fixture.requests[3]).getAsJsonArray("subtitles").size())
        assertFalse(lastToolResult(fixture.requests[3])["completed"].asBoolean)
    }

    @Test
    fun polishCanLeaveEveryCaptionUnchanged() = runBlocking {
        val fixture = Fixture { tool(POLISH_READ_TOOL_NAME, "{}", it) }
        val result = fixture.client.polishSubtitles(tracks(41)) { fail("无需修改时不应写入") }
        assertEquals(3, fixture.requests.size)
        assertTrue(result.all { it.chinese == "晚安" })
    }

    @Test
    fun completedPolishRead_isNotRejectedByTheStallGuard() = runBlocking {
        val fixture = Fixture { turn ->
            if (turn == 1 || turn == 5) tool(POLISH_READ_TOOL_NAME, "{}", turn)
            else """{"choices":[{"message":{"role":"assistant","content":"无需修改"}}]}"""
        }
        val result = fixture.client.polishSubtitles(tracks()) {}
        assertEquals(listOf(PolishCaptionResult(1, "晚安")), result)
        assertEquals(5, fixture.requests.size)
    }

    @Test
    fun polishRejectsUnreadCaptionsAndAllowsCorrectedArguments() = runBlocking {
        val fixture = Fixture { turn ->
            when (turn) {
                2 -> polishWrite(41, "尚未读到", turn)
                3 -> polishWrite(1, "已修正", turn)
                else -> tool(POLISH_READ_TOOL_NAME, "{}", turn)
            }
        }
        val result = fixture.client.polishSubtitles(tracks(41)) {}
        assertEquals("error", lastToolResult(fixture.requests[2])["status"].asString)
        assertEquals("已修正", result.first().chinese)
        assertEquals("晚安", result.last().chinese)
    }

    @Test
    fun polishPromptMatchesToolSchema() {
        val prompt = buildPolishAgentInitialMessages(gson, tracks()).first().content.orEmpty()
        val descriptions = TranslationPrompts.subtitlePolishToolReadDescription() + TranslationPrompts.subtitlePolishToolWriteDescription()
        assertTrue(prompt.contains("caption_id"))
        assertFalse(prompt.contains("source_index"))
        assertTrue(descriptions.contains("caption_id"))
        assertFalse(descriptions.contains("source_index"))
        assertFalse(descriptions.contains("\"offset\""))
    }

    @Test
    fun invalidTitleResponse_exhaustsOnlyTwoRequests() = runBlocking {
        val fixture = Fixture { """{"choices":[{"message":{"role":"assistant","content":"invalid"}}]}""" }
        try {
            fixture.client.translateDisplayNames("作品", "", "", listOf(1L to "音轨"))
            fail("应在有限重试后结束")
        } catch (_: SubtitleTranslationException) {
            assertEquals(2, fixture.requests.size)
        }
    }

    @Test
    fun cancellationDuringCaptionCommit_doesNotRequestAnotherTurn() = runBlocking {
        val fixture = Fixture { subtitleWrite(sources, it) }
        try {
            fixture.client.translateSubtitles(sources, false) { throw CancellationException("用户取消") }
            fail("取消必须向上传递")
        } catch (_: CancellationException) {
            assertEquals(1, fixture.requests.size)
        }
    }

    private suspend fun expectStalled(operation: suspend () -> Unit) {
        try {
            operation()
            fail("应停止无进展对话")
        } catch (error: SubtitleTranslationException) {
            assertFalse(error.retryable)
            assertTrue(error.message.orEmpty().contains("连续多轮"))
        }
    }

    private fun source(index: Int) = GeneratedSubtitleSource(index, index * 1000L, index * 1000L + 900, "おやすみ")

    private fun scriptContext(readable: Boolean = false) = SubtitleScriptContext(
        listOf(SubtitleScriptFile(0, "台本.txt")),
        SubtitleScriptReader { index, offset, _ ->
            if (readable) SubtitleScriptReadResult(index, "台本.txt", offset, 1000, "台本内容", true) else null
        }
    )

    private fun tracks(count: Int = 1) = listOf(PolishTrackInput(0, captions = (1..count).map {
        PolishCaptionInput(it.toLong(), it - 1, "おやすみ", "晚安")
    }))

    private fun subtitleWrite(batch: List<GeneratedSubtitleSource>, turn: Int) = tool(
        SUBTITLE_WRITE_TOOL_NAME,
        gson.toJson(mapOf("captions" to batch.map {
            mapOf("source_indices" to listOf(it.index), "start_ms" to it.startMs, "end_ms" to it.endMs,
                "japanese" to it.text, "chinese" to "晚安")
        })),
        turn
    )

    private fun polishWrite(id: Long, chinese: String, turn: Int) = tool(
        POLISH_WRITE_TOOL_NAME,
        gson.toJson(mapOf("captions" to listOf(mapOf("caption_id" to id, "chinese" to chinese)))),
        turn
    )

    private fun tool(name: String, arguments: String, turn: Int): String = gson.toJson(mapOf(
        "choices" to listOf(mapOf("finish_reason" to "tool_calls", "message" to DeepSeekChatMessage(
            role = "assistant", content = "",
            toolCalls = listOf(DeepSeekToolCall("call-$turn", function = DeepSeekToolCallFunction(name, arguments)))
        )))
    ))

    private fun lastToolResult(request: JsonObject): JsonObject = JsonParser.parseString(
        request.getAsJsonArray("messages").last().asJsonObject["content"].asString
    ).asJsonObject

    private inner class Fixture(respond: (Int) -> String) {
        val requests = mutableListOf<JsonObject>()
        private val config = TranslationApiConfig(TranslationProvider.CUSTOM, "test-key", "https://translation.invalid/v1", "test-model")
        private val http = OkHttpClient.Builder().addInterceptor { chain ->
            requests += JsonParser.parseString(Buffer().also { chain.request().body!!.writeTo(it) }.readUtf8()).asJsonObject
            // 防止回归导致测试无限运行；所有响应在本地生成，不发起网络请求。
            val overLimit = requests.size > 30
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(if (overLimit) 401 else 200).message("Test response")
                .body((if (overLimit) "{}" else respond(requests.size)).toResponseBody("application/json".toMediaType()))
                .build()
        }.build()
        val client = SubtitleTranslationClient(http, gson, config.apiKey, apiConfig = config)
    }
}
