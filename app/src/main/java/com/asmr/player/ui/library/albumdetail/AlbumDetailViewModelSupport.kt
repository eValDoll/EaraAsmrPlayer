package com.asmr.player.ui.library

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.dao.AlbumDao
import com.asmr.player.data.local.db.dao.TagWithCount
import com.asmr.player.data.local.db.dao.TrackDao
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.data.local.db.entities.AlbumFtsEntity
import com.asmr.player.data.local.db.entities.AlbumTagEntity
import com.asmr.player.data.local.db.entities.RemoteSubtitleSourceEntity
import com.asmr.player.data.local.db.entities.TrackEntity
import com.asmr.player.data.local.db.entities.TagEntity
import com.asmr.player.data.local.db.entities.TagSource
import com.asmr.player.data.local.db.entities.TrackTagEntity
import com.asmr.player.data.remote.api.AsmrOneTrackNodeResponse
import com.asmr.player.data.remote.api.WorkDetailsResponse
import com.asmr.player.data.remote.crawler.AsmrOneCrawler
import com.asmr.player.data.remote.dlsite.DlsiteCloudSyncCandidate
import com.asmr.player.data.remote.dlsite.DlsiteCloudSyncResolveResult
import com.asmr.player.data.remote.dlsite.DlsitePlayWorkClient
import com.asmr.player.data.remote.dlsite.DlsitePlayTreeResult
import com.asmr.player.data.remote.dlsite.DlsiteLanguageEdition
import com.asmr.player.data.remote.dlsite.DlsiteProductInfoClient
import com.asmr.player.data.remote.dlsite.resolveCloudSyncWorkId
import com.asmr.player.data.remote.dlsite.resolveDlsiteCloudSync
import com.asmr.player.data.remote.dlsite.resolveSelectedDlsiteCloudSync
import com.asmr.player.data.remote.download.DownloadManager
import com.asmr.player.data.remote.scraper.DLSiteScraper
import com.asmr.player.data.remote.scraper.DlsiteRecommendedWork
import com.asmr.player.data.remote.scraper.DlsiteRecommendations
import com.asmr.player.data.remote.NetworkHeaders
import com.asmr.player.data.lyrics.LyricsLoader
import com.asmr.player.domain.model.Album
import com.asmr.player.domain.model.Track
import com.asmr.player.ui.nav.AlbumCoverHint
import com.asmr.player.util.OnlineLyricsStore
import com.asmr.player.util.RemoteSubtitleSource
import com.asmr.player.util.SyncCoordinator
import com.asmr.player.util.TrackKeyNormalizer
import com.asmr.player.util.DlsiteWorkNo
import com.asmr.player.data.remote.crawler.asmrOneWorkMatchesRj
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

import com.asmr.player.util.MessageManager
import com.asmr.player.util.TagNormalizer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.SystemClock
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import javax.inject.Named
import com.asmr.player.BuildConfig
import com.asmr.player.work.AlbumCoverThumbWorker

internal fun centerCropSquare(src: Bitmap, size: Int): Bitmap {
    val w = src.width
    val h = src.height
    if (w <= 0 || h <= 0) return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val side = minOf(w, h)
    val left = (w - side) / 2
    val top = (h - side) / 2
    val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    canvas.drawBitmap(
        src,
        Rect(left, top, left + side, top + side),
        Rect(0, 0, size, size),
        paint
    )
    return out
}

@Immutable
sealed class AlbumDetailUiState {
    object Loading : AlbumDetailUiState()
    data class Success(val model: AlbumDetailModel) : AlbumDetailUiState()
    data class Error(val message: String) : AlbumDetailUiState()
}

internal fun requestRemoteFileSize(url: String, client: OkHttpClient? = null): Long? {
    if (client == null) return null
    fun execute(request: Request): Long? {
        return runCatching {
            client.newCall(request).execute().use(::extractRemoteFileSize)
        }.getOrNull()
    }

    val headRequest = Request.Builder()
        .url(url)
        .head()
        .header(NetworkHeaders.HEADER_SILENT_IO_ERROR, NetworkHeaders.SILENT_IO_ERROR_ON)
        .build()
    execute(headRequest)?.let { return it }

    val rangeRequest = Request.Builder()
        .url(url)
        .get()
        .header("Range", "bytes=0-0")
        .header(NetworkHeaders.HEADER_SILENT_IO_ERROR, NetworkHeaders.SILENT_IO_ERROR_ON)
        .build()
    return execute(rangeRequest)
}

internal fun extractRemoteFileSize(response: Response): Long? {
    if (!response.isSuccessful) return null
    val contentRange = response.header("Content-Range").orEmpty()
    val totalFromRange = contentRange.substringAfterLast('/', "").toLongOrNull()
    if (totalFromRange != null && totalFromRange > 0L) return totalFromRange
    val contentLength = response.header("Content-Length")?.toLongOrNull()
    if (contentLength != null && contentLength > 0L) return contentLength
    val bodyLength = response.body?.contentLength()
    return bodyLength?.takeIf { it > 0L }
}

@Immutable
data class AlbumDetailModel(
    val baseRjCode: String,
    val rjCode: String,
    val listenTogetherRjListenerCount: Int?,
    val displayAlbum: Album,
    val localAlbum: Album?,
    val dlsiteInfo: Album?,
    val dlsiteGalleryUrls: List<String>,
    val dlsiteTrialTracks: List<Track>,
    val dlsiteRecommendations: DlsiteRecommendations,
    val dlsiteWorkno: String,
    val dlsitePlayWorkno: String,
    val dlsiteEditions: List<DlsiteLanguageEdition>,
    val dlsiteSelectedLang: String,
    val hasResolvedInitialDlsiteTarget: Boolean,
    val hasLoadedInitialDlsiteContent: Boolean,
    val hasResolvedAsmrOneContent: Boolean,
    val hasResolvedDlsitePlayContent: Boolean,
    val preserveHeaderAlbumMetadata: Boolean,
    val isDlsiteLanguageUserSelected: Boolean,
    val asmrOneWorkId: String?,
    val asmrOneSite: Int?,
    val asmrOneTree: List<AsmrOneTrackNodeResponse>,
    val dlsitePlayTree: List<AsmrOneTrackNodeResponse>,
    val isLoadingDlsite: Boolean,
    val isLoadingDlsiteTrial: Boolean,
    val isLoadingAsmrOne: Boolean,
    val isLoadingDlsitePlay: Boolean
)

internal fun AlbumDetailModel.listenTogetherSummaryRj(): String {
    return baseRjCode.trim().uppercase().ifBlank { rjCode.trim().uppercase() }
}

internal fun AlbumDetailModel.withPreservedListenTogetherListenerCount(
    previous: AlbumDetailModel?
): AlbumDetailModel {
    if (previous == null || listenTogetherSummaryRj() != previous.listenTogetherSummaryRj()) {
        return this
    }
    return copy(listenTogetherRjListenerCount = previous.listenTogetherRjListenerCount)
}

internal fun AlbumDetailModel.withUpdatedLocalCover(
    albumId: Long,
    coverPath: String,
    coverThumbPath: String
): AlbumDetailModel {
    val localMatches = localAlbum?.id == albumId
    val displayMatches = displayAlbum.id == albumId || localMatches
    if (!localMatches && !displayMatches) return this

    return copy(
        localAlbum = if (localMatches) {
            localAlbum?.copy(coverPath = coverPath, coverThumbPath = coverThumbPath)
        } else {
            localAlbum
        },
        displayAlbum = if (displayMatches) {
            displayAlbum.copy(coverPath = coverPath, coverThumbPath = coverThumbPath)
        } else {
            displayAlbum
        }
    )
}

internal fun resolveStableAlbumHeroCoverSource(
    stable: String,
    currentLocal: String,
    current: String
): String {
    return if (currentLocal.isNotBlank() && currentLocal != stable) {
        currentLocal
    } else if (stable.isBlank() && current.isNotBlank()) {
        current
    } else {
        stable
    }
}

internal fun buildDisplayAlbum(
    rjCode: String,
    localAlbum: Album?,
    dlsiteInfo: Album?,
    asmrOneWorkId: String?,
    fallbackCv: String = "",
    fallbackCoverUrl: String = ""
): Album {
    val base = dlsiteInfo ?: localAlbum ?: Album(title = rjCode.ifBlank { "专辑" }, path = "")
    val displayTitle = localAlbum?.displayTitle?.takeIf { it.isNotBlank() }
    return base.copy(
        title = displayTitle ?: base.title,
        displayTitle = displayTitle.orEmpty(),
        workId = asmrOneWorkId?.takeIf { it.isNotBlank() } ?: base.workId,
        rjCode = rjCode.ifBlank { base.rjCode.ifBlank { base.workId } },
        cv = base.cv.ifBlank { fallbackCv },
        // 网络解析尚未返回封面时，保留列表种入/上一帧的 coverUrl，避免 hero 先空白再加载。
        coverUrl = base.coverUrl.ifBlank { fallbackCoverUrl }
    )
}

internal fun Album.withResolvedWorkIdentity(
    rjCode: String,
    asmrOneWorkId: String?
): Album {
    return copy(
        workId = asmrOneWorkId?.takeIf { it.isNotBlank() } ?: workId,
        rjCode = rjCode.ifBlank { this.rjCode.ifBlank { workId } }
    )
}

internal fun mergeDetailHeaderAlbum(
    currentDisplayAlbum: Album,
    localAlbum: Album?,
    fetchedDlsiteInfo: Album?,
    rjCode: String,
    asmrOneWorkId: String?,
    preserveHeaderAlbumMetadata: Boolean
): Album {
    if (preserveHeaderAlbumMetadata) {
        return currentDisplayAlbum.withResolvedWorkIdentity(
            rjCode = rjCode,
            asmrOneWorkId = asmrOneWorkId
        )
    }
    return if (fetchedDlsiteInfo != null) {
        buildDisplayAlbum(
            rjCode = rjCode,
            localAlbum = localAlbum,
            dlsiteInfo = fetchedDlsiteInfo,
            asmrOneWorkId = asmrOneWorkId,
            fallbackCv = currentDisplayAlbum.cv,
            fallbackCoverUrl = currentDisplayAlbum.coverUrl
        )
    } else {
        currentDisplayAlbum.withResolvedWorkIdentity(
            rjCode = rjCode,
            asmrOneWorkId = asmrOneWorkId
        )
    }
}

internal fun shouldPreserveHeaderAlbumMetadata(hint: AlbumCoverHint?): Boolean {
    return hint?.hasResolvedDlsiteInfo == true
}

internal data class ResolvedDlsiteLoadTarget(
    val editions: List<DlsiteLanguageEdition>,
    val selectedLang: String,
    val workno: String
)

internal fun defaultDlsiteEditions(baseRj: String): List<DlsiteLanguageEdition> {
    val clean = baseRj.trim().uppercase()
    if (clean.isBlank()) return emptyList()
    return listOf(
        DlsiteLanguageEdition(
            workno = clean,
            lang = "JPN",
            label = "日本語",
            displayOrder = 1
        )
    )
}

internal fun mergeDlsiteEditions(
    baseRj: String,
    editions: List<DlsiteLanguageEdition>
): List<DlsiteLanguageEdition> {
    val clean = baseRj.trim().uppercase()
    if (clean.isBlank()) return emptyList()
    return buildList {
        val hasJpn = editions.any { it.lang.equals("JPN", ignoreCase = true) }
        val hasEntryEdition = editions.any {
            it.workno.trim().equals(clean, ignoreCase = true)
        }
        if (!hasJpn && !hasEntryEdition) addAll(defaultDlsiteEditions(clean))
        addAll(editions)
    }.distinctBy { it.lang.trim().uppercase() }
        .sortedWith(compareBy({ it.displayOrder }, { it.lang }))
}

internal fun resolveInitialDlsiteLoadTarget(
    entryRjCode: String,
    editions: List<DlsiteLanguageEdition>
): ResolvedDlsiteLoadTarget {
    val clean = entryRjCode.trim().uppercase()
    if (clean.isBlank()) {
        return ResolvedDlsiteLoadTarget(
            editions = emptyList(),
            selectedLang = "JPN",
            workno = ""
        )
    }
    val merged = mergeDlsiteEditions(clean, editions)
    val entryEdition = merged.firstOrNull {
        it.workno.trim().equals(clean, ignoreCase = true)
    }
    val selectedLang = entryEdition?.lang?.trim().orEmpty().ifBlank { "JPN" }
    return ResolvedDlsiteLoadTarget(
        editions = merged,
        selectedLang = selectedLang,
        workno = clean
    )
}

internal fun asmrOneTrackRjCandidates(
    baseRj: String,
    currentRj: String,
    dlsiteWorkno: String,
    originalRj: String,
    selectedLang: String,
    preferInitialRj: Boolean
): List<String> {
    val normalizedLang = selectedLang.trim().uppercase().ifBlank { "JPN" }
    return buildList {
        if (preferInitialRj) add(baseRj)
        add(currentRj)
        add(dlsiteWorkno)
        if (normalizedLang == "JPN") {
            add(originalRj)
            add(baseRj)
        }
    }
        .map { it.trim().uppercase() }
        .filter { ALBUM_DETAIL_RJ_CODE_REGEX.matches(it) }
        .distinct()
}

internal fun resolveAsmrOneTrackWorkId(
    resolvedWorkId: String,
    resolvedDetails: WorkDetailsResponse?,
    selectedLang: String,
    selectedRjs: List<String>
): String? {
    val normalizedWorkId = resolvedWorkId.trim().ifBlank { return null }
    val normalizedLang = selectedLang.trim().uppercase().ifBlank { "JPN" }
    if (normalizedLang == "JPN") return normalizedWorkId

    val exactRjs = selectedRjs
        .map { it.trim().uppercase() }
        .filter { ALBUM_DETAIL_RJ_CODE_REGEX.matches(it) }
        .toSet()
    val details = resolvedDetails ?: return null
    val resolvedSourceRj = details.source_id.trim().uppercase()
    if (resolvedSourceRj in exactRjs) return normalizedWorkId

    val otherEditions = details.other_language_editions_in_db.orEmpty()
    val exactEdition = otherEditions.firstOrNull { edition ->
        edition.source_id.orEmpty().trim().uppercase() in exactRjs
    }
    val languageEdition = exactEdition ?: otherEditions.firstOrNull { edition ->
        asmrOneEditionMatchesLanguage(edition.lang.orEmpty(), normalizedLang)
    }
    return languageEdition?.id?.takeIf { it > 0 }?.toString()
}

private fun asmrOneEditionMatchesLanguage(languageLabel: String, selectedLang: String): Boolean {
    val normalizedLabel = languageLabel.trim().uppercase()
    return when (selectedLang) {
        "CHI_HANS" -> normalizedLabel.contains("CHI_HANS") ||
            normalizedLabel.contains("简体") ||
            normalizedLabel.contains("簡体") ||
            normalizedLabel.contains("简中") ||
            normalizedLabel.contains("簡中")

        "CHI_HANT" -> normalizedLabel.contains("CHI_HANT") ||
            normalizedLabel.contains("繁体") ||
            normalizedLabel.contains("繁體") ||
            normalizedLabel.contains("繁中")

        "KO_KR" -> normalizedLabel.contains("KO_KR") ||
            normalizedLabel.contains("韩") ||
            normalizedLabel.contains("韓")

        "ENG" -> normalizedLabel.contains("ENG") ||
            normalizedLabel.contains("英语") ||
            normalizedLabel.contains("英語")

        else -> normalizedLabel == selectedLang
    }
}

private val ALBUM_DETAIL_RJ_CODE_REGEX = Regex("""RJ\d{6,}""")

internal data class AsmrOneLeafDownload(
    val url: String,
    val relativePath: String,
    val duration: Double?
)

internal const val DlsiteTrialDownloadDirectoryName = "体验版"

internal fun flattenAsmrOneTracks(tree: List<AsmrOneTrackNodeResponse>): List<Track> {
    val leaves = flattenAsmrOneLeafDownloads(tree)
    return leaves.map {
        Track(
            albumId = 0,
            title = it.relativePath.substringAfterLast('/'),
            path = it.url,
            duration = it.duration ?: 0.0
        )
    }
}

internal fun filterDownloadableMediaTree(tree: List<AsmrOneTrackNodeResponse>): List<AsmrOneTrackNodeResponse> {
    return tree.mapNotNull { node ->
        val filteredChildren = filterDownloadableMediaTree(node.children.orEmpty())
        val url = (node.mediaDownloadUrl ?: node.streamUrl).orEmpty().trim()
        when {
            filteredChildren.isNotEmpty() -> node.copy(children = filteredChildren)
            url.isBlank() -> null
            else -> {
                val fileType = treeFileTypeForNode(node.title.orEmpty(), url, node.type)
                if (isDownloadableTreeFileType(fileType)) node.copy(children = null) else null
            }
        }
    }
}

internal fun buildDlsiteTrialDownloadTree(trialTracks: List<Track>): List<AsmrOneTrackNodeResponse> {
    var mediaIndex = 1
    return trialTracks.mapNotNull { track ->
        val url = track.path.trim()
        if (!url.startsWith("http", ignoreCase = true)) return@mapNotNull null

        val mediaType = inferDlsiteTrialMediaType(track.title, url) ?: return@mapNotNull null
        val ext = inferDlsiteTrialDownloadExtension(url, mediaType) ?: return@mapNotNull null
        val fallbackTitle = if (mediaType == TreeFileType.Video) "体验视频" else "体验音频"
        val safeTitle = sanitizeDlsiteTrialFileBaseName(track.title, fallbackTitle)
        val fileName = "${mediaIndex.toString().padStart(2, '0')}_$safeTitle.$ext"
        mediaIndex += 1

        AsmrOneTrackNodeResponse(
            title = fileName,
            mediaDownloadUrl = url
        )
    }
}

internal suspend fun fetchAsmrOneTracksFromBackup(
    candidateRjs: List<String>,
    fetchBackup: suspend (String) -> Pair<String, List<AsmrOneTrackNodeResponse>>?
): Pair<String?, List<AsmrOneTrackNodeResponse>> {
    candidateRjs
        .asSequence()
        .map { it.trim().uppercase() }
        .filter { it.isNotBlank() }
        .distinct()
        .forEach { rj ->
            val backupResult = runCatching { fetchBackup(rj) }.getOrNull()
            val tree = backupResult?.second.orEmpty()
            if (backupResult != null && tree.isNotEmpty()) {
                return backupResult.first.takeIf { it.isNotBlank() } to tree
            }
        }
    return null to emptyList()
}

private fun inferDlsiteTrialMediaType(title: String, url: String): TreeFileType? {
    val normalizedUrl = url.trim()
    val normalizedTitle = title.trim()
    if (normalizedTitle.contains("ZIP", ignoreCase = true)) return null
    if (normalizedUrl.contains("trial_download", ignoreCase = true)) return null
    if (isVideoPreviewUrl(normalizedUrl)) return TreeFileType.Video

    return when (treeFileTypeForNode(normalizedTitle, normalizedUrl)) {
        TreeFileType.Audio -> TreeFileType.Audio
        TreeFileType.Video -> TreeFileType.Video
        else -> if (normalizedUrl.startsWith("http", ignoreCase = true)) TreeFileType.Audio else null
    }
}

private fun sanitizeDlsiteTrialFileBaseName(title: String, fallback: String): String {
    return title.trim()
        .substringBeforeLast('.')
        .ifBlank { fallback }
        .replace(Regex("""[\\/:*?\"<>|]"""), "_")
}

private fun inferDlsiteTrialDownloadExtension(url: String, mediaType: TreeFileType): String? {
    val ext = url
        .substringBefore('#')
        .substringBefore('?')
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .substringAfterLast('.', "")
        .lowercase()

    if (ext.isNotBlank()) {
        return when (treeFileTypeForName("sample.$ext")) {
            mediaType -> ext
            else -> null
        }
    }

    return when (mediaType) {
        TreeFileType.Audio -> "mp3"
        TreeFileType.Video -> "mp4"
        else -> null
    }
}

internal fun flattenAsmrOneLeafDownloads(tree: List<AsmrOneTrackNodeResponse>): List<AsmrOneLeafDownload> {
    val out = mutableListOf<AsmrOneLeafDownload>()
    fun sanitizeSegment(name: String): String {
        return name.trim().ifEmpty { "item" }.replace(Regex("""[\\/:*?"<>|]"""), "_")
    }
    fun walk(nodes: List<AsmrOneTrackNodeResponse>, parentPath: String) {
        nodes.forEach { node ->
            val title = node.title?.trim().orEmpty().ifBlank { "item" }
            val safeTitle = sanitizeSegment(title)
            val path = if (parentPath.isBlank()) safeTitle else "$parentPath/$safeTitle"
            val children = node.children.orEmpty()
            val url = node.mediaDownloadUrl ?: node.streamUrl
            if (!url.isNullOrBlank() && children.isEmpty()) {
                out.add(AsmrOneLeafDownload(url = url, relativePath = path, duration = node.duration))
            } else if (children.isNotEmpty()) {
                walk(children, path)
            }
        }
    }
    walk(tree, "")
    return out
}
