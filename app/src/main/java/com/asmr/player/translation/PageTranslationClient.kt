package com.asmr.player.translation

import com.asmr.player.data.remote.NetworkHeaders
import com.google.gson.JsonParser
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor

internal class PageTranslationException(message: String, val cooldownMillis: Long = 30_000) : IOException(message)

/** A best-effort public service: no API key, quota or availability guarantee. */
internal class PageTranslationClient(
    private val calls: Call.Factory,
    private val endpoint: String = "https://translate.googleapis.com/translate_a/t",
) {
    @Inject constructor(client: OkHttpClient) : this(
        calls = client.newBuilder()
            // Translation requests contain user-visible titles and local display names.
            .apply { interceptors().removeAll { it is HttpLoggingInterceptor } }
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build(),
    )

    suspend fun translateBatch(texts: List<String>, target: String): List<String> {
        require(texts.isNotEmpty() && texts.size <= PageTranslationBatchMaxItems)
        require(texts.sumOf(String::length) <= PageTranslationBatchMaxCharacters)
        val url = endpoint.toHttpUrl().newBuilder()
            .addQueryParameter("client", "dict-chrome-ex")
            .addQueryParameter("sl", "auto")
            .addQueryParameter("tl", target)
            .build()
        val request = Request.Builder().url(url)
            .post(FormBody.Builder().apply { texts.forEach { add("q", it) } }.build())
            .header(NetworkHeaders.HEADER_SILENT_IO_ERROR, NetworkHeaders.SILENT_IO_ERROR_ON)
            .build()
        return suspendCancellableCoroutine { continuation ->
            val call = calls.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching {
                        response.use {
                            if (it.code == 429 || it.code == 403) {
                                val cooldown = it.header("Retry-After")?.toLongOrNull()
                                    ?.coerceIn(60, 86_400)?.times(1_000) ?: 60_000
                                if (it.peekBody(8_192).string().contains("unusual traffic", ignoreCase = true)) {
                                    throw PageTranslationException(
                                        "Google 检测到当前网络异常流量，请检查代理或稍后再试",
                                        maxOf(cooldown, 300_000),
                                    )
                                }
                                throw PageTranslationException("翻译服务暂时限流，请稍后重试", cooldown)
                            }
                            if (!it.isSuccessful) throw PageTranslationException("翻译服务暂不可用（${it.code}）")
                            parsePageTranslationBatch(it.body?.string().orEmpty(), texts.size)
                        }
                    }
                    if (continuation.isActive) result.fold(continuation::resume, continuation::resumeWithException)
                }
            })
        }
    }
}

internal fun parsePageTranslationBatch(body: String, expectedCount: Int): List<String> = try {
    val results = JsonParser.parseString(body).asJsonArray
    require(expectedCount > 0 && results.size() == expectedCount)
    results.map { item ->
        // The batch endpoint returns strings, or [translation, detected language] pairs.
        val value = if (item.isJsonArray) {
            val pair = item.asJsonArray
            require(pair.size() == 2 && pair[1].isJsonPrimitive && pair[1].asJsonPrimitive.isString)
            pair[0]
        } else item
        require(value.isJsonPrimitive && value.asJsonPrimitive.isString)
        value.asString.also { require(it.isNotBlank()) }
    }
} catch (error: Exception) {
    throw PageTranslationException("翻译服务返回了无效内容，请稍后重试")
}

internal data class PageTranslationText(val source: String, val suffix: String = "")

// Conservative client limits; keep a normal viewport in one POST without unbounded payloads.
internal const val PageTranslationBatchMaxCharacters = 4_000
internal const val PageTranslationBatchMaxItems = 50

internal fun pageTranslationBatches(texts: List<String>): List<List<String>> {
    val batches = mutableListOf<List<String>>()
    var current = mutableListOf<String>()
    var characters = 0
    texts.forEach { text ->
        require(text.length <= PageTranslationBatchMaxCharacters)
        if (current.isNotEmpty() && (current.size == PageTranslationBatchMaxItems || characters + text.length > PageTranslationBatchMaxCharacters)) {
            batches += current
            current = mutableListOf()
            characters = 0
        }
        current += text
        characters += text.length
    }
    if (current.isNotEmpty()) batches += current
    return batches
}

private val FileSuffix = Regex("(?i)\\.(wav|mp3|flac|m4a|aac|ogg|opus|wma|aiff|mp4|mkv|webm|avi|mov|jpg|jpeg|png|webp|gif|bmp|txt|srt|vtt|lrc|ass|pdf|zip|rar|7z)$")
private val WorkNumber = Regex("(?i)^(RJ|BJ|VJ)\\d+$")

internal fun pageTranslationText(text: String, fileName: Boolean): PageTranslationText {
    val suffix = if (fileName) FileSuffix.find(text)?.value.orEmpty() else ""
    return PageTranslationText(text.removeSuffix(suffix), suffix)
}

internal fun shouldTranslatePageText(text: String): Boolean =
    text.isNotBlank() && text.length <= PageTranslationBatchMaxCharacters && text.any(Char::isLetter) && !WorkNumber.matches(text)
