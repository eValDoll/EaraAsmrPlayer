package com.asmr.player.data.remote.download

import com.asmr.player.data.remote.NetworkHeaders
import com.asmr.player.data.remote.crawler.isJapaneseAsmrAudioName
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.IOException

internal fun isBuzzheavierUrl(url: String): Boolean {
    val host = url.toHttpUrlOrNull()?.host ?: return false
    return host == "buzzheavier.com" || host.endsWith(".buzzheavier.com")
}

/** Resolve at transfer time so expiring file links are refreshed on retries. */
internal fun resolveJapaneseAsmrDownload(client: OkHttpClient, url: String, name: String): String {
    if (!isJapaneseAsmrAudioName(name)) throw IOException("不支持的音频格式")
    if (!isBuzzheavierUrl(url)) return url
    val request = Request.Builder().url(url).header("User-Agent", NetworkHeaders.USER_AGENT).build()
    client.newCall(request).execute().use { page ->
        if (!page.isSuccessful) throw IOException("音频下载站点请求失败 (${page.code})")
        val contentType = page.header("Content-Type").orEmpty().substringBefore(';').lowercase()
        if (contentType.startsWith("audio/") || contentType == "application/octet-stream" || contentType == "application/mp4") {
            return page.request.url.toString()
        }
        val pageUrl = page.request.url
        if (!isBuzzheavierUrl(pageUrl.toString())) throw IOException("下载链接未返回音频")
        val document = Jsoup.parse(page.body?.string().orEmpty(), pageUrl.toString())
        val endpoint = document.selectFirst("[hx-get]")?.absUrl("hx-get")
            ?.takeIf(::isBuzzheavierUrl)
            ?: pageUrl.newBuilder().addPathSegment("download").build().toString()
        client.newCall(Request.Builder().url(endpoint)
            .header("User-Agent", NetworkHeaders.USER_AGENT)
            .header("Referer", pageUrl.toString()).header("HX-Request", "true").build())
            .execute().use { response ->
                if (!response.isSuccessful) throw IOException("音频下载链接解析失败 (${response.code})")
                val direct = response.header("HX-Redirect").orEmpty()
                val parsed = direct.toHttpUrlOrNull()
                if (parsed?.scheme != "https") throw IOException("未找到音频下载链接")
                return parsed.toString()
            }
    }
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
