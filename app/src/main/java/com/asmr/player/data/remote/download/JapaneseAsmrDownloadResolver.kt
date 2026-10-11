package com.asmr.player.data.remote.download

import com.asmr.player.data.remote.NetworkHeaders
import com.asmr.player.data.remote.crawler.isJapaneseAsmrAudioName
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.io.IOException
import kotlin.coroutines.resumeWithException

internal class JapaneseAsmrSourceFileMissingException : IOException("源文件已丢失")

private fun requireJapaneseAsmrSourceResponse(code: Int) {
    if (code == 404 || code == 410) throw JapaneseAsmrSourceFileMissingException()
    if (code !in 200..299) throw IOException("音频下载站点请求失败 ($code)")
}

internal fun isBuzzheavierUrl(url: String): Boolean {
    val host = url.toHttpUrlOrNull()?.host ?: return false
    return host == "buzzheavier.com" || host.endsWith(".buzzheavier.com")
}

/** Resolve at transfer time so expiring file links are refreshed on retries. */
internal suspend fun resolveJapaneseAsmrDownload(client: OkHttpClient, url: String, name: String): String {
    if (!isJapaneseAsmrAudioName(name)) throw IOException("不支持的音频格式")
    if (!isBuzzheavierUrl(url)) return url
    val request = Request.Builder().url(url).header("User-Agent", NetworkHeaders.USER_AGENT)
        .header(NetworkHeaders.HEADER_SILENT_IO_ERROR, NetworkHeaders.SILENT_IO_ERROR_ON).build()
    val (pageUrl, endpoint) = withJapaneseAsmrResponse(client, request) { page ->
        requireJapaneseAsmrSourceResponse(page.code)
        val contentType = page.header("Content-Type").orEmpty().substringBefore(';').lowercase()
        if (contentType.startsWith("audio/") || contentType == "application/octet-stream" || contentType == "application/mp4") {
            return@withJapaneseAsmrResponse page.request.url to null
        }
        val pageUrl = page.request.url
        if (!isBuzzheavierUrl(pageUrl.toString())) throw IOException("下载链接未返回音频")
        val document = Jsoup.parse(page.body?.string().orEmpty(), pageUrl.toString())
        val endpoint = document.selectFirst("[hx-get]")?.absUrl("hx-get")
            ?.takeIf(::isBuzzheavierUrl)
            ?: pageUrl.newBuilder().addPathSegment("download").build().toString()
        pageUrl to endpoint
    }
    if (endpoint == null) return pageUrl.toString()
    return withJapaneseAsmrResponse(client, Request.Builder().url(endpoint)
        .header("User-Agent", NetworkHeaders.USER_AGENT)
        .header(NetworkHeaders.HEADER_SILENT_IO_ERROR, NetworkHeaders.SILENT_IO_ERROR_ON)
        .header("Referer", pageUrl.toString()).header("HX-Request", "true").build()) { response ->
        requireJapaneseAsmrSourceResponse(response.code)
        val parsed = response.header("HX-Redirect").orEmpty().toHttpUrlOrNull()
        if (parsed?.scheme != "https") throw IOException("未找到音频下载链接")
        parsed.toString()
    }
}

internal suspend fun <T> withJapaneseAsmrResponse(
    client: OkHttpClient,
    request: Request,
    read: (Response) -> T,
): T = suspendCancellableCoroutine { continuation ->
    val call = client.newCall(request)
    continuation.invokeOnCancellation { call.cancel() }
    call.enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            if (!continuation.isActive) {
                response.close()
                return
            }
            continuation.resumeWith(runCatching { response.use(read) })
        }
    })
}

internal fun isJapaneseAsmrAudioHeader(name: String, header: ByteArray): Boolean {
    fun starts(value: String, offset: Int = 0): Boolean = header.size >= offset + value.length &&
        value.indices.all { header[offset + it] == value[it].code.toByte() }
    return when (name.substringAfterLast('.', "").lowercase()) {
        "m4a" -> starts("ftyp", 4)
        "flac" -> starts("fLaC") || starts("ID3")
        "opus" -> starts("OggS")
        "mp3" -> starts("ID3") || (header.size >= 2 && header[0].toInt() and 0xff == 0xff &&
            header[1].toInt() and 0xe0 == 0xe0)
        else -> false
    }
}
