package com.asmr.player.data.remote.crawler

import com.asmr.player.data.remote.NetworkHeaders
import com.asmr.player.data.remote.awaitResponse
import com.asmr.player.data.remote.api.AsmrOneTrackNodeResponse
import com.asmr.player.util.ChapterMediaReference
import com.asmr.player.util.DlsiteWorkNo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import javax.inject.Inject
import javax.inject.Singleton

enum class AlbumResourceSource(val label: String) {
    AsmrOne("asmr.one"), JapaneseAsmr("Japanese ASMR")
}

data class JapaneseAsmrWork(val pageUrl: String, val tree: List<AsmrOneTrackNodeResponse>)
internal data class JapaneseAsmrDownload(val name: String, val url: String)

@Singleton
class JapaneseAsmrClient @Inject constructor(private val client: OkHttpClient) {
    suspend fun load(workNo: String): JapaneseAsmrWork = withContext(Dispatchers.IO) {
        val rj = DlsiteWorkNo.normalizeWorkNo(workNo, minimumDigits = 6)
        require(rj.isNotBlank())
        val search = read("$BASE/?s=$rj")
        val candidates = search.select(".entry-title a[href]").map { it.absUrl("href") }
            .filter { it.toHttpUrl().host == "japaneseasmr.com" }.distinct().take(12)
        for (url in candidates) {
            coroutineContext.ensureActive()
            val page = read(url)
            if (!japaneseAsmrPageMatches(page, rj)) continue
            // A download listing failure must not prevent streaming chapters.
            val downloads = try {
                parseJapaneseAsmrDownloads(read("$BASE/dlc.php?f=$rj"), rj)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
            return@withContext JapaneseAsmrWork(url, parseJapaneseAsmrTree(page, rj, downloads))
        }
        JapaneseAsmrWork("", emptyList())
    }

    private suspend fun read(url: String): Document {
        client.newCall(Request.Builder().url(url).header("User-Agent", NetworkHeaders.USER_AGENT).build())
            .awaitResponse().use { response ->
                check(response.isSuccessful) { "资源站点请求失败 (${response.code})" }
                return Jsoup.parse(checkNotNull(response.body).string(), response.request.url.toString())
            }
    }

    companion object { const val BASE = "https://japaneseasmr.com" }
}

internal fun japaneseAsmrPageMatches(page: Document, rj: String): Boolean {
    val identity = page.select("#work_title_jp, #cleanp_audio video, #play-title").joinToString(" ") {
        it.text() + " " + it.attr("title")
    }
    return Regex("(?i)(?<![A-Z0-9])${Regex.escape(rj)}(?![0-9])").containsMatchIn(identity)
}

internal fun parseJapaneseAsmrDownloads(page: Document, rj: String): List<JapaneseAsmrDownload> =
    page.select("a[href]").mapNotNull { link ->
        val name = link.text().trim().removePrefix("$rj - ")
        val url = link.absUrl("href")
        if (!link.text().startsWith(rj, ignoreCase = true) || !isJapaneseAsmrAudioName(name) ||
            !url.startsWith("https://")) null else JapaneseAsmrDownload(name, url)
    }.distinctBy { it.name }

internal fun isJapaneseAsmrAudioName(name: String): Boolean =
    name.substringAfterLast('.', "").lowercase() in setOf("mp3", "m4a", "flac", "opus")

internal fun parseJapaneseAsmrTree(
    page: Document, rj: String, downloads: List<JapaneseAsmrDownload>
): List<AsmrOneTrackNodeResponse> {
    val players = page.select(".cleanPlayer, .audio_main")
    val usedStreams = mutableSetOf<String>()
    val nodes = players.mapIndexedNotNull { playerIndex, player ->
        val media = player.selectFirst("video, audio") ?: return@mapIndexedNotNull null
        val stream = media.absUrl("src").ifBlank { media.selectFirst("source[src]")?.absUrl("src").orEmpty() }
        if (!stream.startsWith("https://") || !usedStreams.add(stream)) return@mapIndexedNotNull null
        val chapters = player.select("a[data-track-title][data-value]").mapNotNull { link ->
            val seconds = link.attr("data-value").toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 && it < 604800 }
                ?: return@mapNotNull null
            (seconds * 1000).toLong() to link.attr("data-track-title").trim()
        }.distinctBy { it.first }.sortedBy { it.first }.toMutableList()
        if (chapters.isEmpty() || chapters.first().first > 0) chapters.add(0, 0L to media.attr("descr").ifBlank { "本編" })
        val leaves = chapters.mapIndexed { index, (start, title) ->
            val end = chapters.getOrNull(index + 1)?.first
            val download = downloads.firstOrNull { it.name.substringBeforeLast('.').replace(Regex("^\\d+_"), "") == title }
                ?: downloads.singleOrNull().takeIf { chapters.size == 1 }
            val name = download?.name ?: "${(index + 1).toString().padStart(2, '0')}_${title.ifBlank { rj }}.m4a"
            AsmrOneTrackNodeResponse(title = name, type = "audio",
                duration = end?.let { (it - start) / 1000.0 },
                streamUrl = ChapterMediaReference(stream, start, end, download?.url, name).encode(),
                mediaDownloadUrl = download?.url)
        }
        (media.attr("descr").ifBlank { "音频 ${playerIndex + 1}" }) to leaves
    }
    return if (nodes.isEmpty()) emptyList() else listOf(
        AsmrOneTrackNodeResponse(title = "japaneseasmr.com", type = "folder",
            children = if (nodes.size == 1) nodes.single().second else nodes.mapIndexed { index, (title, leaves) ->
                AsmrOneTrackNodeResponse(title = "${index + 1}_$title", type = "folder", children = leaves)
            })
    )
}
