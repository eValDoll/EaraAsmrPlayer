package com.asmr.player.data.remote.dlsite

import android.content.Context
import android.util.Log
import com.asmr.player.data.remote.NetworkHeaders
import com.asmr.player.data.remote.api.AsmrOneTrackNodeResponse
import com.asmr.player.data.remote.auth.DlsiteAuthStore
import com.asmr.player.util.DlsiteWorkNo
import com.asmr.player.util.RemoteSubtitleSource
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DlsitePlayWorkClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    @ApplicationContext context: Context
) {
    private val authStore = DlsiteAuthStore(context)
    private val gson = Gson()

    suspend fun fetchPlayableTree(workno: String): DlsitePlayTreeResult = withContext(Dispatchers.IO) {
        val clean = DlsiteWorkNo.extractRjCode(workno)
        if (clean.isBlank()) return@withContext DlsitePlayTreeResult(emptyList(), emptyMap())

        val cookie = authStore.getPlayCookie().trim()
        if (cookie.isBlank()) {
            throw IllegalStateException("请先登录 DLsite（需要 play.dlsite.com 的 Cookie）")
        }

        val (baseUrl, params, revision) = fetchDownloadSign(clean, cookie)

        val ziptree = fetchZiptree(baseUrl, params, cookie)
        val tree = (ziptree["tree"] as? List<*>)?.mapNotNull { it as? Map<*, *> }.orEmpty()
        val playfile = ziptree["playfile"] as? Map<*, *>
        val rev = (ziptree["revision"] as? String)?.trim().orEmpty().ifBlank { revision }

        val fileToDisplay = LinkedHashMap<String, String>()
        val dirToFiles = LinkedHashMap<String, MutableList<Map<String, String>>>()

        fun walk(nodes: List<Map<*, *>>, parentPath: String) {
            nodes.forEach { n ->
                val type = (n["type"] as? String).orEmpty()
                when (type) {
                    "folder" -> {
                        val pth = ((n["path"] as? String).orEmpty().ifBlank { parentPath }).trim()
                        val children = (n["children"] as? List<*>)?.mapNotNull { it as? Map<*, *> }.orEmpty()
                        walk(children, pth)
                    }
                    "file" -> {
                        val hn = (n["hashname"] as? String).orEmpty().trim()
                        val nm = (n["name"] as? String).orEmpty().trim()
                        if (hn.isBlank()) return@forEach
                        val display = if (parentPath.isNotBlank()) {
                            if (nm.isNotBlank()) "$parentPath/$nm" else parentPath
                        } else {
                            nm.ifBlank { hn }
                        }
                        fileToDisplay[hn] = display
                        dirToFiles.getOrPut(parentPath) { mutableListOf() }.add(mapOf("name" to nm.ifBlank { hn }, "hashname" to hn))
                    }
                }
            }
        }
        walk(tree, "")

        val vttOpt = LinkedHashMap<String, String>()
        if (playfile != null) {
            playfile.forEach { (k, v) ->
                val hn = k?.toString().orEmpty().trim()
                val meta = v as? Map<*, *> ?: return@forEach
                if ((meta["type"] as? String).orEmpty() != "vtt") return@forEach
                val vtt = meta["vtt"] as? Map<*, *> ?: return@forEach
                val opt = vtt["optimized"] as? Map<*, *> ?: return@forEach
                val name = (opt["name"] as? String).orEmpty().trim()
                if (hn.isNotBlank() && name.isNotBlank()) vttOpt[hn] = name
            }
        }

        val q = buildQuery(mapOf("v" to rev) + params)
        val audioUrlByHash = LinkedHashMap<String, String>()
        val videoUrlByHash = LinkedHashMap<String, String>()
        val durationByHash = LinkedHashMap<String, Double?>()
        val audioLeaves = mutableListOf<DlsitePlayLeaf>()
        val videoLeaves = mutableListOf<DlsitePlayLeaf>()

        if (playfile != null) {
            playfile.forEach { (k, v) ->
                val origHash = k?.toString().orEmpty().trim()
                val meta = v as? Map<*, *> ?: return@forEach
                val type = (meta["type"] as? String).orEmpty()
                when (type) {
                    "audio" -> {
                        val audio = meta["audio"] as? Map<*, *> ?: return@forEach
                        val opt = audio["optimized"] as? Map<*, *> ?: return@forEach
                        val optName = (opt["name"] as? String).orEmpty().trim()
                        if (optName.isBlank()) return@forEach

                        val streamUrl = "${baseUrl}optimized/$optName?$q"
                        val display = fileToDisplay[origHash].orEmpty().ifBlank { origHash }
                        val (folder, filename) = splitFolder(display)
                        val subtitles = buildSubtitles(
                            baseUrl = baseUrl,
                            query = q,
                            folder = folder,
                            filename = filename,
                            dirToFiles = dirToFiles,
                            vttOpt = vttOpt
                        )
                        val duration = (opt["duration"] as? Number)?.toDouble()
                        audioUrlByHash[origHash] = streamUrl
                        durationByHash[origHash] = duration
                        audioLeaves.add(
                            DlsitePlayLeaf(
                                displayPath = display,
                                url = streamUrl,
                                duration = duration,
                                subtitles = subtitles
                            )
                        )
                    }
                    "video", "movie" -> {
                        val video = (meta["video"] as? Map<*, *>) ?: (meta["movie"] as? Map<*, *>)
                        val opt = video?.get("optimized") as? Map<*, *> ?: return@forEach
                        val optName = (opt["name"] as? String).orEmpty().trim()
                        if (optName.isBlank()) return@forEach

                        val streamUrl = "${baseUrl}optimized/$optName?$q"
                        val display = fileToDisplay[origHash].orEmpty().ifBlank { origHash }
                        val (folder, filename) = splitFolder(display)
                        val subtitles = buildSubtitles(
                            baseUrl = baseUrl,
                            query = q,
                            folder = folder,
                            filename = filename,
                            dirToFiles = dirToFiles,
                            vttOpt = vttOpt
                        )
                        val duration = (opt["duration"] as? Number)?.toDouble()
                        videoUrlByHash[origHash] = streamUrl
                        durationByHash[origHash] = duration
                        videoLeaves.add(
                            DlsitePlayLeaf(
                                displayPath = display,
                                url = streamUrl,
                                duration = duration,
                                subtitles = subtitles
                            )
                        )
                    }
                }
            }
        }

        val subtitleMap = LinkedHashMap<String, List<RemoteSubtitleSource>>()
        (audioLeaves + videoLeaves).forEach { leaf ->
            if (leaf.subtitles.isNotEmpty()) {
                subtitleMap[leaf.url] = leaf.subtitles
            }
        }

        val subtitleUrlByHash = LinkedHashMap<String, String>()
        vttOpt.forEach { (hn, optName) ->
            if (hn.isBlank() || optName.isBlank()) return@forEach
            subtitleUrlByHash[hn] = "${baseUrl}optimized/$optName?$q"
        }

        val files = fileToDisplay.entries.mapNotNull { (hn, display) ->
            val fileName = display.substringAfterLast('/').trim().ifBlank { display.trim() }
            val kind = fileKindForName(fileName)
            if (kind == DlsiteFileKind.Other) return@mapNotNull null
            val url = when (kind) {
                DlsiteFileKind.Audio -> audioUrlByHash[hn]
                DlsiteFileKind.Subtitle -> subtitleUrlByHash[hn]
                DlsiteFileKind.Video -> videoUrlByHash[hn]
                else -> null
            }
            DlsitePlayFileEntry(
                displayPath = display,
                url = url,
                duration = durationByHash[hn]
            )
        }

        val treeNodes = buildTreeFromFiles(files)
        DlsitePlayTreeResult(treeNodes, subtitleMap)
    }

    private fun fetchDownloadSign(workno: String, cookie: String): Triple<String, Map<String, String>, String> {
        val request = Request.Builder()
            .url("https://play.dlsite.com/api/v3/download/sign/url?workno=${URLEncoder.encode(workno, Charsets.UTF_8.name())}")
            .header("Cookie", cookie)
            .header("Referer", "https://play.dlsite.com/")
            .header("Accept", "application/json, text/plain, */*")
            .header("User-Agent", UA)
            .header(NetworkHeaders.HEADER_SILENT_IO_ERROR, NetworkHeaders.SILENT_IO_ERROR_ON)
            .get()
            .build()

        okHttpClient.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                Log.w(TAG, "sign/url failed: ${resp.code}, body=${body.take(300)}")
                throw IllegalStateException("DLsite Play 获取签名失败（${resp.code}）")
            }
            val obj = runCatching { gson.fromJson(body, Map::class.java) as? Map<*, *> }.getOrNull().orEmpty()
            val baseUrl = (obj["url"] as? String).orEmpty().trim()
            val paramsAny = obj["params"] as? Map<*, *>
            val params = paramsAny?.entries
                ?.mapNotNull { (k, v) ->
                    val key = k?.toString().orEmpty().trim()
                    val value = v?.toString().orEmpty().trim()
                    if (key.isBlank() || value.isBlank()) null else key to value
                }
                ?.toMap()
                .orEmpty()
            if (baseUrl.isBlank() || params.isEmpty()) {
                throw IllegalStateException("DLsite Play 获取签名返回缺少 url/params")
            }

            val nowSec = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())
            val ziptreeV = (nowSec - (nowSec % 60)).toString()
            return Triple(baseUrl, params, ziptreeV)
        }
    }

    private fun fetchZiptree(baseUrl: String, params: Map<String, String>, cookie: String): Map<String, Any?> {
        val nowSec = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())
        val ziptreeV = (nowSec - (nowSec % 60)).toString()
        val q = buildQuery(mapOf("v" to ziptreeV) + params)
        val url = "${baseUrl}ziptree.json?$q"
        val request = Request.Builder()
            .url(url)
            .header("Cookie", cookie)
            .header("Referer", "https://play.dlsite.com/")
            .header("Accept", "application/json, text/plain, */*")
            .header("User-Agent", UA)
            .header(NetworkHeaders.HEADER_SILENT_IO_ERROR, NetworkHeaders.SILENT_IO_ERROR_ON)
            .get()
            .build()

        okHttpClient.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                Log.w(TAG, "ziptree.json failed: ${resp.code}, body=${body.take(300)}")
                throw IllegalStateException("DLsite Play 获取目录树失败（${resp.code}）")
            }
            @Suppress("UNCHECKED_CAST")
            return runCatching { gson.fromJson(body, Map::class.java) as? Map<String, Any?> }.getOrNull().orEmpty()
        }
    }

    private fun buildTreeFromFiles(files: List<DlsitePlayFileEntry>): List<AsmrOneTrackNodeResponse> {
        data class Node(
            val title: String,
            val children: LinkedHashMap<String, Node> = linkedMapOf(),
            var url: String? = null,
            var duration: Double? = null
        )

        val root = Node(title = "")
        fun getOrCreateChild(parent: Node, title: String): Node {
            return parent.children.getOrPut(title) { Node(title = title) }
        }

        files.forEach { file ->
            val parts = file.displayPath.split('/').filter { it.isNotBlank() }
            if (parts.isEmpty()) return@forEach
            var cur = root
            parts.dropLast(1).forEach { seg -> cur = getOrCreateChild(cur, seg) }
            val fileName = parts.last()
            val node = getOrCreateChild(cur, fileName)
            node.url = file.url
            node.duration = file.duration
        }

        fun toResponse(node: Node): AsmrOneTrackNodeResponse {
            val kids = node.children.values.map { toResponse(it) }
            return AsmrOneTrackNodeResponse(
                title = node.title,
                children = kids.takeIf { it.isNotEmpty() },
                duration = node.duration,
                streamUrl = node.url,
                mediaDownloadUrl = node.url
            )
        }

        return root.children.values.map { toResponse(it) }
    }

    private fun buildSubtitles(
        baseUrl: String,
        query: String,
        folder: String,
        filename: String,
        dirToFiles: Map<String, List<Map<String, String>>>,
        vttOpt: Map<String, String>
    ): List<RemoteSubtitleSource> {
        if (filename.isBlank()) return emptyList()
        val candidates = dirToFiles[folder].orEmpty()
        val out = mutableListOf<RemoteSubtitleSource>()
        candidates.forEach { f ->
            val nm = f["name"].orEmpty().trim()
            val hn = f["hashname"].orEmpty().trim()
            if (nm.isBlank() || hn.isBlank()) return@forEach
            val low = nm.lowercase()
            if (!low.endsWith(".vtt")) return@forEach
            val lang = when {
                nm == "$filename.vtt" -> "default"
                nm.startsWith("$filename.") && low.endsWith(".vtt") -> nm.substring(filename.length + 1, nm.length - 4).ifBlank { "default" }
                else -> return@forEach
            }
            val optName = vttOpt[hn].orEmpty().trim()
            if (optName.isBlank()) return@forEach
            val subUrl = "${baseUrl}optimized/$optName?$query"
            out.add(RemoteSubtitleSource(url = subUrl, language = lang, ext = "vtt"))
        }
        return out
    }

    private fun splitFolder(display: String): Pair<String, String> {
        val trimmed = display.trim()
        val idx = trimmed.lastIndexOf('/')
        return if (idx >= 0) trimmed.substring(0, idx) to trimmed.substring(idx + 1) else "" to trimmed
    }

    private fun buildQuery(params: Map<String, String>): String {
        return params.entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, Charsets.UTF_8.name())}=${URLEncoder.encode(v, Charsets.UTF_8.name())}"
        }
    }

    private data class DlsitePlayLeaf(
        val displayPath: String,
        val url: String,
        val duration: Double?,
        val subtitles: List<RemoteSubtitleSource>
    )

    private data class DlsitePlayFileEntry(
        val displayPath: String,
        val url: String?,
        val duration: Double?
    )

    private enum class DlsiteFileKind { Audio, Subtitle, Image, Video, Text, Pdf, Other }

    private fun fileKindForName(fileName: String): DlsiteFileKind {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "mp3", "wav", "flac", "m4a", "ogg", "aac", "opus" -> DlsiteFileKind.Audio
            "vtt", "lrc", "srt" -> DlsiteFileKind.Subtitle
            "jpg", "jpeg", "png", "webp", "gif" -> DlsiteFileKind.Image
            "mp4", "mkv", "webm" -> DlsiteFileKind.Video
            "txt", "md", "nfo" -> DlsiteFileKind.Text
            "pdf" -> DlsiteFileKind.Pdf
            else -> DlsiteFileKind.Other
        }
    }

    companion object {
        private const val TAG = "DlsitePlayWork"
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}

data class DlsitePlayTreeResult(
    val tree: List<AsmrOneTrackNodeResponse>,
    val subtitlesByUrl: Map<String, List<RemoteSubtitleSource>>
)
