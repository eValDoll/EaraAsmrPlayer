package com.asmr.player.data.remote.dlsite

import android.content.Context
import android.util.Log
import com.asmr.player.data.remote.NetworkHeaders
import com.asmr.player.data.remote.api.AsmrOneTrackNodeResponse
import com.asmr.player.data.remote.auth.DlsiteAuthStore
import com.asmr.player.util.DlsiteWorkNo
import com.asmr.player.util.RemoteSubtitleSource
import com.asmr.player.util.SubtitleMatchSupport
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
        if (clean.isBlank()) return@withContext DlsitePlayTreeResult(
            tree = emptyList(),
            subtitlesByUrl = emptyMap(),
            status = DlsitePlayLoadStatus.NotAvailable
        )

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
        val optimizedUrlByHash = LinkedHashMap<String, String>()
        val durationByHash = LinkedHashMap<String, Double?>()
        val audioLeaves = mutableListOf<DlsitePlayLeaf>()
        val videoLeaves = mutableListOf<DlsitePlayLeaf>()

        if (playfile != null) {
            playfile.forEach { (k, v) ->
                val origHash = k?.toString().orEmpty().trim()
                if (origHash.isBlank()) return@forEach
                val meta = v as? Map<*, *> ?: return@forEach
                val type = (meta["type"] as? String).orEmpty()
                val opt = optimizedBlock(meta, type)
                val optName = (opt?.get("name") as? String).orEmpty().trim()
                val optimizedUrl = if (optName.isNotBlank()) "${baseUrl}optimized/$optName?$q" else null
                if (optimizedUrl != null) {
                    optimizedUrlByHash[origHash] = optimizedUrl
                }
                when (type) {
                    "audio" -> {
                        if (opt == null || optimizedUrl == null) return@forEach

                        val streamUrl = optimizedUrl
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
                        val duration = parseDuration(opt["duration"])
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
                        if (opt == null || optimizedUrl == null) return@forEach

                        val streamUrl = optimizedUrl
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
                        val duration = parseDuration(opt["duration"])
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
                DlsiteFileKind.Image,
                DlsiteFileKind.Text,
                DlsiteFileKind.Pdf -> optimizedUrlByHash[hn]
                else -> null
            }
            if (url.isNullOrBlank() && kind != DlsiteFileKind.Pdf) return@mapNotNull null
            val imageMeta = if (kind == DlsiteFileKind.Image) {
                val meta = playfile?.get(hn) as? Map<*, *>
                val opt = meta?.let { optimizedBlock(it, "image") }
                val optimizedName = (opt?.get("name") as? String).orEmpty().trim()
                if (opt != null && optimizedName.isNotBlank()) {
                    val rawCrypt = opt["crypt"]
                    val crypt = parseDlsitePlayCryptFlag(rawCrypt)
                    val width = parseInt(opt["width"])
                    val height = parseInt(opt["height"])
                    DlsitePlayImageMeta(
                        crypt = crypt,
                        width = width,
                        height = height,
                        optimizedName = optimizedName
                    )
                } else {
                    null
                }
            } else {
                null
            }
            DlsitePlayFileEntry(
                displayPath = display,
                url = url,
                duration = durationByHash[hn],
                imageMeta = imageMeta
            )
        }

        val treeNodes = buildTreeFromFiles(files)
        DlsitePlayTreeResult(
            tree = treeNodes,
            subtitlesByUrl = subtitleMap,
            status = if (treeNodes.isNotEmpty()) DlsitePlayLoadStatus.Success else DlsitePlayLoadStatus.NotAvailable
        )
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
            if (!resp.isSuccessful) {
                val body = resp.body?.string().orEmpty()
                Log.w(TAG, "sign/url failed: ${resp.code}, body=${body.take(300)}")
                throw IllegalStateException("DLsite Play 获取签名失败（${resp.code}）")
            }
            val body = resp.body ?: throw IllegalStateException("DLsite Play 获取签名返回为空")
            val obj = body.charStream().use { reader ->
                runCatching { gson.fromJson(reader, Map::class.java) }.getOrNull().orEmpty()
            }
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
            if (!resp.isSuccessful) {
                val body = resp.body?.string().orEmpty()
                Log.w(TAG, "ziptree.json failed: ${resp.code}, body=${body.take(300)}")
                throw IllegalStateException("DLsite Play 获取目录树失败（${resp.code}）")
            }
            val body = resp.body ?: throw IllegalStateException("DLsite Play 获取目录树为空")
            @Suppress("UNCHECKED_CAST")
            return body.charStream().use { reader ->
                runCatching { gson.fromJson(reader, Map::class.java) as? Map<String, Any?> }.getOrNull().orEmpty()
            }
        }
    }

    private fun buildTreeFromFiles(files: List<DlsitePlayFileEntry>): List<AsmrOneTrackNodeResponse> {
        data class Node(
            val title: String,
            val children: LinkedHashMap<String, Node> = linkedMapOf(),
            var url: String? = null,
            var duration: Double? = null,
            var imageMeta: DlsitePlayImageMeta? = null
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
            node.imageMeta = file.imageMeta
        }

        fun toResponse(node: Node): AsmrOneTrackNodeResponse {
            val kids = node.children.values.map { toResponse(it) }
            val resp = AsmrOneTrackNodeResponse(
                title = node.title,
                children = kids.takeIf { it.isNotEmpty() },
                duration = node.duration,
                streamUrl = node.url,
                mediaDownloadUrl = node.url,
                dlsitePlayImageCrypt = node.imageMeta?.crypt == true,
                dlsitePlayImageWidth = node.imageMeta?.width,
                dlsitePlayImageHeight = node.imageMeta?.height,
                dlsitePlayOptimizedName = node.imageMeta?.optimizedName
            )
            return resp
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
        val mediaPath = if (folder.isBlank()) filename else "$folder/$filename"
        val subtitleCandidates = dirToFiles.flatMap { (candidateFolder, files) ->
            files.mapNotNull { f ->
                val nm = f["name"].orEmpty().trim()
                val hn = f["hashname"].orEmpty().trim()
                if (nm.isBlank() || hn.isBlank()) return@mapNotNull null
                val low = nm.lowercase()
                if (!low.endsWith(".vtt")) return@mapNotNull null
                val optName = vttOpt[hn].orEmpty().trim()
                if (optName.isBlank()) return@mapNotNull null
                val subUrl = "${baseUrl}optimized/$optName?$query"
                val relativePath = if (candidateFolder.isBlank()) nm else "$candidateFolder/$nm"
                val candidate = SubtitleMatchSupport.inferCandidate(relativePath, subUrl) ?: return@mapNotNull null
                candidate to subUrl
            }
        }
        val matched = SubtitleMatchSupport.matchBest(mediaPath, subtitleCandidates.map { it.first }) ?: return emptyList()
        val subUrl = subtitleCandidates.firstOrNull { it.first.sourceRef == matched.sourceRef }?.second ?: return emptyList()
        return listOf(RemoteSubtitleSource(url = subUrl, language = matched.language, ext = "vtt"))
    }

    private fun splitFolder(display: String): Pair<String, String> {
        val trimmed = display.trim()
        val idx = trimmed.lastIndexOf('/')
        return if (idx >= 0) trimmed.substring(0, idx) to trimmed.substring(idx + 1) else "" to trimmed
    }

    private fun typedFileBlock(meta: Map<*, *>, type: String): Map<*, *>? {
        val keys = when (type) {
            "movie" -> listOf("movie", "video")
            else -> listOf(type)
        }
        return keys.asSequence()
            .mapNotNull { key -> meta[key] as? Map<*, *> }
            .firstOrNull()
    }

    private fun optimizedBlock(meta: Map<*, *>, type: String): Map<*, *>? {
        return typedFileBlock(meta, type)?.get("optimized") as? Map<*, *>
    }

    private fun parseDuration(value: Any?): Double? {
        return when (value) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull()
            else -> null
        }
    }

    private fun parseInt(value: Any?): Int? {
        return when (value) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull()
            else -> null
        }
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
        val duration: Double?,
        val imageMeta: DlsitePlayImageMeta? = null
    )

    private data class DlsitePlayImageMeta(
        val crypt: Boolean,
        val width: Int?,
        val height: Int?,
        val optimizedName: String
    )

    private enum class DlsiteFileKind { Audio, Subtitle, Image, Video, Text, Pdf, Other }

    private fun fileKindForName(fileName: String): DlsiteFileKind {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "mp3", "wav", "flac", "m4a", "ogg", "aac", "opus" -> DlsiteFileKind.Audio
            "vtt", "lrc", "srt", "ass", "ssa" -> DlsiteFileKind.Subtitle
            "jpg", "jpeg", "png", "webp", "gif" -> DlsiteFileKind.Image
            "mp4", "mkv", "webm" -> DlsiteFileKind.Video
            "txt", "md", "nfo", "csv", "tsv", "json", "xml", "html", "htm", "log", "ini", "cue", "ks", "yaml", "yml", "rtf" -> DlsiteFileKind.Text
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
    val subtitlesByUrl: Map<String, List<RemoteSubtitleSource>>,
    val status: DlsitePlayLoadStatus
)

enum class DlsitePlayLoadStatus {
    Success,
    NotAvailable,
}
