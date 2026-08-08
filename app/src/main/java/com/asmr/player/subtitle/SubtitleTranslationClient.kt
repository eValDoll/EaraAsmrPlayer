package com.asmr.player.subtitle

import android.util.Log
import com.asmr.player.data.remote.NetworkHeaders
import com.asmr.player.data.settings.DeepSeekTranslationSettings
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class DisplayNameTranslationResult(
    val albumTitle: String,
    val trackTitles: Map<Long, String>
)

internal data class DeepSeekChatMessage(
    val role: String,
    val content: String? = null,
    @SerializedName("reasoning_content")
    val reasoningContent: String? = null,
    @SerializedName("tool_calls")
    val toolCalls: List<DeepSeekToolCall>? = null,
    @SerializedName("tool_call_id")
    val toolCallId: String? = null
)

private data class DeepSeekThinking(
    val type: String = "enabled"
)

private data class DeepSeekResponseFormat(
    val type: String = "json_object"
)

private data class DeepSeekChatRequest(
    val model: String = DEEPSEEK_SUBTITLE_MODEL,
    val messages: List<DeepSeekChatMessage>,
    val thinking: DeepSeekThinking = DeepSeekThinking(),
    @SerializedName("reasoning_effort")
    val reasoningEffort: String? = "high",
    @SerializedName("response_format")
    val responseFormat: DeepSeekResponseFormat? = DeepSeekResponseFormat(),
    val tools: List<DeepSeekToolDefinition>? = null,
    @SerializedName("max_tokens")
    val maxTokens: Int = 32_768,
    val stream: Boolean = false
)

private data class DeepSeekToolDefinition(
    val type: String = "function",
    val function: DeepSeekFunctionDefinition
)

private data class DeepSeekFunctionDefinition(
    val name: String,
    val description: String,
    val parameters: Map<String, Any>
)

private data class DeepSeekChatResponse(
    val choices: List<DeepSeekChoice> = emptyList()
)

private data class DeepSeekChoice(
    @SerializedName("finish_reason")
    val finishReason: String? = null,
    val message: DeepSeekChatMessage = DeepSeekChatMessage(role = "assistant")
)

internal data class DeepSeekToolCall(
    val id: String = "",
    val type: String = "function",
    val function: DeepSeekToolCallFunction = DeepSeekToolCallFunction()
)

internal data class DeepSeekToolCallFunction(
    val name: String? = null,
    val arguments: String? = null
)

private data class SubtitleAgentResponse(
    val assistantMessage: DeepSeekChatMessage,
    val finishReason: String?
)

internal class SubtitleTranslationException(
    message: String,
    val retryable: Boolean,
    val retryAfterMs: Long? = null,
    cause: Throwable? = null
) : IOException(message, cause)

internal class SubtitleTranslationClient(
    okHttpClient: OkHttpClient,
    private val gson: Gson,
    apiKey: String,
    private val settings: DeepSeekTranslationSettings = DeepSeekTranslationSettings(),
    private val apiUrl: String = DEEPSEEK_CHAT_COMPLETIONS_URL
) {
    private val authorization = "Bearer ${apiKey.trim().also { require(it.isNotEmpty()) { "请先在设置中配置 DeepSeek API Key" } }}"
    private val callFactory: Call.Factory = okHttpClient.newBuilder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(6, TimeUnit.MINUTES)
        .build()

    suspend fun translateSubtitles(
        sources: List<GeneratedSubtitleSource>,
        allowMerging: Boolean,
        confirmedCaptions: List<GeneratedSubtitleCaption> = emptyList(),
        onCaptionsConfirmed: suspend (List<GeneratedSubtitleCaption>) -> Unit
    ): List<GeneratedSubtitleCaption> {
        require(sources.isNotEmpty()) { "完整字幕不能为空" }
        require(sources.map(GeneratedSubtitleSource::index).distinct().size == sources.size) {
            "字幕源索引不能重复"
        }
        val confirmed = confirmedCaptions.toMutableList()
        if (confirmed.isNotEmpty()) {
            validateSubtitleCaptionBatch(
                captions = confirmed,
                expectedRemainingSources = sources,
                allowMerging = allowMerging
            )
        }
        var confirmedSourceCount = confirmed.sumOf { it.sourceIndices.size }
        if (confirmedSourceCount >= sources.size) return confirmed
        val messages = buildSubtitleAgentInitialMessages(
            gson = gson,
            sources = sources,
            allowMerging = allowMerging,
            confirmedCaptions = confirmed
        ).toMutableList()
        var stalledTurnCount = 0
        while (confirmedSourceCount < sources.size) {
            val response = requestSubtitleAgentResponse(
                messages = messages,
                targetIndices = sources.drop(confirmedSourceCount).map(GeneratedSubtitleSource::index)
            )
            messages += response.assistantMessage
            val toolCalls = response.assistantMessage.toolCalls.orEmpty()
            var wroteCaptions = false
            if (toolCalls.isEmpty()) {
                stalledTurnCount += 1
                messages += DeepSeekChatMessage(
                    role = "user",
                    content = subtitleAgentContinueMessage(response.finishReason)
                )
            } else {
                toolCalls.forEach { toolCall ->
                    val toolCallId = toolCall.id.trim()
                    if (toolCallId.isEmpty()) {
                        throw SubtitleTranslationException(
                            message = "字幕翻译模型返回的工具调用缺少 id",
                            retryable = true
                        )
                    }
                    val toolResult = when (toolCall.function.name.orEmpty()) {
                        SUBTITLE_READ_TOOL_NAME -> buildSubtitleReadToolResultMessage(
                            gson = gson,
                            toolCallId = toolCallId,
                            sources = sources,
                            confirmedCaptions = confirmed
                        )

                        SUBTITLE_WRITE_TOOL_NAME -> {
                            val remainingSources = sources.drop(confirmedSourceCount)
                            val parsed = runCatching {
                                parseSubtitleWriteToolArguments(
                                    arguments = toolCall.function.arguments.orEmpty(),
                                    expectedRemainingSources = remainingSources,
                                    allowMerging = allowMerging
                                )
                            }
                            parsed.fold(
                                onSuccess = { captions ->
                                    onCaptionsConfirmed(captions)
                                    confirmed += captions
                                    val writtenSourceCount = captions.sumOf { it.sourceIndices.size }
                                    confirmedSourceCount += writtenSourceCount
                                    wroteCaptions = true
                                    buildSubtitleWriteToolResultMessage(
                                        gson = gson,
                                        toolCallId = toolCallId,
                                        writtenCaptionCount = captions.size,
                                        writtenSourceCount = writtenSourceCount,
                                        confirmedSourceCount = confirmedSourceCount,
                                        sources = sources
                                    )
                                },
                                onFailure = { error ->
                                    buildSubtitleToolErrorMessage(
                                        gson = gson,
                                        toolCallId = toolCallId,
                                        message = error.message.orEmpty().ifBlank { "字幕参数无效" }
                                    )
                                }
                            )
                        }

                        else -> buildSubtitleToolErrorMessage(
                            gson = gson,
                            toolCallId = toolCallId,
                            message = "未知工具：${toolCall.function.name.orEmpty()}"
                        )
                    }
                    messages += toolResult
                }
                stalledTurnCount = if (wroteCaptions) 0 else stalledTurnCount + 1
            }
            if (stalledTurnCount >= MAX_SUBTITLE_AGENT_STALLED_TURNS) {
                throw SubtitleTranslationException(
                    message = "字幕翻译 agent 连续多轮没有写入字幕",
                    retryable = true
                )
            }
        }
        return confirmed
    }

    /**
     * 翻译一次本地库作品的显示名（作品标题 + 音轨标题），用于翻译任务进行期间的
     * 显示名覆盖。失败可重试，重试次数由 [maxAttempts] 控制。
     */
    suspend fun translateDisplayNames(
        albumTitle: String,
        circle: String,
        cv: String,
        trackTitles: List<Pair<Long, String>>,
        maxAttempts: Int = 2
    ): DisplayNameTranslationResult {
        require(albumTitle.isNotBlank()) { "作品标题不能为空" }
        require(trackTitles.isNotEmpty()) { "音轨列表不能为空" }
        require(trackTitles.map(Pair<Long, String>::first).distinct().size == trackTitles.size) {
            "音轨 id 不能重复"
        }
        val requestBody = buildDeepSeekTitleTranslationRequest(
            gson = gson,
            albumTitle = albumTitle,
            circle = circle,
            cv = cv,
            trackTitles = trackTitles,
            settings = settings
        )
        val expectedTrackIds = trackTitles.map(Pair<Long, String>::first)
        return retrySubtitleTranslation(maxAttempts = maxAttempts, onAttempt = { _, _ -> }, onRetry = { _, _, _ -> }) {
            executeTranslationRequest(requestBody, emptyList()) { message, finishReason ->
                require(finishReason != "length") { "作品显示名翻译输出达到长度限制" }
                val content = message.content.orEmpty()
                require(content.isNotBlank()) { "作品显示名翻译模型返回了空内容" }
                parseDisplayNameTranslationResponse(content, expectedTrackIds)
            }
        }
    }

    private suspend fun requestSubtitleAgentResponse(
        messages: List<DeepSeekChatMessage>,
        targetIndices: List<Int>
    ): SubtitleAgentResponse {
        val requestBody = buildDeepSeekSubtitleAgentRequest(
            gson = gson,
            messages = messages,
            settings = settings
        )
        return executeTranslationRequest(
            requestBody,
            targetIndices
        ) { message, finishReason ->
            SubtitleAgentResponse(
                assistantMessage = message.copy(role = "assistant", content = message.content.orEmpty()),
                finishReason = finishReason
            )
        }
    }

    private suspend fun <T> executeTranslationRequest(
        requestBody: String,
        targetIndices: List<Int>,
        parseMessage: (DeepSeekChatMessage, finishReason: String?) -> T
    ): T = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(apiUrl)
            .header("Authorization", authorization)
            .header(NetworkHeaders.HEADER_SILENT_IO_ERROR, NetworkHeaders.SILENT_IO_ERROR_ON)
            .post(requestBody.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val call = callFactory.newCall(request)
        val cancellationHandle = currentCoroutineContext().job.invokeOnCompletion { cause ->
            if (cause is CancellationException) call.cancel()
        }
        val response = try {
            executeCancellable(call)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: IOException) {
            throw SubtitleTranslationException(
                message = "无法连接 DeepSeek 翻译服务",
                retryable = true,
                cause = error
            )
        }
        try {
            response.use {
                val raw = it.body?.string().orEmpty()
                if (!it.isSuccessful) {
                    val retryable = it.code == 408 || it.code == 425 || it.code == 429 || it.code >= 500
                    val serviceMessage = parseDeepSeekErrorMessage(raw)
                    throw SubtitleTranslationException(
                        message = when (it.code) {
                            401 -> "DeepSeek API Key 无效"
                            402 -> "DeepSeek 账户余额不足"
                            429 -> "DeepSeek 请求过于频繁"
                            in 500..599 -> "DeepSeek 服务暂时不可用"
                            else -> buildString {
                                append("DeepSeek 翻译请求失败（HTTP ${it.code}）")
                                serviceMessage?.let { message -> append("：$message") }
                            }
                        },
                        retryable = retryable,
                        retryAfterMs = parseRetryAfterMillis(it.header("Retry-After"))
                    )
                }
                val deepSeekResponse = runCatching {
                    gson.fromJson(raw, DeepSeekChatResponse::class.java)
                }.getOrNull()
                val choice = deepSeekResponse?.choices?.firstOrNull()
                val message = choice?.message ?: DeepSeekChatMessage(role = "assistant")
                val content = message.content.orEmpty()
                val reasoningContent = message.reasoningContent.orEmpty()
                val toolArgumentLength = message.toolCalls.orEmpty()
                    .sumOf { it.function.arguments.orEmpty().length }
                try {
                    parseMessage(message, choice?.finishReason)
                } catch (error: IllegalArgumentException) {
                    val reason = error.message?.takeIf { it.isNotBlank() } ?: "未知格式错误"
                    logRejectedTranslation(
                        reason = reason,
                        targetIndices = targetIndices,
                        rawLength = raw.length,
                        finishReason = choice?.finishReason,
                        contentLength = content.length,
                        reasoningLength = reasoningContent.length,
                        toolArgumentLength = toolArgumentLength,
                        error = error
                    )
                    throw SubtitleTranslationException(
                        message = "字幕翻译模型返回格式错误：$reason",
                        retryable = true,
                        cause = error
                    )
                }
            }
        } finally {
            cancellationHandle.dispose()
        }
    }

    private suspend fun executeCancellable(call: Call): Response =
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (!continuation.isActive) return
                    if (call.isCanceled()) {
                        continuation.resumeWithException(CancellationException("字幕翻译请求已取消", e))
                    } else {
                        continuation.resumeWithException(e)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    if (continuation.isActive) {
                        continuation.resume(response)
                    } else {
                        response.close()
                    }
                }
            })
        }

    private companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val TAG = "SubtitleTranslation"

        private fun logRejectedTranslation(
            reason: String,
            targetIndices: List<Int>,
            rawLength: Int,
            finishReason: String?,
            contentLength: Int,
            reasoningLength: Int,
            toolArgumentLength: Int,
            error: Throwable? = null
        ) {
            Log.w(
                TAG,
                "DeepSeek subtitle translation rejected: reason=$reason, " +
                    "targets=${targetIndices.summarizeIndices()}, rawLength=$rawLength, finishReason=$finishReason, " +
                    "contentLength=$contentLength, reasoningLength=$reasoningLength, " +
                    "toolArgumentLength=$toolArgumentLength",
                error
            )
        }

        private fun List<Int>.summarizeIndices(): String = when {
            isEmpty() -> "[]"
            size == 1 -> "[${single()}]"
            else -> "[${first()}..${last()}] ($size)"
        }
    }
}

internal fun parseDeepSeekErrorMessage(raw: String): String? = runCatching {
    JsonParser.parseString(raw).asJsonObject
        .getAsJsonObject("error")
        ?.get("message")
        ?.asString
        ?.trim()
        ?.replace(Regex("\\s+"), " ")
        ?.take(300)
        ?.takeIf(String::isNotEmpty)
}.getOrNull()

internal fun parseRetryAfterMillis(
    value: String?,
    nowMs: Long = System.currentTimeMillis()
): Long? {
    val normalized = value?.trim().orEmpty()
    if (normalized.isEmpty()) return null
    normalized.toLongOrNull()?.let { seconds ->
        return seconds.coerceAtLeast(0L).let { safeSeconds ->
            if (safeSeconds > Long.MAX_VALUE / 1_000L) Long.MAX_VALUE else safeSeconds * 1_000L
        }
    }
    val retryAt = runCatching {
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply {
            isLenient = false
        }.parse(normalized)?.time
    }.getOrNull() ?: return null
    return (retryAt - nowMs).coerceAtLeast(0L)
}

internal const val DEEPSEEK_SUBTITLE_MODEL = "deepseek-v4-flash"
internal const val SUBTITLE_READ_TOOL_NAME = "read_subtitle_translation_state"
internal const val SUBTITLE_WRITE_TOOL_NAME = "write_timed_chinese_subtitles"
private const val DEEPSEEK_CHAT_COMPLETIONS_URL = "https://api.deepseek.com/chat/completions"
private const val MAX_SUBTITLE_AGENT_STALLED_TURNS = 4

internal fun buildDeepSeekTitleTranslationRequest(
    gson: Gson,
    albumTitle: String,
    circle: String,
    cv: String,
    trackTitles: List<Pair<Long, String>>,
    settings: DeepSeekTranslationSettings = DeepSeekTranslationSettings()
): String {
    require(albumTitle.isNotBlank())
    require(trackTitles.isNotEmpty())
    val userPayload = buildMap<String, Any> {
        put("work_title", albumTitle)
        put("circle", circle)
        put("cv", cv)
        put("tracks", trackTitles.mapIndexed { index, (trackId, title) ->
            mapOf("track_id" to trackId, "index" to index, "title" to title)
        })
    }
    return gson.toJson(
        DeepSeekChatRequest(
            messages = listOf(
                DeepSeekChatMessage(role = "system", content = displayNameTranslationSystemPrompt()),
                DeepSeekChatMessage(role = "user", content = gson.toJson(userPayload))
            ),
            thinking = DeepSeekThinking(type = if (settings.thinkingEnabled) "enabled" else "disabled"),
            reasoningEffort = settings.reasoningEffort.wireValue.takeIf { settings.thinkingEnabled }
        )
    )
}

internal fun parseDisplayNameTranslationResponse(
    content: String,
    expectedTrackIds: List<Long>
): DisplayNameTranslationResult {
    require(expectedTrackIds.isNotEmpty())
    val root = parseTranslationContentJsonObject(content)
    val workTitle = runCatching { root.get("work_title").asString.trim() }
        .getOrElse { throw IllegalArgumentException("缺少 work_title", it) }
    require(workTitle.isNotEmpty()) { "作品标题不能为空" }
    val tracks = runCatching { root.getAsJsonArray("tracks") }
        .getOrElse { throw IllegalArgumentException("缺少 tracks 数组", it) }
        ?: throw IllegalArgumentException("缺少 tracks 数组")
    val expectedSet = expectedTrackIds.toSet()
    val parsed = LinkedHashMap<Long, String>(tracks.size())
    tracks.forEach { element ->
        val item = element.takeIf { it.isJsonObject }?.asJsonObject
            ?: throw IllegalArgumentException("音轨翻译项不是对象")
        val trackId = runCatching { item.get("track_id").asLong }
            .getOrElse { throw IllegalArgumentException("音轨翻译项缺少 track_id", it) }
        val title = runCatching { item.get("title").asString.trim() }
            .getOrElse { throw IllegalArgumentException("音轨翻译项缺少 title", it) }
        if (trackId !in expectedSet) return@forEach
        require(title.isNotEmpty()) { "音轨标题不能为空：track_id=$trackId" }
        require(parsed.put(trackId, title) == null) { "音轨翻译索引重复：track_id=$trackId" }
    }
    val missing = expectedTrackIds.filterNot { it in parsed }
    require(missing.isEmpty()) { "音轨翻译索引不匹配：缺少 ${missing.joinToString(",")}" }
    return DisplayNameTranslationResult(
        albumTitle = workTitle,
        trackTitles = expectedTrackIds.associateWith { parsed.getValue(it) }
    )
}

private fun displayNameTranslationSystemPrompt(): String =
    TranslationPrompts.displayNameSystemPrompt()

internal fun buildDeepSeekSubtitleTranslationRequest(
    gson: Gson,
    sources: List<GeneratedSubtitleSource>,
    allowMerging: Boolean,
    confirmedCaptions: List<GeneratedSubtitleCaption> = emptyList(),
    settings: DeepSeekTranslationSettings = DeepSeekTranslationSettings()
): String {
    val messages = buildSubtitleAgentInitialMessages(
        gson = gson,
        sources = sources,
        allowMerging = allowMerging,
        confirmedCaptions = confirmedCaptions
    )
    return buildDeepSeekSubtitleAgentRequest(gson, messages, settings)
}

internal fun buildSubtitleAgentInitialMessages(
    gson: Gson,
    sources: List<GeneratedSubtitleSource>,
    allowMerging: Boolean,
    confirmedCaptions: List<GeneratedSubtitleCaption> = emptyList()
): List<DeepSeekChatMessage> {
    require(sources.isNotEmpty())
    val confirmedSourceCount = confirmedCaptions.sumOf { it.sourceIndices.size }
    require(confirmedSourceCount < sources.size)
    val targetIndices = sources.map(GeneratedSubtitleSource::index)
    val userPayload = mapOf(
        "task" to "translate_current_audio_track_subtitles",
        "source_count" to sources.size,
        "completed_source_count" to confirmedSourceCount,
        "message" to TranslationPrompts.subtitleInitialUserMessage()
    )
    return listOf(
        DeepSeekChatMessage(
            role = "system",
            content = subtitleToolTranslationSystemPrompt(targetIndices, allowMerging)
        ),
        DeepSeekChatMessage(
            role = "user",
            content = gson.toJson(userPayload)
        )
    )
}

internal fun buildDeepSeekSubtitleAgentRequest(
    gson: Gson,
    messages: List<DeepSeekChatMessage>,
    settings: DeepSeekTranslationSettings = DeepSeekTranslationSettings()
): String {
    require(messages.isNotEmpty())
    return gson.toJson(
        DeepSeekChatRequest(
            messages = messages,
            thinking = DeepSeekThinking(
                type = if (settings.thinkingEnabled) "enabled" else "disabled"
            ),
            reasoningEffort = settings.reasoningEffort.wireValue.takeIf {
                settings.thinkingEnabled
            },
            responseFormat = null,
            tools = subtitleTranslationTools()
        )
    )
}

internal fun buildSubtitleReadToolResultMessage(
    gson: Gson,
    toolCallId: String,
    sources: List<GeneratedSubtitleSource>,
    confirmedCaptions: List<GeneratedSubtitleCaption>
): DeepSeekChatMessage {
    require(toolCallId.isNotBlank())
    val confirmedSourceCount = confirmedCaptions.sumOf { it.sourceIndices.size }
    require(confirmedSourceCount in 0..sources.size)
    val nextSource = sources.getOrNull(confirmedSourceCount)
    val result = buildMap<String, Any> {
        put("japanese_subtitles", sources.map { source ->
            mapOf(
                "index" to source.index,
                "start_ms" to source.startMs,
                "end_ms" to source.endMs,
                "japanese" to source.text
            )
        })
        put("completed_chinese_subtitles", confirmedCaptions.map { caption ->
            mapOf(
                "source_indices" to caption.sourceIndices,
                "start_ms" to caption.startMs,
                "end_ms" to caption.endMs,
                "japanese" to caption.correctedJapanese,
                "chinese" to caption.chineseText
            )
        })
        put("completed_source_count", confirmedSourceCount)
        put("remaining_source_count", sources.size - confirmedSourceCount)
        put("completed", nextSource == null)
        nextSource?.let {
            put("next_untranslated_index", it.index)
            put("next_action", TranslationPrompts.subtitleProgressInstruction())
        }
    }
    return DeepSeekChatMessage(
        role = "tool",
        content = gson.toJson(result),
        toolCallId = toolCallId
    )
}

internal fun buildSubtitleWriteToolResultMessage(
    gson: Gson,
    toolCallId: String,
    writtenCaptionCount: Int,
    writtenSourceCount: Int,
    confirmedSourceCount: Int,
    sources: List<GeneratedSubtitleSource>
): DeepSeekChatMessage {
    require(toolCallId.isNotBlank())
    require(writtenCaptionCount > 0)
    require(writtenSourceCount > 0)
    require(confirmedSourceCount in writtenSourceCount..sources.size)
    val nextSource = sources.getOrNull(confirmedSourceCount)
    val result = buildMap<String, Any> {
        put("status", "written")
        put("written_caption_count", writtenCaptionCount)
        put("written_source_count", writtenSourceCount)
        put("completed_source_count", confirmedSourceCount)
        put("remaining_source_count", sources.size - confirmedSourceCount)
        put("completed", nextSource == null)
        nextSource?.let {
            put("next_untranslated_index", it.index)
            put("next_action", TranslationPrompts.subtitleProgressInstruction())
        }
    }
    return DeepSeekChatMessage(
        role = "tool",
        content = gson.toJson(result),
        toolCallId = toolCallId
    )
}

internal fun buildSubtitleToolErrorMessage(
    gson: Gson,
    toolCallId: String,
    message: String
): DeepSeekChatMessage = DeepSeekChatMessage(
    role = "tool",
    content = gson.toJson(
        mapOf(
            "status" to "error",
            "message" to message,
            "instruction" to TranslationPrompts.subtitleToolErrorInstruction()
        )
    ),
    toolCallId = toolCallId
)

private fun subtitleTranslationTools(): List<DeepSeekToolDefinition> = listOf(
    DeepSeekToolDefinition(
        function = DeepSeekFunctionDefinition(
            name = SUBTITLE_READ_TOOL_NAME,
            description = TranslationPrompts.subtitleToolReadDescription(),
            parameters = mapOf(
                "type" to "object",
                "properties" to emptyMap<String, Any>(),
                "additionalProperties" to false
            )
        )
    ),
    DeepSeekToolDefinition(
        function = DeepSeekFunctionDefinition(
            name = SUBTITLE_WRITE_TOOL_NAME,
            description = TranslationPrompts.subtitleToolWriteDescription(),
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "captions" to mapOf(
                        "type" to "array",
                        "minItems" to 1,
                        "items" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "source_indices" to mapOf(
                                    "type" to "array",
                                    "minItems" to 1,
                                    "items" to mapOf("type" to "integer")
                                ),
                                "start_ms" to mapOf(
                                    "type" to "integer",
                                    "minimum" to 0
                                ),
                                "end_ms" to mapOf(
                                    "type" to "integer",
                                    "minimum" to 0
                                ),
                                "japanese" to mapOf(
                                    "type" to "string",
                                    "minLength" to 1
                                ),
                                "chinese" to mapOf(
                                    "type" to "string",
                                    "minLength" to 1,
                                    "description" to TranslationPrompts.subtitleToolChineseFieldDescription()
                                )
                            ),
                            "required" to listOf(
                                "source_indices",
                                "start_ms",
                                "end_ms",
                                "japanese",
                                "chinese"
                            ),
                            "additionalProperties" to false
                        )
                    )
                ),
                "required" to listOf("captions"),
                "additionalProperties" to false
            )
        )
    )
)

private fun subtitleAgentContinueMessage(finishReason: String?): String =
    if (finishReason == "length") {
        TranslationPrompts.subtitleContinueMessageLengthLimited()
    } else {
        TranslationPrompts.subtitleContinueMessageGeneric()
    }

internal suspend fun <T> retrySubtitleTranslation(
    maxAttempts: Int = 4,
    onAttempt: suspend (attempt: Int, maxAttempts: Int) -> Unit = { _, _ -> },
    onRetry: suspend (nextAttempt: Int, maxAttempts: Int, reason: String) -> Unit = { _, _, _ -> },
    delayProvider: suspend (Long) -> Unit = { delay(it) },
    operation: suspend (previousError: SubtitleTranslationException?) -> T
): T {
    require(maxAttempts > 0)
    var lastError: SubtitleTranslationException? = null
    for (attempt in 1..maxAttempts) {
        onAttempt(attempt, maxAttempts)
        try {
            return operation(lastError)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: SubtitleTranslationException) {
            lastError = error
            if (!error.retryable || attempt == maxAttempts) throw error
            val exponentialDelay = BASE_RETRY_DELAY_MS * (1L shl (attempt - 1))
            onRetry(attempt + 1, maxAttempts, error.message.orEmpty().ifBlank { "未知原因" })
            delayProvider(maxOf(exponentialDelay, error.retryAfterMs ?: 0L))
        }
    }
    throw checkNotNull(lastError)
}

internal fun parseSubtitleWriteToolArguments(
    arguments: String,
    expectedRemainingSources: List<GeneratedSubtitleSource>,
    allowMerging: Boolean
): List<GeneratedSubtitleCaption> {
    require(expectedRemainingSources.isNotEmpty()) { "全部字幕已完成，无需继续写入" }
    val root = runCatching {
        JsonParser.parseString(arguments.trim()).asJsonObject
    }.getOrElse { error ->
        val reason = if (arguments.trimStart().startsWith('{') && !arguments.trimEnd().endsWith('}')) {
            "字幕工具调用参数被截断，未形成完整 JSON 对象"
        } else {
            "字幕工具调用参数不是有效 JSON 对象"
        }
        throw IllegalArgumentException(reason, error)
    }
    val captions = runCatching { root.getAsJsonArray("captions") }
        .getOrElse { throw IllegalArgumentException("captions 不是数组", it) }
        ?: throw IllegalArgumentException("缺少 captions 数组")
    val parsed = captions.map { element ->
        val item = element.takeIf { it.isJsonObject }?.asJsonObject
            ?: throw IllegalArgumentException("字幕组不是对象")
        val sourceIndices = runCatching {
            item.getAsJsonArray("source_indices").map { it.asInt }
        }.getOrElse { throw IllegalArgumentException("字幕组缺少 source_indices", it) }
        require(sourceIndices.isNotEmpty()) { "字幕组的 source_indices 不能为空" }
        val startMs = runCatching { item.get("start_ms").asLong }
            .getOrElse { throw IllegalArgumentException("字幕组缺少 start_ms", it) }
        val endMs = runCatching { item.get("end_ms").asLong }
            .getOrElse { throw IllegalArgumentException("字幕组缺少 end_ms", it) }
        val correctedJapanese = runCatching { item.get("japanese").asString.trim() }
            .getOrElse { throw IllegalArgumentException("字幕组缺少 japanese", it) }
        val chineseText = runCatching { item.get("chinese").asString.trim() }
            .getOrElse { throw IllegalArgumentException("字幕组缺少 chinese", it) }
        GeneratedSubtitleCaption(
            sourceIndices = sourceIndices,
            startMs = startMs,
            endMs = endMs,
            correctedJapanese = correctedJapanese,
            chineseText = chineseText
        )
    }
    return validateSubtitleCaptionBatch(
        captions = parsed,
        expectedRemainingSources = expectedRemainingSources,
        allowMerging = allowMerging
    )
}

private fun parseTranslationContentJsonObject(content: String) = content.trim()
    .removePrefix("```json")
    .removePrefix("```JSON")
    .removePrefix("```")
    .removeSuffix("```")
    .trim()
    .let { trimmed ->
        val objectStart = trimmed.indexOf('{')
        val objectEnd = trimmed.lastIndexOf('}')
        require(objectStart >= 0 && objectEnd > objectStart) { "响应正文中没有 JSON 对象" }
        runCatching {
            JsonParser.parseString(trimmed.substring(objectStart, objectEnd + 1)).asJsonObject
        }.getOrElse { throw IllegalArgumentException("无法解析 JSON", it) }
    }

internal fun subtitleToolTranslationSystemPrompt(
    targetIndices: List<Int>,
    allowMerging: Boolean
): String {
    require(targetIndices.isNotEmpty())
    require(targetIndices.distinct().size == targetIndices.size)
    val segmentationRules = if (allowMerging) {
        TranslationPrompts.subtitleSegmentationRulesMerge()
    } else {
        TranslationPrompts.subtitleSegmentationRulesNoMerge()
    }
    return TranslationPrompts.subtitleAgentSystemPromptTemplate()
        .replace("{{SOURCE_COUNT}}", targetIndices.size.toString())
        .replace("{{READ_TOOL_NAME}}", SUBTITLE_READ_TOOL_NAME)
        .replace("{{WRITE_TOOL_NAME}}", SUBTITLE_WRITE_TOOL_NAME)
        .replace("{{STYLE_GUIDE}}", TranslationPrompts.subtitleStyleGuide())
        .replace("{{ADULT_REFERENCE_TABLE}}", TranslationPrompts.subtitleAdultReferenceTable())
        .replace("{{SEGMENTATION_RULES}}", segmentationRules)
        .trim()
}

private const val BASE_RETRY_DELAY_MS = 1_000L
