package com.asmr.player.data.remote.download

import com.asmr.player.data.remote.NetworkHeaders
import com.asmr.player.util.ChapterMediaReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class JapaneseAsmrDownloadPreflight internal constructor(
    private val client: OkHttpClient,
    private val checkTimeoutMs: Long,
) {
    @Inject constructor(client: OkHttpClient) : this(client, 20_000L)

    suspend fun prepare(
        request: DownloadBatchRequest,
        confirmOnlineAudio: suspend (List<String>) -> Boolean,
    ): DownloadBatchRequest? = withContext(Dispatchers.IO) {
        val chapters = request.items.mapNotNull { item ->
            ChapterMediaReference.parse(item.url)?.takeIf { it.isHls }?.let { item to it }
        }
        if (chapters.isEmpty()) return@withContext request
        val probeClient = client.newBuilder().callTimeout(15, TimeUnit.SECONDS).build()
        val semaphore = Semaphore(2)
        val missing = withTimeout(checkTimeoutMs) {
            coroutineScope {
                chapters.map { (item, chapter) ->
                    async {
                        semaphore.withPermit {
                            ensureActive()
                            if (sourceFileMissing(probeClient, chapter)) item else null
                        }
                    }
                }.awaitAll().filterNotNull().toSet()
            }
        }
        ensureActive()
        if (missing.isEmpty()) return@withContext request
        if (!confirmOnlineAudio(missing.map { it.relativePath.substringAfterLast('/') })) return@withContext null
        ensureActive()
        request.copy(items = request.items.map { item ->
            if (item !in missing) item else {
                val name = item.relativePath.substringAfterLast('/').substringBeforeLast('.') + ".m4a"
                val path = item.relativePath.substringBeforeLast('/', "").let { dir ->
                    if (dir.isBlank()) name else "$dir/$name"
                }
                val chapter = checkNotNull(ChapterMediaReference.parse(item.url))
                item.copy(relativePath = path, url = chapter.copy(
                    downloadUrl = null, fileName = name, useHlsDownload = true,
                ).encode())
            }
        })
    }

    private suspend fun sourceFileMissing(client: OkHttpClient, chapter: ChapterMediaReference): Boolean {
        val url = chapter.downloadUrl?.takeIf { it.isNotBlank() } ?: return true
        try {
            val resolved = resolveJapaneseAsmrDownload(client, url, chapter.fileName)
            val request = Request.Builder().url(resolved)
                .header("User-Agent", NetworkHeaders.USER_AGENT)
                .header("Referer", "https://japaneseasmr.com/")
                .header(NetworkHeaders.HEADER_SILENT_IO_ERROR, NetworkHeaders.SILENT_IO_ERROR_ON)
                .header("Range", "bytes=0-15").build()
            return withJapaneseAsmrResponse(client, request) { response ->
                if (response.code == 404 || response.code == 410) return@withJapaneseAsmrResponse true
                if (!response.isSuccessful) throw IOException("源文件状态检查失败 (${response.code})")
                val source = response.body?.source()?.peek() ?: throw IOException("源文件状态检查失败")
                source.request(12)
                if (!isJapaneseAsmrAudioHeader(chapter.fileName, source.readByteArray(minOf(12L, source.buffer.size)))) {
                    throw IOException("源文件状态检查未返回音频")
                }
                false
            }
        } catch (_: JapaneseAsmrSourceFileMissingException) {
            return true
        }
    }
}
