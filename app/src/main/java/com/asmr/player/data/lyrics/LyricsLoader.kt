package com.asmr.player.data.lyrics

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import com.asmr.player.data.local.db.dao.AlbumDao
import com.asmr.player.data.local.db.dao.RemoteSubtitleSourceDao
import com.asmr.player.data.local.db.dao.TrackDao
import com.asmr.player.data.local.db.entities.LocalTreeCacheEntity
import com.asmr.player.data.local.db.entities.RemoteSubtitleSourceEntity
import com.asmr.player.data.local.db.entities.SubtitleEntity
import com.asmr.player.data.local.db.entities.TrackEntity
import com.asmr.player.data.local.db.entities.titleForDisplay
import com.asmr.player.data.remote.NetworkHeaders
import com.asmr.player.data.remote.auth.DlsiteAuthStore
import com.asmr.player.data.remote.auth.buildDlsiteCookieHeader
import com.asmr.player.ui.library.LocalTreeLeafCacheEntry
import com.asmr.player.ui.library.TreeFileType
import com.asmr.player.util.EmbeddedMediaExtractor
import com.asmr.player.util.OnlineLyricsStore
import com.asmr.player.util.RemoteSubtitleSource
import com.asmr.player.util.SubtitleEntry
import com.asmr.player.util.SubtitleMatchCandidate
import com.asmr.player.util.SubtitleMatchSupport
import com.asmr.player.util.SubtitleParser
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
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
    private val albumDao: AlbumDao,
    private val remoteSubtitleSourceDao: RemoteSubtitleSourceDao,
    private val manualLyricsSourceRepository: ManualLyricsSourceRepository,
    private val okHttpClient: OkHttpClient,
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(item: MediaItem?): Flow<LyricsResult> {
        val target = lyricsTargetContextFromMediaItem(item)
            ?: return flowOf(LyricsResult("", emptyList()))
        return flow {
            // 一次订阅内复用外部字幕读取结果，避免每批翻译写入都重新解析音频和文件。
            val cache = LyricsReadCache()
            emitAll(trackDao.observeLyricsState(target.mediaId, target.trackId)
                .distinctUntilChanged()
                .mapLatest { load(target, item?.mediaMetadata?.title?.toString().orEmpty(), cache) }
                .distinctUntilChanged())
        }.flowOn(Dispatchers.IO)
    }

    suspend fun load(item: MediaItem?): LyricsResult = withContext(Dispatchers.IO) {
        val target = lyricsTargetContextFromMediaItem(item)
            ?: return@withContext LyricsResult(title = "", lyrics = emptyList())
        val fallbackTitle = item?.mediaMetadata?.title?.toString().orEmpty()
        return@withContext load(target, fallbackTitle)
    }

    suspend fun load(mediaId: String, fallbackTitle: String): LyricsResult = withContext(Dispatchers.IO) {
        val fakeItem = MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(mediaId)
            .build()
        val target = lyricsTargetContextFromMediaItem(fakeItem)
            ?: return@withContext LyricsResult(title = "", lyrics = emptyList())
        return@withContext load(target, fallbackTitle)
    }

    private suspend fun load(target: LyricsTargetContext, fallbackTitle: String, cache: LyricsReadCache? = null): LyricsResult {
        if (target.mediaId.isBlank()) return LyricsResult(title = "", lyrics = emptyList())

        val trackByPath = trackDao.getTrackByPathOnce(target.mediaId)
        val trackById = target.trackId.takeIf { it > 0L }?.let { id -> trackDao.getTrackByIdOnce(id) }
        val track = trackByPath ?: trackById
        val title = track?.titleForDisplay?.takeIf { it.isNotBlank() } ?: fallbackTitle.ifBlank { target.mediaId }

        if (trackDao.isSubtitleAutoImportBlocked(track?.path ?: target.mediaId)) {
            // 删除后不自动加载旧文件；主动重扫、选择字幕或重新生成仍可恢复。
            return subtitleResult(title, track?.let { trackDao.getSubtitlesForTrack(it.id) }.orEmpty())
        }

        val manualLyrics = loadManualLyrics(target, cache)
        if (manualLyrics.isNotEmpty()) {
            return LyricsResult(title = title, lyrics = manualLyrics)
        }

        if (track == null) {
            val remoteSources = target.remoteSubtitleSources.ifEmpty { OnlineLyricsStore.get(target.mediaId) }
            val remoteLyrics = if (remoteSources.isNotEmpty()) loadRemoteLyrics(remoteSources) else emptyList()
            return LyricsResult(title = title, lyrics = remoteLyrics)
        }

        val embedded = cache?.embedded?.takeIf { it.first == track.path }?.second
            ?: EmbeddedMediaExtractor.extractEmbeddedLyricsEntries(context, track.path).also {
                cache?.embedded = track.path to it
            }
        if (embedded.isNotEmpty()) {
            return LyricsResult(title = title, lyrics = embedded)
        }

        var subs = trackDao.getSubtitlesForTrack(track.id)
        if (subs.isEmpty()) {
            importBestLyricsIfPossible(track, target)
            subs = trackDao.getSubtitlesForTrack(track.id)
        }

        if (subs.isEmpty() && track.path.trim().startsWith("http", ignoreCase = true)) {
            val remoteSources = target.remoteSubtitleSources.ifEmpty {
                remoteSubtitleSourceDao.getSourcesForTrackOnce(track.id).mapNotNull { src ->
                    val url = src.url.trim()
                    if (url.isBlank()) return@mapNotNull null
                    RemoteSubtitleSource(url = url, language = src.language, ext = src.ext)
                }
            }
            val remoteLyrics = if (remoteSources.isNotEmpty()) loadRemoteLyrics(remoteSources) else emptyList()
            if (remoteLyrics.isNotEmpty()) {
                persistAutoLyrics(track.id, target, remoteLyrics)
                subs = trackDao.getSubtitlesForTrack(track.id)
            }
        }

        return subtitleResult(title, subs)
    }

    suspend fun saveManualLyrics(target: LyricsTargetContext, sourceUri: String): LyricsResult = withContext(Dispatchers.IO) {
        manualLyricsSourceRepository.upsert(target, sourceUri)
        val entries = readLyricsFromUri(sourceUri)
        if (entries.isNotEmpty()) {
            val track = trackDao.getTrackByPathOnce(target.mediaId)
                ?: target.trackId.takeIf { it > 0 }?.let { trackDao.getTrackByIdOnce(it) }
            trackDao.allowSubtitleAutoImport(track?.path ?: target.mediaId)
        }
        LyricsResult(title = target.title.ifBlank { target.mediaId }, lyrics = entries)
    }

    suspend fun fetchTextForPreview(url: String): String? {
        return fetchText(url)
    }

    private suspend fun loadManualLyrics(target: LyricsTargetContext, cache: LyricsReadCache?): List<SubtitleEntry> {
        val manual = manualLyricsSourceRepository.findBestMatch(target) ?: return emptyList()
        val key = "${manual.sourceUri}|${manual.updatedAt}"
        return cache?.manual?.takeIf { it.first == key }?.second
            ?: readLyricsFromUri(manual.sourceUri).also { cache?.manual = key to it }
    }

    private suspend fun importBestLyricsIfPossible(track: TrackEntity, target: LyricsTargetContext): List<SubtitleEntry> {
        val trimmed = track.path.trim()
        return when {
            trimmed.startsWith("http", ignoreCase = true) -> emptyList()
            trimmed.startsWith("content://", ignoreCase = true) -> importFromContentTree(track, target)
            else -> importFromLocalTree(track, target)
        }
    }

    private suspend fun importFromLocalTree(track: TrackEntity, target: LyricsTargetContext): List<SubtitleEntry> {
        val album = albumDao.getAlbumById(track.albumId) ?: return emptyList()
        val roots = album.getAllLocalRoots()
        if (roots.isEmpty()) return emptyList()
        val relativePath = target.relativePathNoExt.ifBlank {
            deriveLyricsRelativePathNoExt(track.path, roots)
        }
        if (relativePath.isBlank()) return emptyList()

        val candidates = mutableListOf<Pair<SubtitleMatchCandidate, File>>()
        roots.forEach { rootPath ->
            val root = File(rootPath)
            if (!root.exists() || !root.isDirectory) return@forEach
            root.walkTopDown().forEach fileLoop@{ file ->
                if (!file.isFile) return@fileLoop
                val relative = runCatching { file.relativeTo(root).path.replace('\\', '/') }.getOrNull().orEmpty()
                val candidate = SubtitleMatchSupport.inferCandidate(relative, file.absolutePath) ?: return@fileLoop
                candidates += candidate to file
            }
        }
        val matched = SubtitleMatchSupport.matchBest(relativePath, candidates.map { it.first }) ?: return emptyList()
        val file = candidates.firstOrNull { it.first.sourceRef == matched.sourceRef }?.second ?: return emptyList()
        val entries = SubtitleParser.parse(file.absolutePath)
        if (entries.isNotEmpty()) {
            persistAutoLyrics(track.id, target, entries)
        }
        return entries
    }

    private suspend fun importFromContentTree(track: TrackEntity, target: LyricsTargetContext): List<SubtitleEntry> {
        val album = albumDao.getAlbumById(track.albumId) ?: return emptyList()
        val cache = findLocalTreeCache(album.id, album.cacheCandidatePaths()) ?: return emptyList()
        val leaves = decodeLocalTreeLeaves(cache)
        if (leaves.isEmpty()) return emptyList()
        val relativePath = target.relativePathNoExt.ifBlank {
            leaves.firstOrNull { it.absolutePath == track.path }?.relativePath?.substringBeforeLast('.') ?: ""
        }
        if (relativePath.isBlank()) return emptyList()

        val candidates = leaves.asSequence()
            .filter { it.fileType == TreeFileType.Subtitle }
            .mapNotNull { leaf ->
                SubtitleMatchSupport.inferCandidate(leaf.relativePath, leaf.absolutePath)?.let { it to leaf }
            }
            .toList()
        val matched = SubtitleMatchSupport.matchBest(relativePath, candidates.map { it.first }) ?: return emptyList()
        val leaf = candidates.firstOrNull { it.first.sourceRef == matched.sourceRef }?.second ?: return emptyList()
        val entries = readLyricsFromUri(leaf.absolutePath)
        if (entries.isNotEmpty()) {
            persistAutoLyrics(track.id, target, entries)
        }
        return entries
    }

    private suspend fun loadRemoteLyrics(sources: List<RemoteSubtitleSource>): List<SubtitleEntry> {
        val ordered = sources.sortedWith(
            compareBy<RemoteSubtitleSource> { source ->
                val index = SubtitleMatchSupport.PreferredLanguages.indexOf(source.language.lowercase())
                if (index >= 0) index else Int.MAX_VALUE
            }.thenBy { it.url }
        )

        for (source in ordered) {
            val text = fetchText(source.url) ?: continue
            val ext = source.ext.ifBlank { source.url.substringAfterLast('.', "") }
            val parsed = SubtitleParser.parseText(ext, text)
            if (parsed.isNotEmpty()) {
                return parsed.distinctBy { it.startMs to (it.endMs to it.text) }
            }
        }
        return emptyList()
    }

    private suspend fun persistAutoLyrics(trackId: Long, target: LyricsTargetContext, entries: List<SubtitleEntry>) {
        if (entries.isEmpty()) return
        if (trackDao.replaceAutoSubtitles(trackId, entries.toSubtitleEntities(trackId))) {
            manualLyricsSourceRepository.clearForAutoLyrics(target)
        }
    }

    private fun subtitleResult(title: String, subtitles: List<SubtitleEntity>): LyricsResult = LyricsResult(
        title = title,
        lyrics = normalizeAndDistinct(subtitles).sortedBy { it.startMs }
            .map { SubtitleEntry(it.startMs, it.endMs, it.text) }
    )

    private fun normalizeAndDistinct(subs: List<SubtitleEntity>): List<SubtitleEntity> {
        fun normalizeDuplicateMergedLines(text: String): String {
            val raw = text.replace('\r', '\n')
            val parts = raw.split('\n').map { it.trim() }.filter { it.isNotBlank() }
            if (parts.size <= 1) return text
            val first = parts.first()
            return if (first.isNotBlank() && parts.all { it == first }) first else text
        }

        return subs.map { it.copy(text = normalizeDuplicateMergedLines(it.text)) }
            .distinctBy { it.startMs to (it.endMs to it.text) }
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

    private fun readLyricsFromUri(sourceUri: String): List<SubtitleEntry> {
        val trimmed = sourceUri.trim()
        if (trimmed.isBlank()) return emptyList()
        return when {
            trimmed.startsWith("content://", ignoreCase = true) -> readSubtitleEntriesFromContentUri(trimmed)
            trimmed.startsWith("file://", ignoreCase = true) -> {
                val path = runCatching { Uri.parse(trimmed).path.orEmpty() }.getOrNull().orEmpty()
                if (path.isBlank()) emptyList() else SubtitleParser.parse(path)
            }
            else -> SubtitleParser.parse(trimmed)
        }
    }

    private fun readSubtitleEntriesFromContentUri(uriString: String): List<SubtitleEntry> {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return emptyList()
        val displayName = resolveDisplayName(uri)
        val extension = displayName.substringAfterLast('.', uri.lastPathSegment?.substringAfterLast('.', "") ?: "lrc")
            .lowercase()
        val content = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes()
            }
        }.getOrNull() ?: return emptyList()
        return SubtitleParser.parseBytes(extension, content)
    }

    private fun resolveDisplayName(uri: Uri): String {
        val projection = arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)
        return runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull().orEmpty().ifBlank { uri.lastPathSegment.orEmpty() }
    }

    private suspend fun findLocalTreeCache(albumId: Long, albumPaths: List<String>): LocalTreeCacheEntity? {
        val normalizedPaths = albumPaths.map { it.trim() }.filter { it.isNotBlank() }.distinct().sorted()
        if (normalizedPaths.isEmpty()) return null
        val key = normalizedPaths.joinToString("|")
        return com.asmr.player.data.local.db.AppDatabaseProvider.get(context).localTreeCacheDao().getByAlbumAndKey(albumId, key)
    }

    private fun decodeLocalTreeLeaves(entity: LocalTreeCacheEntity): List<LocalTreeLeafCacheEntry> {
        if (entity.payloadJson.isBlank()) return emptyList()
        val newType = object : TypeToken<List<LocalTreeLeafCacheEntry>>() {}.type
        val parsed = runCatching { gson.fromJson<List<LocalTreeLeafCacheEntry>>(entity.payloadJson, newType) }.getOrNull()
        if (!parsed.isNullOrEmpty()) return parsed

        val legacyType = object : TypeToken<List<LegacyCacheLeafEntry>>() {}.type
        val legacy = runCatching { gson.fromJson<List<LegacyCacheLeafEntry>>(entity.payloadJson, legacyType) }.getOrNull().orEmpty()
        return legacy.map { leaf ->
            LocalTreeLeafCacheEntry(
                relativePath = leaf.relativePath,
                absolutePath = leaf.absolutePath,
                fileType = legacyFileTypeToTreeFileType(leaf.fileType)
            )
        }
    }

    private fun legacyFileTypeToTreeFileType(fileType: String): TreeFileType {
        return when (fileType.trim()) {
            "Audio" -> TreeFileType.Audio
            "Video" -> TreeFileType.Video
            "Image" -> TreeFileType.Image
            "Subtitle" -> TreeFileType.Subtitle
            "Text" -> TreeFileType.Text
            "Pdf" -> TreeFileType.Pdf
            "Archive" -> TreeFileType.Archive
            "Document" -> TreeFileType.Document
            "Spreadsheet" -> TreeFileType.Spreadsheet
            "Presentation" -> TreeFileType.Presentation
            "Code" -> TreeFileType.Code
            "Ebook" -> TreeFileType.Ebook
            "Font" -> TreeFileType.Font
            "AppPackage" -> TreeFileType.AppPackage
            else -> TreeFileType.Other
        }
    }

    private fun List<SubtitleEntry>.toSubtitleEntities(trackId: Long): List<SubtitleEntity> {
        return distinctBy { it.startMs to (it.endMs to it.text) }.map { entry ->
            SubtitleEntity(
                trackId = trackId,
                startMs = entry.startMs,
                endMs = entry.endMs,
                text = entry.text
            )
        }
    }

    private fun com.asmr.player.data.local.db.entities.AlbumEntity.getAllLocalRoots(): List<String> {
        val roots = mutableListOf<String>()
        val mainPath = path.trim()
        if (mainPath.isNotBlank() && !mainPath.startsWith("http", ignoreCase = true) && !mainPath.startsWith("web://", ignoreCase = true)) {
            roots += mainPath
        }
        localPath?.trim()?.takeIf { it.isNotBlank() }?.let { roots += it }
        downloadPath?.trim()?.takeIf { it.isNotBlank() }?.let { roots += it }
        return roots.distinct()
    }

    private fun com.asmr.player.data.local.db.entities.AlbumEntity.cacheCandidatePaths(): List<String> {
        return listOfNotNull(path, localPath, downloadPath)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private data class LegacyCacheLeafEntry(
        val relativePath: String,
        val absolutePath: String,
        val fileType: String
    )

    private class LyricsReadCache {
        var embedded: Pair<String, List<SubtitleEntry>>? = null
        var manual: Pair<String, List<SubtitleEntry>>? = null
    }
}
