package com.asmr.player.data.lyrics

import android.content.Context
import com.asmr.player.data.local.db.dao.RemoteSubtitleSourceDao
import com.asmr.player.data.local.db.dao.TrackDao
import com.asmr.player.data.local.db.entities.SubtitleEntity
import com.asmr.player.data.remote.NetworkHeaders
import com.asmr.player.data.remote.auth.DlsiteAuthStore
import com.asmr.player.data.remote.auth.buildDlsiteCookieHeader
import com.asmr.player.util.OnlineLyricsStore
import com.asmr.player.util.RemoteSubtitleSource
import com.asmr.player.util.SubtitleEntry
import com.asmr.player.util.SubtitleParser
import com.asmr.player.util.EmbeddedMediaExtractor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class LyricsResult(
    val title: String,
    val lyrics: List<SubtitleEntry>
)

@Singleton
class LyricsLoader @Inject constructor(
    private val trackDao: TrackDao,
    private val remoteSubtitleSourceDao: RemoteSubtitleSourceDao,
    private val okHttpClient: OkHttpClient,
    @ApplicationContext private val context: Context
) {
    suspend fun load(mediaId: String, fallbackTitle: String): LyricsResult = withContext(Dispatchers.IO) {
        val id = mediaId.trim()
        if (id.isBlank()) return@withContext LyricsResult(title = "", lyrics = emptyList())
        val track = trackDao.getTrackByPathOnce(id)
        if (track == null) {
            val remoteSources = OnlineLyricsStore.get(id)
            val remoteLyrics = if (remoteSources.isNotEmpty()) loadRemoteLyrics(remoteSources) else emptyList()
            val title = fallbackTitle.ifBlank { id }
            return@withContext LyricsResult(title = title, lyrics = remoteLyrics)
        }

            val embedded = EmbeddedMediaExtractor.extractEmbeddedLyricsEntries(context, track.path)
            if (embedded.isNotEmpty()) {
                return@withContext LyricsResult(title = track.title, lyrics = embedded)
            }

        var subs = trackDao.getSubtitlesForTrack(track.id)
        if (subs.isEmpty()) {
            importSidecarSubtitlesIfPossible(trackId = track.id, trackPath = track.path)
            subs = trackDao.getSubtitlesForTrack(track.id)
        }
        if (subs.isEmpty() && track.path.trim().startsWith("http", ignoreCase = true)) {
            val remoteSources = remoteSubtitleSourceDao.getSourcesForTrackOnce(track.id).mapNotNull { src ->
                val url = src.url.trim()
                if (url.isBlank()) return@mapNotNull null
                RemoteSubtitleSource(url = url, language = src.language, ext = src.ext)
            }
            val remoteLyrics = if (remoteSources.isNotEmpty()) loadRemoteLyrics(remoteSources) else emptyList()
            if (remoteLyrics.isNotEmpty()) {
                trackDao.deleteSubtitlesForTrack(track.id)
                trackDao.insertSubtitles(
                    remoteLyrics.map { e ->
                        SubtitleEntity(
                            trackId = track.id,
                            startMs = e.startMs,
                            endMs = e.endMs,
                            text = e.text
                        )
                    }
                )
                subs = trackDao.getSubtitlesForTrack(track.id)
            }
        }
        fun normalizeDuplicateMergedLines(text: String): String {
            val raw = text.replace('\r', '\n')
            val parts = raw.split('\n').map { it.trim() }.filter { it.isNotBlank() }
            if (parts.size <= 1) return text
            val first = parts.first()
            return if (first.isNotBlank() && parts.all { it == first }) first else text
        }

        val normalized = subs.map { it.copy(text = normalizeDuplicateMergedLines(it.text)) }
        val distinct = normalized.distinctBy { it.startMs to (it.endMs to it.text) }
        if (distinct.size != subs.size) {
            trackDao.deleteSubtitlesForTrack(track.id)
            distinct.forEach { e ->
                trackDao.insertSubtitle(
                    SubtitleEntity(
                        trackId = track.id,
                        startMs = e.startMs,
                        endMs = e.endMs,
                        text = e.text
                    )
                )
            }
        }
        val entries = distinct.sortedBy { it.startMs }.map { SubtitleEntry(it.startMs, it.endMs, it.text) }
        return@withContext LyricsResult(title = track.title, lyrics = entries)
    }

    private suspend fun importSidecarSubtitlesIfPossible(trackId: Long, trackPath: String) {
        val trimmed = trackPath.trim()
        if (trimmed.startsWith("http", ignoreCase = true) || trimmed.startsWith("content://")) return
        val audio = File(trimmed)
        if (!audio.exists() || !audio.isFile) return

        val base = audio.nameWithoutExtension
        val subtitleExtensions = setOf("lrc", "srt", "vtt")
        val audioExts = setOf("mp3", "flac", "wav", "m4a", "ogg", "aac", "opus")
        val siblings = audio.parentFile?.listFiles()
            ?.filter { it.isFile && subtitleExtensions.contains(it.extension.lowercase()) }
            .orEmpty()

        val matched = siblings.mapNotNull { f ->
            val nameWithoutExt = f.nameWithoutExtension
            val isMatch: Boolean
            val language: String

            if (nameWithoutExt.equals(base, ignoreCase = true)) {
                isMatch = true
                language = "default"
            } else if (nameWithoutExt.startsWith("$base.", ignoreCase = true)) {
                isMatch = true
                val suffix = nameWithoutExt.substring(base.length + 1)
                val parts = suffix.split('.').filter { it.isNotBlank() }
                val valid = parts.filter { !audioExts.contains(it.lowercase()) }
                language = when {
                    valid.isEmpty() -> "zh"
                    valid.size == 1 -> valid[0]
                    else -> valid.last()
                }
            } else {
                isMatch = false
                language = "default"
            }

            if (isMatch) f to language else null
        }

        val prefer = listOf("default", "zh", "cn", "chs", "ja", "jp", "jpn")
        val ordered = matched.sortedWith(
            compareBy<Pair<File, String>> { (_, lang) ->
                val idx = prefer.indexOf(lang.lowercase())
                if (idx >= 0) idx else Int.MAX_VALUE
            }.thenBy { it.first.name.lowercase() }
        ).map { it.first }

        val parsedLists = ordered.take(1).map { SubtitleParser.parse(it.absolutePath) }.filter { it.isNotEmpty() }
        val merged = parsedLists.firstOrNull() ?: emptyList()
        if (merged.isEmpty()) return
        trackDao.deleteSubtitlesForTrack(trackId)
        merged.forEach { e ->
            trackDao.insertSubtitle(
                SubtitleEntity(
                    trackId = trackId,
                    startMs = e.startMs,
                    endMs = e.endMs,
                    text = e.text
                )
            )
        }
    }

    private suspend fun loadRemoteLyrics(sources: List<RemoteSubtitleSource>): List<SubtitleEntry> {
        val prefer = listOf("default", "zh", "cn", "chs", "ja", "jp", "jpn")
        val ordered = sources.sortedWith(
            compareBy<RemoteSubtitleSource> { s ->
                val idx = prefer.indexOf(s.language.lowercase())
                if (idx >= 0) idx else Int.MAX_VALUE
            }.thenBy { it.url }
        )

        val parsed = ordered.take(1).mapNotNull { src ->
            val text = fetchText(src.url) ?: return@mapNotNull null
            val ext = src.ext.ifBlank { src.url.substringAfterLast('.', "") }
            SubtitleParser.parseText(ext, text).takeIf { it.isNotEmpty() }
        }

        return (parsed.firstOrNull() ?: emptyList()).distinctBy { it.startMs to (it.endMs to it.text) }
    }

    suspend fun fetchTextForPreview(url: String): String? {
        return fetchText(url)
    }

    private suspend fun fetchText(url: String): String? = withContext(Dispatchers.IO) {
        val lowerUrl = url.lowercase()
        val authStore = DlsiteAuthStore(context)
        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", NetworkHeaders.USER_AGENT)
            .header("Accept", "text/plain, application/octet-stream, */*")

        if (lowerUrl.contains("play.dlsite.com")) {
            requestBuilder
                .header("Referer", "https://play.dlsite.com/library")
                .header("Accept-Language", NetworkHeaders.ACCEPT_LANGUAGE)
            val cookie = buildDlsiteCookieHeader(authStore.getPlayCookie())
            if (cookie.isNotBlank()) {
                requestBuilder.header("Cookie", cookie)
            }
        } else if (lowerUrl.contains("dlsite")) {
            requestBuilder
                .header("Referer", NetworkHeaders.REFERER_DLSITE)
                .header("Accept-Language", NetworkHeaders.ACCEPT_LANGUAGE)
            val cookie = buildDlsiteCookieHeader(authStore.getDlsiteCookie())
            if (cookie.isNotBlank()) {
                requestBuilder.header("Cookie", cookie)
            }
        } else if (lowerUrl.contains("dlsite.com")) {
            requestBuilder.header("Referer", "https://play.dlsite.com/")
        }

        runCatching {
            okHttpClient.newCall(requestBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                resp.body?.string()
            }
        }.getOrNull()
    }
}
