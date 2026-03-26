package com.asmr.player.ui.library

import android.content.Context
import android.util.Log
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

data class AlbumDetailModel(
    val baseRjCode: String,
    val rjCode: String,
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

internal enum class DlsiteChinesePreference {
    None,
    Hans,
    Hant
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
        if (!hasJpn) addAll(defaultDlsiteEditions(clean))
        addAll(editions)
    }.distinctBy { it.lang.trim().uppercase() }
        .sortedWith(compareBy({ it.displayOrder }, { it.lang }))
}

internal fun resolveInitialDlsiteLoadTarget(
    baseRj: String,
    editions: List<DlsiteLanguageEdition>,
    currentSelectedLang: String = "JPN",
    preserveCurrentSelection: Boolean = false,
    chinesePreference: DlsiteChinesePreference = DlsiteChinesePreference.None
): ResolvedDlsiteLoadTarget {
    val clean = baseRj.trim().uppercase()
    if (clean.isBlank()) {
        return ResolvedDlsiteLoadTarget(
            editions = emptyList(),
            selectedLang = "JPN",
            workno = ""
        )
    }
    val merged = mergeDlsiteEditions(clean, editions)
    val normalizedCurrentLang = currentSelectedLang.trim().uppercase().ifBlank { "JPN" }
    val hasHans = merged.any { it.lang.equals("CHI_HANS", ignoreCase = true) }
    val hasHant = merged.any { it.lang.equals("CHI_HANT", ignoreCase = true) }
    val preservedTarget = if (preserveCurrentSelection) {
        merged.firstOrNull { it.lang.equals(normalizedCurrentLang, ignoreCase = true) }
    } else {
        null
    }
    val preferredLang = when {
        preservedTarget != null -> preservedTarget.lang
        chinesePreference == DlsiteChinesePreference.Hans && hasHans -> "CHI_HANS"
        chinesePreference == DlsiteChinesePreference.Hant && hasHant -> "CHI_HANT"
        hasHans -> "CHI_HANS"
        hasHant -> "CHI_HANT"
        else -> "JPN"
    }
    val selected = preservedTarget
        ?: merged.firstOrNull { it.lang.equals(preferredLang, ignoreCase = true) }
        ?: merged.firstOrNull { it.lang.equals("JPN", ignoreCase = true) }
        ?: merged.firstOrNull()
    val selectedLang = selected?.lang?.trim().orEmpty().ifBlank { preferredLang }
    val selectedWorkno = selected?.workno?.trim()?.uppercase().orEmpty().ifBlank { clean }
    return ResolvedDlsiteLoadTarget(
        editions = merged,
        selectedLang = selectedLang,
        workno = selectedWorkno
    )
}

internal data class AsmrOneLeafDownload(
    val url: String,
    val relativePath: String,
    val duration: Double?
)

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
