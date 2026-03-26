package com.asmr.player.ui.library

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.asmr.player.data.local.db.AppDatabaseProvider
import com.asmr.player.data.local.db.entities.LocalTreeCacheEntity
import com.asmr.player.data.remote.auth.DlsiteAuthStore
import com.asmr.player.data.remote.api.AsmrOneTrackNodeResponse
import com.asmr.player.data.remote.scraper.DlsiteRecommendedWork
import com.asmr.player.data.remote.scraper.DlsiteRecommendations
import com.asmr.player.domain.model.Album
import com.asmr.player.domain.model.Track
import com.asmr.player.playback.MediaItemFactory
import com.asmr.player.data.remote.NetworkHeaders
import com.asmr.player.cache.CacheImageModel
import com.asmr.player.data.remote.dlsite.DlsiteLanguageEdition
import com.asmr.player.ui.dlsite.DlsitePlayViewModel
import com.asmr.player.util.DlsiteAntiHotlink
import com.asmr.player.util.SmartSortKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import com.asmr.player.ui.common.rememberDominantColor
import com.asmr.player.ui.common.SubtitleStamp
import com.asmr.player.ui.common.DiscPlaceholder
import com.asmr.player.ui.common.AsmrAsyncImage
import com.asmr.player.ui.common.AsmrShimmerPlaceholder
import com.asmr.player.ui.common.CvChipsFlow
import com.asmr.player.ui.common.EaraLogoLoadingIndicator
import com.asmr.player.ui.common.collapsibleHeaderUiState
import com.asmr.player.ui.common.rememberCollapsibleHeaderState
import com.asmr.player.ui.playlists.PlaylistPickerScreen
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.ui.common.LocalBottomOverlayPadding
import com.asmr.player.ui.common.thinScrollbar
import com.asmr.player.ui.theme.AsmrPlayerTheme
import com.asmr.player.ui.theme.dynamicPageContainerColor
import com.asmr.player.util.Formatting
import com.asmr.player.util.MessageManager
import com.asmr.player.util.RemoteSubtitleSource

internal sealed class AsmrTreeUiEntry {
    abstract val path: String
    abstract val title: String
    abstract val depth: Int

    data class Folder(
        override val path: String,
        override val title: String,
        override val depth: Int
    ) : AsmrTreeUiEntry()

    data class File(
        override val path: String,
        override val title: String,
        override val depth: Int,
        val fileType: TreeFileType,
        val isPlayable: Boolean,
        val url: String? = null
    ) : AsmrTreeUiEntry()
}

internal sealed class LocalTreeUiEntry {
    abstract val path: String
    abstract val title: String
    abstract val depth: Int

    data class Folder(
        override val path: String,
        override val title: String,
        override val depth: Int
    ) : LocalTreeUiEntry()

    data class File(
        override val path: String,
        override val title: String,
        override val depth: Int,
        val absolutePath: String,
        val fileType: TreeFileType,
        val track: Track?
    ) : LocalTreeUiEntry()
}

internal enum class TreeFileType {
    Audio,
    Video,
    Image,
    Subtitle,
    Text,
    Pdf,
    Other
}

internal fun treeFileTypeForName(fileName: String): TreeFileType {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "mp3", "wav", "flac", "m4a", "ogg", "aac", "opus" -> TreeFileType.Audio
        "mp4", "mkv", "webm", "mov", "m4v" -> TreeFileType.Video
        "jpg", "jpeg", "png", "webp", "gif" -> TreeFileType.Image
        "lrc", "srt", "vtt" -> TreeFileType.Subtitle
        "txt", "md", "nfo" -> TreeFileType.Text
        "pdf" -> TreeFileType.Pdf
        else -> TreeFileType.Other
    }
}

internal fun treeFileTypeForNode(title: String, url: String?): TreeFileType {
    val t = title.trim()
    val fromTitle = treeFileTypeForName(t)

    val urlName = url
        ?.substringBefore('#')
        ?.substringBefore('?')
        ?.substringAfterLast('/')
        ?.substringAfterLast('\\')
        .orEmpty()
    val fromUrl = if (urlName.isNotBlank()) treeFileTypeForName(urlName) else TreeFileType.Other

    return when {
        fromUrl != TreeFileType.Other -> fromUrl
        fromTitle != TreeFileType.Other -> fromTitle
        url != null && url.isNotBlank() -> TreeFileType.Audio
        else -> TreeFileType.Other
    }
}

internal fun buildVideoMediaItem(
    title: String,
    uriOrPath: String,
    artworkUri: String,
    artist: String
): MediaItem? {
    val trimmed = uriOrPath.trim()
    if (trimmed.isBlank()) return null
    val uri = if (
        trimmed.startsWith("http", ignoreCase = true) ||
            trimmed.startsWith("content://", ignoreCase = true) ||
            trimmed.startsWith("file://", ignoreCase = true)
    ) {
        trimmed.toUri()
    } else {
        Uri.fromFile(File(trimmed))
    }
    val ext = trimmed.substringBefore('#').substringBefore('?').substringAfterLast('.', "").lowercase()
    val mimeType = when (ext) {
        "mp4", "m4v" -> "video/mp4"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        "mov" -> "video/quicktime"
        else -> "video/*"
    }
    val displayTitle = title.ifBlank { trimmed.substringAfterLast('/').substringAfterLast('\\') }
    val metadata = androidx.media3.common.MediaMetadata.Builder()
        .setTitle(displayTitle)
        .setArtist(artist.trim())
        .setArtworkUri(artworkUri.trim().takeIf { it.isNotBlank() }?.toUri())
        .setExtras(android.os.Bundle().apply { putBoolean("is_video", true) })
        .build()
    return MediaItem.Builder()
        .setMediaId(trimmed)
        .setUri(uri)
        .setMimeType(mimeType)
        .setMediaMetadata(metadata)
        .build()
}

internal sealed class FileSizeSource {
    data object None : FileSizeSource()
    data class Local(val path: String) : FileSizeSource()
    data class Remote(val url: String) : FileSizeSource()
}

internal data class DirectoryBreadcrumbSegment(
    val label: String,
    val path: String
)

internal data class DirectoryFolderItem(
    val path: String,
    val title: String
)

internal data class DirectoryFileItem(
    val path: String,
    val title: String,
    val fileType: TreeFileType,
    val isPlayable: Boolean,
    val durationSeconds: Double? = null,
    val sizeSource: FileSizeSource = FileSizeSource.None,
    val absolutePath: String = "",
    val url: String = "",
    val track: Track? = null,
    val thumbnailModel: Any? = null,
    val playlistTarget: PlaylistAddTarget? = null,
    val subtitleSources: List<RemoteSubtitleSource> = emptyList(),
    val showSubtitleStamp: Boolean = false
)

internal data class DirectoryBrowserResult(
    val currentPath: String,
    val breadcrumbs: List<DirectoryBreadcrumbSegment>,
    val folders: List<DirectoryFolderItem>,
    val files: List<DirectoryFileItem>
) {
    val batchTargets: List<PlaylistAddTarget>
        get() = files.mapNotNull { file ->
            when (file.fileType) {
                TreeFileType.Audio, TreeFileType.Video -> file.playlistTarget
                else -> null
            }
        }
}

internal fun buildBreadcrumbSegments(currentPath: String): List<DirectoryBreadcrumbSegment> {
    val normalized = currentPath.trim().trim('/')
    if (normalized.isBlank()) return emptyList()
    val segments = normalized.split('/').filter { it.isNotBlank() }
    val out = mutableListOf<DirectoryBreadcrumbSegment>()
    var path = ""
    segments.forEach { segment ->
        path = if (path.isBlank()) segment else "$path/$segment"
        out += DirectoryBreadcrumbSegment(label = segment, path = path)
    }
    return out
}

internal fun albumArtistLabel(album: Album): String {
    return when {
        album.cv.isNotBlank() && album.circle.isNotBlank() -> "${album.circle} / ${album.cv}"
        album.cv.isNotBlank() -> album.cv
        album.circle.isNotBlank() -> album.circle
        album.rjCode.isNotBlank() -> album.rjCode
        else -> album.workId
    }.trim()
}

internal fun albumArtworkLabel(album: Album): String {
    return album.coverPath.ifBlank { album.coverUrl }
}

internal data class LocalTreeUiResult(
    val entries: List<LocalTreeUiEntry>
)

internal data class AsmrTreeUiResult(
    val entries: List<AsmrTreeUiEntry>
)

internal fun folderPathPrefixes(path: String): List<String> {
    val segs = path.split('/').filter { it.isNotBlank() }
    if (segs.isEmpty()) return emptyList()
    val out = ArrayList<String>(segs.size)
    var cur = ""
    for (seg in segs) {
        cur = if (cur.isBlank()) seg else "$cur/$seg"
        out.add(cur)
    }
    return out
}

internal class LocalTreeNode(
    val name: String,
    val path: String,
    val children: MutableMap<String, LocalTreeNode> = linkedMapOf(),
    var absolutePath: String? = null,
    var fileType: TreeFileType = TreeFileType.Other,
    var track: Track? = null
)

internal data class LocalFolderStats(
    var audioCount: Int = 0,
    var videoCount: Int = 0,
    var hasWav: Boolean = false,
    var hasMp4: Boolean = false
)

internal data class LocalTreeIndex(
    val root: LocalTreeNode,
    val folderStats: Map<String, LocalFolderStats>
)

internal fun findLocalTreeNode(root: LocalTreeNode, folderPath: String): LocalTreeNode? {
    val normalized = folderPath.trim().trimStart('/').trimEnd('/')
    if (normalized.isBlank()) return root
    var cur: LocalTreeNode = root
    val segments = normalized.split('/').filter { it.isNotBlank() }
    for (seg in segments) {
        val next = cur.children[seg] ?: return null
        cur = next
    }
    return cur
}

internal fun siblingAudioTracksForEntry(index: LocalTreeIndex, entryPath: String): List<Track> {
    val folderPath = entryPath.substringBeforeLast('/', "")
    val node = findLocalTreeNode(index.root, folderPath) ?: index.root
    return node.children.values
        .asSequence()
        .filter { it.children.isEmpty() && it.absolutePath != null && it.fileType == TreeFileType.Audio && it.track != null }
        .sortedBy { SmartSortKey.of(it.name) }
        .mapNotNull { it.track }
        .toList()
}

internal fun siblingPlayableNodesForEntry(index: LocalTreeIndex, entryPath: String): List<LocalTreeNode> {
    val folderPath = entryPath.substringBeforeLast('/', "")
    val node = findLocalTreeNode(index.root, folderPath) ?: index.root
    return node.children.values
        .asSequence()
        .filter { it.children.isEmpty() && it.absolutePath != null && (it.fileType == TreeFileType.Audio || it.fileType == TreeFileType.Video) }
        .filter { it.fileType != TreeFileType.Audio || it.track != null }
        .sortedBy { SmartSortKey.of(it.name) }
        .toList()
}

internal fun buildLocalDirectoryBrowser(
    index: LocalTreeIndex,
    currentPath: String,
    album: Album,
    shouldShowSubtitleStamp: (Track?) -> Boolean
): DirectoryBrowserResult {
    val normalizedPath = currentPath.trim().trim('/')
    val currentNode = findLocalTreeNode(index.root, normalizedPath) ?: index.root
    val folders = currentNode.children.values
        .asSequence()
        .filter { it.children.isNotEmpty() }
        .sortedBy { SmartSortKey.of(it.name) }
        .map { child ->
            DirectoryFolderItem(
                path = child.path,
                title = child.name
            )
        }
        .toList()
    val files = currentNode.children.values
        .asSequence()
        .filter { it.children.isEmpty() && it.absolutePath != null }
        .sortedBy { SmartSortKey.of(it.name) }
        .mapNotNull { child ->
            val absolutePath = child.absolutePath ?: return@mapNotNull null
            val displayTitle = child.track?.title?.ifBlank { child.name.substringBeforeLast('.') }
                ?: child.name.substringBeforeLast('.')
            val playlistTarget = when (child.fileType) {
                TreeFileType.Audio -> child.track?.let { PlaylistAddTarget.fromTrack(album, it) }
                TreeFileType.Video -> PlaylistAddTarget.fromVideo(album, displayTitle, absolutePath)
                else -> null
            }
            DirectoryFileItem(
                path = child.path,
                title = displayTitle,
                fileType = child.fileType,
                isPlayable = child.track != null || child.fileType == TreeFileType.Video,
                durationSeconds = child.track?.duration?.takeIf { it > 0.0 },
                sizeSource = FileSizeSource.Local(absolutePath),
                absolutePath = absolutePath,
                url = absolutePath,
                track = child.track,
                thumbnailModel = if (child.fileType == TreeFileType.Image) absolutePath else null,
                playlistTarget = playlistTarget,
                showSubtitleStamp = shouldShowSubtitleStamp(child.track)
            )
        }
        .toList()
    return DirectoryBrowserResult(
        currentPath = normalizedPath,
        breadcrumbs = buildBreadcrumbSegments(normalizedPath),
        folders = folders,
        files = files
    )
}

internal class RemoteTreeNode(
    val name: String,
    val path: String,
    val children: MutableMap<String, RemoteTreeNode> = linkedMapOf(),
    var fileType: TreeFileType = TreeFileType.Other,
    var url: String = "",
    var durationSeconds: Double? = null,
    var subtitleSources: List<RemoteSubtitleSource> = emptyList(),
    var playlistTarget: PlaylistAddTarget? = null
)

internal data class RemoteTreeIndex(
    val root: RemoteTreeNode
)

internal fun findRemoteTreeNode(root: RemoteTreeNode, folderPath: String): RemoteTreeNode? {
    val normalized = folderPath.trim().trim('/')
    if (normalized.isBlank()) return root
    var current = root
    normalized.split('/').filter { it.isNotBlank() }.forEach { segment ->
        current = current.children[segment] ?: return null
    }
    return current
}

internal fun buildRemoteTreeIndex(
    tree: List<AsmrOneTrackNodeResponse>,
    album: Album
): RemoteTreeIndex {
    val root = RemoteTreeNode(name = "", path = "")
    val subtitleExts = setOf("lrc", "srt", "vtt")
    val mediaExts = setOf("mp3", "wav", "flac", "m4a", "ogg", "aac", "opus", "mp4", "mkv", "webm", "mov", "m4v")

    fun sanitize(name: String): String {
        return name.trim().ifEmpty { "item" }.replace(Regex("""[\\/:*?"<>|]"""), "_")
    }

    data class LeafFile(
        val rawTitle: String,
        val safeTitle: String,
        val url: String,
        val duration: Double?,
        val fileType: TreeFileType
    ) {
        val ext: String = rawTitle.substringAfterLast('.', "").lowercase()
        val baseName: String = rawTitle.substringBeforeLast('.')
        val displayTitle: String = sanitize(baseName).ifBlank { safeTitle.substringBeforeLast('.') }
    }

    fun buildSubtitlesForMedia(media: LeafFile, siblings: List<LeafFile>): List<RemoteSubtitleSource> {
        val base = media.baseName
        if (base.isBlank()) return emptyList()
        return siblings.filter { subtitleExts.contains(it.ext) }.mapNotNull { sibling ->
            val siblingBase = sibling.baseName
            val language = when {
                siblingBase == base -> "default"
                siblingBase.startsWith("$base.") -> {
                    val suffix = siblingBase.substring(base.length + 1)
                    val parts = suffix.split('.').filter { it.isNotBlank() }
                    val valid = parts.filter { !mediaExts.contains(it.lowercase()) }
                    when {
                        valid.isEmpty() -> "zh"
                        valid.size == 1 -> valid[0]
                        else -> valid.last()
                    }
                }
                else -> return@mapNotNull null
            }
            RemoteSubtitleSource(
                url = sibling.url,
                language = language,
                ext = sibling.ext.ifBlank { "vtt" }
            )
        }.sortedWith(
            compareBy<RemoteSubtitleSource> { source ->
                val preferred = listOf("default", "zh", "cn", "chs", "ja", "jp", "jpn")
                val index = preferred.indexOf(source.language.lowercase())
                if (index >= 0) index else Int.MAX_VALUE
            }.thenBy { it.url }
        )
    }

    fun walk(
        nodes: List<AsmrOneTrackNodeResponse>,
        parentNode: RemoteTreeNode,
        parentPath: String
    ) {
        val leafFiles = nodes.mapNotNull { node ->
            val children = node.children.orEmpty()
            val url = node.mediaDownloadUrl ?: node.streamUrl
            if (!url.isNullOrBlank() && children.isEmpty()) {
                val rawTitle = node.title?.trim().orEmpty().ifBlank { "item" }
                val safeTitle = sanitize(rawTitle)
                LeafFile(
                    rawTitle = rawTitle,
                    safeTitle = safeTitle,
                    url = url,
                    duration = node.duration,
                    fileType = treeFileTypeForNode(rawTitle, url)
                )
            } else {
                null
            }
        }

        leafFiles.forEach { leaf ->
            if (leaf.fileType == TreeFileType.Other || leaf.fileType == TreeFileType.Subtitle) return@forEach
            val path = if (parentPath.isBlank()) leaf.safeTitle else "$parentPath/${leaf.safeTitle}"
            val child = parentNode.children.getOrPut(leaf.safeTitle) {
                RemoteTreeNode(name = leaf.safeTitle, path = path)
            }
            val subtitleSources = when (leaf.fileType) {
                TreeFileType.Audio, TreeFileType.Video -> buildSubtitlesForMedia(leaf, leafFiles)
                else -> emptyList()
            }
            val playlistTarget = when (leaf.fileType) {
                TreeFileType.Audio -> PlaylistAddTarget.fromTrack(
                    album = album,
                    track = Track(
                        albumId = album.id,
                        title = leaf.displayTitle,
                        path = leaf.url,
                        duration = leaf.duration ?: 0.0
                    )
                )
                TreeFileType.Video -> PlaylistAddTarget.fromVideo(album, leaf.displayTitle, leaf.url)
                else -> null
            }
            child.fileType = leaf.fileType
            child.url = leaf.url
            child.durationSeconds = leaf.duration
            child.subtitleSources = subtitleSources
            child.playlistTarget = playlistTarget
        }

        nodes.forEach { node ->
            val children = node.children.orEmpty()
            if (children.isEmpty()) return@forEach
            val rawTitle = node.title?.trim().orEmpty().ifBlank { "item" }
            val safeTitle = sanitize(rawTitle)
            val path = if (parentPath.isBlank()) safeTitle else "$parentPath/$safeTitle"
            val childNode = parentNode.children.getOrPut(safeTitle) {
                RemoteTreeNode(name = safeTitle, path = path)
            }
            walk(children, childNode, path)
        }
    }

    walk(tree, root, "")
    return RemoteTreeIndex(root = root)
}

internal fun buildRemoteDirectoryBrowser(
    index: RemoteTreeIndex,
    currentPath: String
): DirectoryBrowserResult {
    val normalizedPath = currentPath.trim().trim('/')
    val currentNode = findRemoteTreeNode(index.root, normalizedPath) ?: index.root
    val folders = currentNode.children.values
        .asSequence()
        .filter { it.children.isNotEmpty() }
        .sortedBy { SmartSortKey.of(it.name) }
        .map { child ->
            DirectoryFolderItem(
                path = child.path,
                title = child.name
            )
        }
        .toList()
    val files = currentNode.children.values
        .asSequence()
        .filter { it.children.isEmpty() && it.url.isNotBlank() && it.fileType != TreeFileType.Subtitle && it.fileType != TreeFileType.Other }
        .sortedBy { SmartSortKey.of(it.name) }
        .map { child ->
            DirectoryFileItem(
                path = child.path,
                title = child.name.substringBeforeLast('.'),
                fileType = child.fileType,
                isPlayable = child.fileType == TreeFileType.Audio || child.fileType == TreeFileType.Video,
                durationSeconds = child.durationSeconds,
                sizeSource = if (child.url.isNotBlank()) FileSizeSource.Remote(child.url) else FileSizeSource.None,
                absolutePath = child.url,
                url = child.url,
                playlistTarget = child.playlistTarget,
                subtitleSources = child.subtitleSources,
                showSubtitleStamp = child.subtitleSources.isNotEmpty()
            )
        }
        .toList()
    return DirectoryBrowserResult(
        currentPath = normalizedPath,
        breadcrumbs = buildBreadcrumbSegments(normalizedPath),
        folders = folders,
        files = files
    )
}

internal data class LocalTreeLeafCacheEntry(
    val relativePath: String,
    val absolutePath: String,
    val fileType: TreeFileType
)

internal data class LocalTreeIndexBuildResult(
    val index: LocalTreeIndex,
    val leaves: List<LocalTreeLeafCacheEntry>
)

internal suspend fun loadOrBuildLocalTreeIndex(
    context: android.content.Context,
    albumId: Long,
    albumPaths: List<String>,
    tracks: List<Track>
): LocalTreeIndex {
    val gson = Gson()
    val cacheKey = albumPaths.map { it.trim() }.filter { it.isNotBlank() }.sorted().joinToString("|")
    val stamp = computeAlbumPathsStamp(context, albumPaths)
    val dao = AppDatabaseProvider.get(context).localTreeCacheDao()
    val onlineTracks = tracks.filter { it.path.trim().startsWith("http", ignoreCase = true) }
    val onlineUrlSet = onlineTracks.map { it.path.trim() }.filter { it.isNotBlank() }.toSet()

    fun sanitizeSeg(name: String): String {
        return name.trim().ifEmpty { "item" }.replace(Regex("""[\\/:*?"<>|]"""), "_")
    }

    fun guessExtFromUrl(url: String): String {
        val u = url.substringBefore('?').trim()
        val ext = u.substringAfterLast('.', "").lowercase()
        if (ext.isBlank() || ext.length > 6) return ""
        if (ext.contains('/') || ext.contains('\\')) return ""
        return ext
    }

    fun buildOnlineLeaves(): List<LocalTreeLeafCacheEntry> {
        if (onlineTracks.isEmpty()) return emptyList()
        val audioExts = setOf("mp3", "flac", "wav", "m4a", "ogg", "aac", "opus")
        val videoExts = setOf("mp4", "mkv", "webm")
        return onlineTracks.mapNotNull { t ->
            val url = t.path.trim()
            if (url.isBlank()) return@mapNotNull null
            val ext = guessExtFromUrl(url)
            val type = when {
                videoExts.contains(ext) -> TreeFileType.Video
                audioExts.contains(ext) -> TreeFileType.Audio
                else -> TreeFileType.Audio
            }
            val groupPath = t.group.trim()
                .trim('/')
                .split('/')
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .joinToString("/") { sanitizeSeg(it) }

            val baseName = sanitizeSeg(t.title.ifBlank { "track" })
            val fileName = if (ext.isNotBlank() && !baseName.endsWith(".$ext", ignoreCase = true)) "$baseName.$ext" else baseName
            val rel = if (groupPath.isBlank()) fileName else "$groupPath/$fileName"
            LocalTreeLeafCacheEntry(relativePath = rel, absolutePath = url, fileType = type)
        }
    }

    fun mergeLeaves(localLeaves: List<LocalTreeLeafCacheEntry>, onlineLeaves: List<LocalTreeLeafCacheEntry>): List<LocalTreeLeafCacheEntry> {
        val filteredLocal = localLeaves.filter { leaf ->
            val abs = leaf.absolutePath.trim()
            !(abs.startsWith("http", ignoreCase = true) && !onlineUrlSet.contains(abs))
        }
        val byRel = linkedMapOf<String, LocalTreeLeafCacheEntry>()
        filteredLocal.forEach { byRel[it.relativePath] = it }
        onlineLeaves.forEach { leaf ->
            val existing = byRel[leaf.relativePath]
            if (existing == null) {
                byRel[leaf.relativePath] = leaf
            } else {
                val abs = existing.absolutePath.trim()
                if (abs.startsWith("http", ignoreCase = true)) byRel[leaf.relativePath] = leaf
            }
        }
        return byRel.values.toList()
    }
    val onlineLeaves = buildOnlineLeaves()

    val cached = dao.getByAlbumAndKey(albumId = albumId, cacheKey = cacheKey)
    if (cached != null && cached.stamp == stamp && cached.payloadJson.isNotBlank()) {
        val type = object : TypeToken<List<LocalTreeLeafCacheEntry>>() {}.type
        val leaves = runCatching { gson.fromJson<List<LocalTreeLeafCacheEntry>>(cached.payloadJson, type) }
            .getOrDefault(emptyList())
        val merged = mergeLeaves(localLeaves = leaves, onlineLeaves = onlineLeaves)
        if (merged.isNotEmpty()) {
            return buildLocalTreeIndexFromLeaves(leaves = merged, tracks = tracks)
        }
    }

    val built = buildLocalTreeIndexByScanning(context = context, albumPaths = albumPaths, tracks = tracks)
    val merged = mergeLeaves(localLeaves = built.leaves, onlineLeaves = onlineLeaves)
    dao.upsert(
        LocalTreeCacheEntity(
            albumId = albumId,
            cacheKey = cacheKey,
            stamp = stamp,
            payloadJson = gson.toJson(merged),
            updatedAt = System.currentTimeMillis()
        )
    )
    return buildLocalTreeIndexFromLeaves(leaves = merged, tracks = tracks)
}

internal fun computeAlbumPathsStamp(context: android.content.Context, albumPaths: List<String>): Long {
    val paths = albumPaths.map { it.trim() }.filter { it.isNotBlank() }.sorted()
    var acc = 1469598103934665603L
    paths.forEach { p ->
        val v = if (p.startsWith("content://")) {
            queryDocumentLastModified(context, p)
        } else {
            runCatching { java.io.File(p).lastModified() }.getOrDefault(0L)
        }
        acc = (acc xor v) * 1099511628211L
    }
    return acc
}

internal fun queryDocumentLastModified(context: android.content.Context, uriString: String): Long {
    val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return 0L
    return runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED),
            null,
            null,
            null
        )?.use { cursor ->
            val idx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            if (idx < 0) return@use 0L
            if (!cursor.moveToFirst()) return@use 0L
            cursor.getLong(idx)
        } ?: 0L
    }.getOrDefault(0L)
}

internal fun buildLocalTreeIndexByScanning(
    context: android.content.Context,
    albumPaths: List<String>,
    tracks: List<Track>
): LocalTreeIndexBuildResult {
    val root = LocalTreeNode(name = "", path = "")
    val trackByAbsolutePath = tracks.associateBy { it.path }
    val folderStats = linkedMapOf<String, LocalFolderStats>()

    fun updateFolderStats(segments: List<String>, type: TreeFileType, extLower: String) {
        val folderSegs = segments.dropLast(1)
        if (folderSegs.isEmpty()) return
        var cur = ""
        folderSegs.forEach { seg ->
            cur = if (cur.isBlank()) seg else "$cur/$seg"
            val st = folderStats.getOrPut(cur) { LocalFolderStats() }
            when (type) {
                TreeFileType.Audio -> {
                    st.audioCount += 1
                    if (extLower == "wav") st.hasWav = true
                }
                TreeFileType.Video -> {
                    st.videoCount += 1
                    if (extLower == "mp4") st.hasMp4 = true
                }
                else -> Unit
            }
        }
    }

    albumPaths.forEach { albumPath ->
        if (albumPath.startsWith("content://")) {
            val uri = Uri.parse(albumPath)
            val treeId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: ""
            val rootDocId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: treeId
            val treeUri = if (treeId.isNotBlank()) DocumentsContract.buildTreeDocumentUri(uri.authority, treeId) else uri
            
            fun query(parentDocId: String, parentRel: String) {
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
                context.contentResolver.query(childrenUri, arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ), null, null, null)?.use { cursor ->
                    val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(idIdx)
                        val name = cursor.getString(nameIdx)
                        val mime = cursor.getString(mimeIdx)
                        val rel = if (parentRel.isEmpty()) name else "$parentRel/$name"
                        
                        val segments = rel.split('/').filter { it.isNotBlank() }
                        var cur = root
                        segments.forEachIndexed { idx, seg ->
                            val nextPath = if (cur.path.isBlank()) seg else "${cur.path}/$seg"
                            val child = cur.children.getOrPut(seg) { LocalTreeNode(name = seg, path = nextPath) }
                            if (idx == segments.lastIndex) {
                                val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id).toString()
                                val ft = if (mime == DocumentsContract.Document.MIME_TYPE_DIR) TreeFileType.Other else treeFileTypeForName(name)
                                child.fileType = ft
                                if (mime != DocumentsContract.Document.MIME_TYPE_DIR && ft != TreeFileType.Other && ft != TreeFileType.Subtitle) {
                                    val track = trackByAbsolutePath[fileUri]
                                    if (ft == TreeFileType.Audio && track == null) {
                                        child.absolutePath = null
                                        child.track = null
                                    } else {
                                        child.absolutePath = fileUri
                                        child.track = track
                                    }
                                    if (ft == TreeFileType.Video || (ft == TreeFileType.Audio && track != null)) {
                                        updateFolderStats(segments, ft, name.substringAfterLast('.', "").lowercase())
                                    }
                                } else {
                                    child.absolutePath = null
                                    child.track = null
                                }
                            }
                            cur = child
                        }
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            query(id, rel)
                        }
                    }
                }
            }
            if (rootDocId.isNotBlank()) {
                query(rootDocId, "")
            }
        } else {
            val rootDir = java.io.File(albumPath)
            if (rootDir.exists()) {
                rootDir.walkTopDown().forEach { file ->
                    if (!file.isFile) return@forEach
                    val type = treeFileTypeForName(file.name)
                    if (type == TreeFileType.Other || type == TreeFileType.Subtitle) return@forEach
                    val track = trackByAbsolutePath[file.absolutePath]
                    if (type == TreeFileType.Audio && track == null) return@forEach

                    val rawRel = runCatching { file.relativeTo(rootDir).path }.getOrElse { file.name }
                    val rel = rawRel.replace('\\', '/').trim().trimStart('/')
                    val segments = rel.split('/').filter { it.isNotBlank() }
                    if (segments.isEmpty()) return@forEach

                    var cur = root
                    segments.forEachIndexed { idx, seg ->
                        val nextPath = if (cur.path.isBlank()) seg else "${cur.path}/$seg"
                        val child = cur.children.getOrPut(seg) { LocalTreeNode(name = seg, path = nextPath) }
                        if (idx == segments.lastIndex) {
                            child.absolutePath = file.absolutePath
                            child.fileType = type
                            child.track = track
                            if (type == TreeFileType.Audio || type == TreeFileType.Video) {
                                updateFolderStats(segments, type, file.extension.lowercase())
                            }
                        }
                        cur = child
                    }
                }
            }
        }
    }

    fun collectLeaves(node: LocalTreeNode, out: MutableList<LocalTreeLeafCacheEntry>) {
        if (node.children.isEmpty() && node.absolutePath != null) {
            out.add(
                LocalTreeLeafCacheEntry(
                    relativePath = node.path,
                    absolutePath = node.absolutePath ?: return,
                    fileType = node.fileType
                )
            )
            return
        }
        node.children.values.forEach { child -> collectLeaves(child, out) }
    }

    val leaves = mutableListOf<LocalTreeLeafCacheEntry>()
    collectLeaves(root, leaves)
    return LocalTreeIndexBuildResult(
        index = LocalTreeIndex(root = root, folderStats = folderStats),
        leaves = leaves
    )
}

internal fun buildLocalTreeIndexFromLeaves(
    leaves: List<LocalTreeLeafCacheEntry>,
    tracks: List<Track>
): LocalTreeIndex {
    val root = LocalTreeNode(name = "", path = "")
    val trackByAbsolutePath = tracks.associateBy { it.path }

    leaves.forEach { leaf ->
        if (leaf.fileType == TreeFileType.Subtitle) return@forEach
        val track = trackByAbsolutePath[leaf.absolutePath]
        if (leaf.fileType == TreeFileType.Audio && track == null) return@forEach
        val rel = leaf.relativePath.trim().trimStart('/')
        if (rel.isBlank()) return@forEach
        val segments = rel.split('/').filter { it.isNotBlank() }
        if (segments.isEmpty()) return@forEach
        var cur = root
        segments.forEachIndexed { idx, seg ->
            val nextPath = if (cur.path.isBlank()) seg else "${cur.path}/$seg"
            val child = cur.children.getOrPut(seg) { LocalTreeNode(name = seg, path = nextPath) }
            if (idx == segments.lastIndex) {
                child.absolutePath = leaf.absolutePath
                child.fileType = leaf.fileType
                child.track = track
            }
            cur = child
        }
    }

    return LocalTreeIndex(root = root, folderStats = emptyMap())
}

internal fun flattenLocalTreeIndex(
    index: LocalTreeIndex,
    expanded: Set<String>
): LocalTreeUiResult {
    fun nodeSortKey(n: LocalTreeNode): SmartSortKey = SmartSortKey.of(n.name)
    val out = mutableListOf<LocalTreeUiEntry>()

    fun flatten(node: LocalTreeNode, depth: Int) {
        val folders = node.children.values.filter { it.children.isNotEmpty() }.sortedBy(::nodeSortKey)
        val files = node.children.values.filter { it.children.isEmpty() && it.absolutePath != null }.sortedBy(::nodeSortKey)

        folders.forEach { child ->
            out.add(LocalTreeUiEntry.Folder(path = child.path, title = child.name, depth = depth))
            if (expanded.contains(child.path)) {
                flatten(child, depth + 1)
            }
        }
        files.forEach { child ->
            val title = child.track?.title?.ifBlank { child.name.substringBeforeLast('.') }
                ?: child.name.substringBeforeLast('.')
            out.add(
                LocalTreeUiEntry.File(
                    path = child.path,
                    title = title,
                    depth = depth,
                    absolutePath = child.absolutePath ?: return@forEach,
                    fileType = child.fileType,
                    track = child.track
                )
            )
        }
    }

    flatten(index.root, 0)
    return LocalTreeUiResult(entries = out)
}

internal data class AsmrOneLeafUi(
    val relativePath: String,
    val title: String,
    val url: String,
    val duration: Double?,
    val subtitles: List<com.asmr.player.util.RemoteSubtitleSource>
) {
    fun toTrack(): Track {
        return Track(
            albumId = 0,
            title = title,
            path = url,
            duration = duration ?: 0.0
        )
    }
}

internal fun flattenAsmrOneTracksForUi(tree: List<AsmrOneTrackNodeResponse>): List<AsmrOneLeafUi> {
    val out = mutableListOf<AsmrOneLeafUi>()
    fun sanitize(name: String): String = name.trim().ifEmpty { "item" }.replace(Regex("""[\\/:*?"<>|]"""), "_")

    val audioExts = setOf("mp3", "flac", "wav", "m4a", "ogg", "aac", "opus")
    val subtitleExts = setOf("lrc", "srt", "vtt")

    data class LeafFile(
        val rawTitle: String,
        val safeTitle: String,
        val url: String,
        val duration: Double?
    ) {
        val ext: String = rawTitle.substringAfterLast('.', "").lowercase()
        val baseName: String = rawTitle.substringBeforeLast('.')
    }

    fun buildSubtitlesForAudio(audio: LeafFile, siblings: List<LeafFile>): List<com.asmr.player.util.RemoteSubtitleSource> {
        val base = audio.baseName
        if (base.isBlank()) return emptyList()
        return siblings.filter { subtitleExts.contains(it.ext) }.mapNotNull { sib ->
            val sibBase = sib.baseName
            val lang = when {
                sibBase == base -> "default"
                sibBase.startsWith("$base.") -> {
                    val suffix = sibBase.substring(base.length + 1)
                    val parts = suffix.split('.').filter { it.isNotBlank() }
                    val valid = parts.filter { !audioExts.contains(it.lowercase()) }
                    when {
                        valid.isEmpty() -> "zh"
                        valid.size == 1 -> valid[0]
                        else -> valid.last()
                    }
                }
                else -> return@mapNotNull null
            }
            com.asmr.player.util.RemoteSubtitleSource(url = sib.url, language = lang, ext = sib.ext)
        }.sortedWith(
            compareBy<com.asmr.player.util.RemoteSubtitleSource> { s ->
                val prefer = listOf("default", "zh", "cn", "chs", "ja", "jp", "jpn")
                val idx = prefer.indexOf(s.language.lowercase())
                if (idx >= 0) idx else Int.MAX_VALUE
            }.thenBy { it.url }
        )
    }

    fun walk(nodes: List<AsmrOneTrackNodeResponse>, parentPath: String) {
        val leaves = nodes.mapNotNull { node ->
            val children = node.children.orEmpty()
            val url = node.mediaDownloadUrl ?: node.streamUrl
            if (!url.isNullOrBlank() && children.isEmpty()) {
                val rawTitle = node.title?.trim().orEmpty().ifBlank { "item" }
                val safeTitle = sanitize(rawTitle)
                LeafFile(rawTitle = rawTitle, safeTitle = safeTitle, url = url, duration = node.duration)
            } else null
        }

        leaves.filter { it.ext.isBlank() || audioExts.contains(it.ext) }.forEach { leaf ->
            val path = if (parentPath.isBlank()) leaf.safeTitle else "$parentPath/${leaf.safeTitle}"
            val displayTitle = sanitize(leaf.baseName).ifBlank { leaf.safeTitle }
            val subs = buildSubtitlesForAudio(leaf, leaves)
            out.add(
                AsmrOneLeafUi(
                    relativePath = path,
                    title = displayTitle,
                    url = leaf.url,
                    duration = leaf.duration,
                    subtitles = subs
                )
            )
        }

        nodes.forEach { node ->
            val children = node.children.orEmpty()
            if (children.isEmpty()) return@forEach
            val title = node.title?.trim().orEmpty().ifBlank { "item" }
            val safeTitle = sanitize(title)
            val path = if (parentPath.isBlank()) safeTitle else "$parentPath/$safeTitle"
            walk(children, path)
        }
    }
    walk(tree, "")
    return out
}

internal fun flattenAsmrOneTreeForUi(
    tree: List<AsmrOneTrackNodeResponse>,
    expanded: Set<String>
): AsmrTreeUiResult {
    val out = mutableListOf<AsmrTreeUiEntry>()
    fun sanitize(name: String): String = name.trim().ifEmpty { "item" }.replace(Regex("""[\\/:*?"<>|]"""), "_")

    data class FolderStats(
        var audioCount: Int = 0,
        var videoCount: Int = 0,
        var hasWav: Boolean = false,
        var hasMp4: Boolean = false
    )
    val folderStats = linkedMapOf<String, FolderStats>()
    fun updateFolderStats(parentPath: String, type: TreeFileType, extLower: String) {
        if (parentPath.isBlank()) return
        folderPathPrefixes(parentPath).forEach { folder ->
            val st = folderStats.getOrPut(folder) { FolderStats() }
            when (type) {
                TreeFileType.Audio -> {
                    st.audioCount += 1
                    if (extLower == "wav") st.hasWav = true
                }
                TreeFileType.Video -> {
                    st.videoCount += 1
                    if (extLower == "mp4") st.hasMp4 = true
                }
                else -> Unit
            }
        }
    }

    fun chooseRecommendedExpand(): String? {
        val entries = folderStats.entries.filter { it.value.audioCount > 0 || it.value.videoCount > 0 }
        if (entries.isEmpty()) return null

        fun bestAudio(): Map.Entry<String, FolderStats>? {
            val audioEntries = entries.filter { it.value.audioCount > 0 }
            if (audioEntries.isEmpty()) return null
            val maxAudio = audioEntries.maxOf { it.value.audioCount }
            val threshold = maxOf(1, (maxAudio * 0.7f).toInt())
            val candidates = audioEntries.filter { it.value.audioCount >= threshold }.ifEmpty { audioEntries }
            return candidates
                .sortedWith(
                    compareByDescending<Map.Entry<String, FolderStats>> { it.key.count { ch -> ch == '/' } }
                        .thenByDescending { it.value.audioCount }
                        .thenByDescending { it.value.hasWav }
                        .thenBy { it.key }
                )
                .firstOrNull()
        }

        fun bestVideo(): Map.Entry<String, FolderStats>? {
            val videoEntries = entries.filter { it.value.videoCount > 0 }
            if (videoEntries.isEmpty()) return null
            val maxVideo = videoEntries.maxOf { it.value.videoCount }
            val threshold = maxOf(1, (maxVideo * 0.7f).toInt())
            val candidates = videoEntries.filter { it.value.videoCount >= threshold }.ifEmpty { videoEntries }
            return candidates
                .sortedWith(
                    compareByDescending<Map.Entry<String, FolderStats>> { it.key.count { ch -> ch == '/' } }
                        .thenByDescending { it.value.videoCount }
                        .thenByDescending { it.value.hasMp4 }
                        .thenBy { it.key }
                )
                .firstOrNull()
        }

        val a = bestAudio()
        val v = bestVideo()
        val aCount = a?.value?.audioCount ?: 0
        val vCount = v?.value?.videoCount ?: 0
        return when {
            vCount > aCount -> v?.key
            aCount > vCount -> a?.key
            else -> v?.key ?: a?.key
        }
    }

    fun collectFolderStatsFromFullTree() {
        fun walkAll(nodes: List<AsmrOneTrackNodeResponse>, parentPath: String) {
            nodes.forEach { node ->
                val title = node.title?.trim().orEmpty().ifBlank { "item" }
                val safeTitle = sanitize(title)
                val path = if (parentPath.isBlank()) safeTitle else "$parentPath/$safeTitle"
                val children = node.children.orEmpty()
                val url = node.mediaDownloadUrl ?: node.streamUrl
                if (children.isEmpty()) {
                    val type = treeFileTypeForNode(title, url)
                    if (type == TreeFileType.Other || type == TreeFileType.Subtitle) return@forEach
                    val extLower = title.substringAfterLast('.', "").lowercase()
                    updateFolderStats(parentPath = parentPath, type = type, extLower = extLower)
                } else {
                    walkAll(children, path)
                }
            }
        }
        walkAll(tree, "")
    }

    fun walk(nodes: List<AsmrOneTrackNodeResponse>, parentPath: String, depth: Int) {
        nodes.forEach { node ->
            val title = node.title?.trim().orEmpty().ifBlank { "item" }
            val safeTitle = sanitize(title)
            val path = if (parentPath.isBlank()) safeTitle else "$parentPath/$safeTitle"
            val children = node.children.orEmpty()
            val url = node.mediaDownloadUrl ?: node.streamUrl
            if (children.isEmpty()) {
                val type = treeFileTypeForNode(title, url)
                if (type == TreeFileType.Other || type == TreeFileType.Subtitle) return@forEach
                out.add(
                    AsmrTreeUiEntry.File(
                        path = path,
                        title = safeTitle.substringBeforeLast('.'),
                        depth = depth,
                        fileType = type,
                        isPlayable = type == TreeFileType.Audio && !url.isNullOrBlank(),
                        url = url
                    )
                )
            } else {
                out.add(AsmrTreeUiEntry.Folder(path = path, title = safeTitle, depth = depth))
                if (expanded.contains(path)) {
                    walk(children, path, depth + 1)
                }
            }
        }
    }
    collectFolderStatsFromFullTree()
    walk(tree, "", 0)
    return AsmrTreeUiResult(entries = out)
}

internal fun fileTypeLabel(fileType: TreeFileType): String = when (fileType) {
    TreeFileType.Audio -> "音频"
    TreeFileType.Video -> "视频"
    TreeFileType.Image -> "图片"
    TreeFileType.Subtitle -> "字幕"
    TreeFileType.Text -> "文本"
    TreeFileType.Pdf -> "PDF"
    TreeFileType.Other -> "文件"
}

internal fun queryLocalFileSize(context: android.content.Context, path: String): Long? {
    val trimmed = path.trim()
    if (trimmed.isBlank()) return null
    return when {
        trimmed.startsWith("content://", ignoreCase = true) -> {
            runCatching {
                context.contentResolver.query(
                    Uri.parse(trimmed),
                    arrayOf(DocumentsContract.Document.COLUMN_SIZE),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    val index = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                    if (index < 0 || !cursor.moveToFirst()) null else cursor.getLong(index)
                }
            }.getOrNull()
        }
        else -> runCatching { File(trimmed).takeIf { it.exists() }?.length() }.getOrNull()
    }?.takeIf { it > 0L }
}

@Composable
internal fun DirectoryBreadcrumbBar(
    currentPath: String,
    breadcrumbs: List<DirectoryBreadcrumbSegment>,
    onNavigate: (String) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item {
            FilterChip(
                selected = currentPath.isBlank(),
                onClick = { onNavigate("") },
                label = { Text("根目录") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
        }
        items(
            items = breadcrumbs,
            key = { it.path },
            contentType = { "breadcrumb" }
        ) { crumb ->
            FilterChip(
                selected = crumb.path == currentPath,
                onClick = { onNavigate(crumb.path) },
                label = {
                    Text(
                        text = crumb.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    }
}

@Composable
internal fun CompactDirectoryBreadcrumbBar(
    currentPath: String,
    breadcrumbs: List<DirectoryBreadcrumbSegment>,
    onNavigate: (String) -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    val displayedCrumbs = remember(breadcrumbs) {
        when {
            breadcrumbs.size <= 3 -> breadcrumbs
            else -> listOf(
                DirectoryBreadcrumbSegment(label = "...", path = ""),
                breadcrumbs[breadcrumbs.lastIndex - 1],
                breadcrumbs.last()
            )
        }
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = colorScheme.surface.copy(alpha = 0.58f),
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            CompactBreadcrumbNode(
                text = "根目录",
                selected = currentPath.isBlank(),
                icon = Icons.Default.Home,
                onClick = { onNavigate("") }
            )
            displayedCrumbs.forEach { crumb ->
                Text(
                    text = "/",
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.textTertiary
                )
                if (crumb.label == "..." && crumb.path.isBlank()) {
                    Text(
                        text = "...",
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.textTertiary,
                        maxLines = 1
                    )
                } else {
                    CompactBreadcrumbNode(
                        text = crumb.label,
                        selected = crumb.path == currentPath,
                        onClick = { onNavigate(crumb.path) }
                    )
                }
            }
        }
    }
}

@Composable
internal fun CompactBreadcrumbNode(
    text: String,
    selected: Boolean,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) colorScheme.primary else colorScheme.textSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = if (selected) {
                MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
            } else {
                MaterialTheme.typography.labelLarge
            },
            color = if (selected) colorScheme.primary else colorScheme.textSecondary,
            modifier = Modifier.widthIn(max = 112.dp)
        )
    }
}

@Composable
internal fun CompactDirectoryBreadcrumbContent(
    currentPath: String,
    breadcrumbs: List<DirectoryBreadcrumbSegment>,
    onNavigate: (String) -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    val displayedCrumbs = remember(breadcrumbs) {
        when {
            breadcrumbs.size <= 3 -> breadcrumbs
            else -> listOf(
                DirectoryBreadcrumbSegment(label = "...", path = ""),
                breadcrumbs[breadcrumbs.lastIndex - 1],
                breadcrumbs.last()
            )
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        CompactBreadcrumbNode(
            text = "根目录",
            selected = currentPath.isBlank(),
            icon = Icons.Default.Home,
            onClick = { onNavigate("") }
        )
        displayedCrumbs.forEach { crumb ->
            Text(
                text = "/",
                style = MaterialTheme.typography.labelMedium,
                color = colorScheme.textTertiary
            )
            if (crumb.label == "..." && crumb.path.isBlank()) {
                Text(
                    text = "...",
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.textTertiary,
                    maxLines = 1
                )
            } else {
                CompactBreadcrumbNode(
                    text = crumb.label,
                    selected = crumb.path == currentPath,
                    onClick = { onNavigate(crumb.path) }
                )
            }
        }
    }
}

@Composable
internal fun DirectoryBrowserPanel(
    panelKey: String,
    currentPath: String,
    breadcrumbs: List<DirectoryBreadcrumbSegment>,
    batchTargets: List<PlaylistAddTarget>,
    folders: List<DirectoryFolderItem>,
    files: List<DirectoryFileItem>,
    onNavigate: (String) -> Unit,
    onAddToFavorites: (List<MediaItem>) -> Unit,
    onOpenBatchPlaylistPicker: (List<MediaItem>) -> Unit,
    onAddMediaItemsToQueue: (List<MediaItem>) -> Unit,
    folderKeyPrefix: String,
    fileKeyPrefix: String,
    emptyText: String = "当前目录暂无文件",
    fileContent: @Composable (
        file: DirectoryFileItem,
        selectionMode: Boolean,
        selected: Boolean,
        enterSelectionMode: () -> Unit,
        onSelectedChange: (Boolean) -> Unit
    ) -> Unit
) {
    val browserListState = rememberSaveable("dir-panel:$panelKey", saver = LazyListState.Saver) {
        LazyListState()
    }
    var selectionMode by remember(panelKey, currentPath) { mutableStateOf(false) }
    val selectedPaths = remember(panelKey, currentPath) { mutableStateListOf<String>() }
    val selectedFiles = remember(files, selectedPaths.toList()) {
        val selectedSet = selectedPaths.toSet()
        files.filter { selectedSet.contains(it.path) }
    }
    val activeTargets = remember(selectionMode, batchTargets, selectedFiles) {
        if (selectionMode) selectedFiles.mapNotNull { it.playlistTarget } else batchTargets
    }
    val batchSummaryText = remember(selectionMode, selectedPaths.size, batchTargets.size) {
        if (selectionMode) "已选 ${selectedPaths.size} 项" else "媒体 ${batchTargets.size} 项"
    }
    val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
    val maxHeight = remember(screenHeight) {
        (screenHeight * 0.48f).coerceIn(240.dp, 460.dp)
    }

    LaunchedEffect(panelKey, currentPath) {
        browserListState.scrollToItem(0)
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 1.dp,
        color = AsmrTheme.colorScheme.surface.copy(alpha = 0.44f),
        modifier = dlsiteElasticItemModifier(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            CompactDirectoryBreadcrumbContent(
                currentPath = currentPath,
                breadcrumbs = breadcrumbs,
                onNavigate = onNavigate
            )
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
            )
            DirectoryBatchBarEmbeddedV2(
                targets = activeTargets,
                summaryText = batchSummaryText,
                onAddToFavorites = onAddToFavorites,
                onOpenBatchPlaylistPicker = onOpenBatchPlaylistPicker,
                onAddMediaItemsToQueue = onAddMediaItemsToQueue
            )
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
            )
            LazyColumn(
                state = browserListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .thinScrollbar(browserListState),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                if (folders.isEmpty() && files.isEmpty()) {
                    item(key = "empty") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = emptyText,
                                color = AsmrTheme.colorScheme.textSecondary
                            )
                        }
                    }
                } else {
                    items(
                        items = folders,
                        key = { folder -> "$folderKeyPrefix:${folder.path}" },
                        contentType = { "folder" }
                    ) { folder ->
                        DirectoryFolderRow(
                            title = folder.title,
                            onClick = { onNavigate(folder.path) }
                        )
                    }
                    items(
                        items = files,
                        key = { file -> "$fileKeyPrefix:${file.path}" },
                        contentType = { "file" }
                    ) { file ->
                        val isSelected = selectedPaths.contains(file.path)
                        fileContent(
                            file,
                            selectionMode,
                            isSelected,
                            {
                                selectionMode = true
                                if (!selectedPaths.contains(file.path)) {
                                    selectedPaths.add(file.path)
                                }
                            },
                            { checked ->
                                if (checked) {
                                    if (!selectedPaths.contains(file.path)) {
                                        selectedPaths.add(file.path)
                                    }
                                    selectionMode = true
                                } else {
                                    selectedPaths.remove(file.path)
                                    if (selectedPaths.isEmpty()) {
                                        selectionMode = false
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun DirectoryBatchBarEmbedded(
    targets: List<PlaylistAddTarget>,
    summaryText: String,
    onAddToFavorites: (List<MediaItem>) -> Unit,
    onOpenBatchPlaylistPicker: (List<MediaItem>) -> Unit,
    onAddMediaItemsToQueue: (List<MediaItem>) -> Unit
) {
    val mediaItems = remember(targets) { targets.map { it.toMediaItem() } }
    val hasMediaItems = mediaItems.isNotEmpty()
    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "当前目录 ${mediaItems.size} 个音频/视频文件",
            style = MaterialTheme.typography.bodySmall,
            color = AsmrTheme.colorScheme.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilledTonalButton(
                onClick = { onAddToFavorites(mediaItems) },
                enabled = hasMediaItems,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Icon(Icons.Default.FavoriteBorder, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("收藏", style = MaterialTheme.typography.labelMedium)
            }
            OutlinedButton(
                onClick = { onOpenBatchPlaylistPicker(mediaItems) },
                enabled = hasMediaItems,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("列表", style = MaterialTheme.typography.labelMedium)
            }
            OutlinedButton(
                onClick = { onAddMediaItemsToQueue(mediaItems) },
                enabled = hasMediaItems,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("队列", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
internal fun DirectoryBatchBar(
    targets: List<PlaylistAddTarget>,
    onAddToFavorites: (List<MediaItem>) -> Unit,
    onOpenBatchPlaylistPicker: (List<MediaItem>) -> Unit,
    onAddMediaItemsToQueue: (List<MediaItem>) -> Unit,
    embedded: Boolean = false
) {
    val mediaItems = remember(targets) { targets.map { it.toMediaItem() } }
    val hasMediaItems = mediaItems.isNotEmpty()

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = AsmrTheme.colorScheme.surface.copy(alpha = 0.72f),
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "当前目录 ${mediaItems.size} 个音频/视频文件",
                style = MaterialTheme.typography.titleSmall,
                color = AsmrTheme.colorScheme.textPrimary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = { onAddToFavorites(mediaItems) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.FavoriteBorder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("收藏")
                }
                OutlinedButton(
                    onClick = { onOpenBatchPlaylistPicker(mediaItems) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("列表")
                }
                OutlinedButton(
                    onClick = { onAddMediaItemsToQueue(mediaItems) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("队列")
                }
            }
        }
    }
}

@Composable
internal fun DirectoryBatchBarEmbeddedV2(
    targets: List<PlaylistAddTarget>,
    summaryText: String,
    onAddToFavorites: (List<MediaItem>) -> Unit,
    onOpenBatchPlaylistPicker: (List<MediaItem>) -> Unit,
    onAddMediaItemsToQueue: (List<MediaItem>) -> Unit
) {
    val mediaItems = remember(targets) { targets.map { it.toMediaItem() } }
    val hasMediaItems = mediaItems.isNotEmpty()
    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = summaryText,
            style = MaterialTheme.typography.bodySmall,
            color = AsmrTheme.colorScheme.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(
                onClick = { onAddToFavorites(mediaItems) },
                enabled = hasMediaItems,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Icon(Icons.Default.FavoriteBorder, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("收藏", style = MaterialTheme.typography.labelMedium)
            }
            OutlinedButton(
                onClick = { onOpenBatchPlaylistPicker(mediaItems) },
                enabled = hasMediaItems,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("列表", style = MaterialTheme.typography.labelMedium)
            }
            OutlinedButton(
                onClick = { onAddMediaItemsToQueue(mediaItems) },
                enabled = hasMediaItems,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("队列", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
internal fun CompactDirectoryBreadcrumbContentV2(
    currentPath: String,
    breadcrumbs: List<DirectoryBreadcrumbSegment>,
    onNavigate: (String) -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    val displayedCrumbs = remember(breadcrumbs) {
        when {
            breadcrumbs.size <= 2 -> breadcrumbs
            else -> listOf(
                DirectoryBreadcrumbSegment(label = "...", path = ""),
                breadcrumbs[breadcrumbs.lastIndex - 1],
                breadcrumbs.last()
            )
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        CompactBreadcrumbNode(
            text = "根目录",
            selected = currentPath.isBlank(),
            icon = Icons.Default.Home,
            onClick = { onNavigate("") }
        )
        displayedCrumbs.forEach { crumb ->
            Text(
                text = "/",
                style = MaterialTheme.typography.labelLarge,
                color = colorScheme.textTertiary
            )
            if (crumb.label == "..." && crumb.path.isBlank()) {
                Text(
                    text = "...",
                    style = MaterialTheme.typography.labelLarge,
                    color = colorScheme.textTertiary,
                    maxLines = 1
                )
            } else {
                CompactBreadcrumbNode(
                    text = crumb.label,
                    selected = crumb.path == currentPath,
                    onClick = { onNavigate(crumb.path) }
                )
            }
        }
    }
}

@Composable
internal fun DirectoryFolderRowV2(
    title: String,
    onClick: () -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colorScheme.primary.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = null,
            tint = colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge,
            color = colorScheme.textPrimary
        )
        Icon(
            imageVector = Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = colorScheme.textSecondary,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
internal fun DirectoryActionGroupButton(
    text: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(34.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
internal fun DirectoryBatchBarEmbeddedV3(
    targets: List<PlaylistAddTarget>,
    summaryText: String,
    hintText: String,
    showActions: Boolean,
    onAddToFavorites: (List<MediaItem>) -> Unit,
    onOpenBatchPlaylistPicker: (List<MediaItem>) -> Unit,
    onAddMediaItemsToQueue: (List<MediaItem>) -> Unit
) {
    val mediaItems = remember(targets) { targets.map { it.toMediaItem() } }
    val hasMediaItems = mediaItems.isNotEmpty()
    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = summaryText,
                style = MaterialTheme.typography.titleSmall,
                color = AsmrTheme.colorScheme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = hintText,
                style = MaterialTheme.typography.labelSmall,
                color = AsmrTheme.colorScheme.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (showActions) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DirectoryActionGroupButton(
                    text = "收藏",
                    icon = Icons.Default.FavoriteBorder,
                    enabled = hasMediaItems,
                    onClick = { onAddToFavorites(mediaItems) }
                )
                VerticalDivider(
                    color = dividerColor,
                    modifier = Modifier.height(18.dp),
                    thickness = 0.5.dp
                )
                DirectoryActionGroupButton(
                    text = "列表",
                    icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                    enabled = hasMediaItems,
                    onClick = { onOpenBatchPlaylistPicker(mediaItems) }
                )
                VerticalDivider(
                    color = dividerColor,
                    modifier = Modifier.height(18.dp),
                    thickness = 0.5.dp
                )
                DirectoryActionGroupButton(
                    text = "队列",
                    icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                    enabled = hasMediaItems,
                    onClick = { onAddMediaItemsToQueue(mediaItems) }
                )
            }
        }
    }
}

@Composable
internal fun DirectoryBrowserPanelV2(
    panelKey: String,
    currentPath: String,
    breadcrumbs: List<DirectoryBreadcrumbSegment>,
    batchTargets: List<PlaylistAddTarget>,
    folders: List<DirectoryFolderItem>,
    files: List<DirectoryFileItem>,
    onNavigate: (String) -> Unit,
    onAddToFavorites: (List<MediaItem>) -> Unit,
    onOpenBatchPlaylistPicker: (List<MediaItem>) -> Unit,
    onAddMediaItemsToQueue: (List<MediaItem>) -> Unit,
    folderKeyPrefix: String,
    fileKeyPrefix: String,
    emptyText: String = "当前目录暂无文件",
    fileContent: @Composable (
        file: DirectoryFileItem,
        selectionMode: Boolean,
        selected: Boolean,
        enterSelectionMode: () -> Unit,
        onSelectedChange: (Boolean) -> Unit
    ) -> Unit
) {
    val browserListState = rememberSaveable("dir-panel-v2:$panelKey", saver = LazyListState.Saver) {
        LazyListState()
    }
    var selectionMode by remember(panelKey, currentPath) { mutableStateOf(false) }
    val selectedPaths = remember(panelKey, currentPath) { mutableStateListOf<String>() }
    val selectedFiles = remember(files, selectedPaths.toList()) {
        val selectedSet = selectedPaths.toSet()
        files.filter { selectedSet.contains(it.path) }
    }
    val activeTargets = remember(selectionMode, batchTargets, selectedFiles) {
        if (selectionMode) selectedFiles.mapNotNull { it.playlistTarget } else batchTargets
    }
    val batchSummaryText = remember(selectionMode, selectedPaths.size, batchTargets.size) {
        if (selectionMode) "已选 ${selectedPaths.size} 项" else "媒体 ${batchTargets.size} 项"
    }
    val batchHintText = remember(selectionMode) {
        if (selectionMode) "点击文件可增减选择" else "长按可批量操作"
    }
    val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
    val maxHeight = remember(screenHeight) {
        (screenHeight * 0.48f).coerceIn(240.dp, 460.dp)
    }

    LaunchedEffect(panelKey, currentPath) {
        browserListState.scrollToItem(0)
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 1.dp,
        color = AsmrTheme.colorScheme.surface.copy(alpha = 0.44f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            CompactDirectoryBreadcrumbContentV2(
                currentPath = currentPath,
                breadcrumbs = breadcrumbs,
                onNavigate = onNavigate
            )
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
            )
            DirectoryBatchBarEmbeddedV3(
                targets = activeTargets,
                summaryText = batchSummaryText,
                hintText = batchHintText,
                showActions = selectionMode,
                onAddToFavorites = onAddToFavorites,
                onOpenBatchPlaylistPicker = onOpenBatchPlaylistPicker,
                onAddMediaItemsToQueue = onAddMediaItemsToQueue
            )
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
            )
            LazyColumn(
                state = browserListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .thinScrollbar(browserListState),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                if (folders.isEmpty() && files.isEmpty()) {
                    item(key = "empty") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = emptyText,
                                color = AsmrTheme.colorScheme.textSecondary
                            )
                        }
                    }
                } else {
                    items(
                        items = folders,
                        key = { folder -> "$folderKeyPrefix:${folder.path}" },
                        contentType = { "folder" }
                    ) { folder ->
                        DirectoryFolderRowV2(
                            title = folder.title,
                            onClick = { onNavigate(folder.path) }
                        )
                    }
                    items(
                        items = files,
                        key = { file -> "$fileKeyPrefix:${file.path}" },
                        contentType = { "file" }
                    ) { file ->
                        val isSelected = selectedPaths.contains(file.path)
                        fileContent(
                            file,
                            selectionMode,
                            isSelected,
                            {
                                selectionMode = true
                                if (!selectedPaths.contains(file.path)) {
                                    selectedPaths.add(file.path)
                                }
                            },
                            { checked ->
                                if (checked) {
                                    if (!selectedPaths.contains(file.path)) {
                                        selectedPaths.add(file.path)
                                    }
                                    selectionMode = true
                                } else {
                                    selectedPaths.remove(file.path)
                                    if (selectedPaths.isEmpty()) {
                                        selectionMode = false
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun DirectoryFolderRow(
    title: String,
    onClick: () -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    Surface(
        color = colorScheme.surface.copy(alpha = 0.54f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                color = colorScheme.textPrimary
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = colorScheme.textSecondary
            )
        }
    }
}

@Composable
internal fun CompactDirectoryBreadcrumbContentV3(
    currentPath: String,
    breadcrumbs: List<DirectoryBreadcrumbSegment>,
    onNavigate: (String) -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    val displayedCrumbs = remember(breadcrumbs) {
        when {
            breadcrumbs.size <= 2 -> breadcrumbs
            else -> listOf(
                DirectoryBreadcrumbSegment(label = "...", path = ""),
                breadcrumbs[breadcrumbs.lastIndex - 1],
                breadcrumbs.last()
            )
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        CompactBreadcrumbNode(
            text = "根目录",
            selected = currentPath.isBlank(),
            icon = Icons.Default.Home,
            onClick = { onNavigate("") }
        )
        displayedCrumbs.forEach { crumb ->
            Text(
                text = "/",
                style = MaterialTheme.typography.labelLarge,
                color = colorScheme.textTertiary
            )
            if (crumb.label == "..." && crumb.path.isBlank()) {
                Text(
                    text = "...",
                    style = MaterialTheme.typography.labelLarge,
                    color = colorScheme.textTertiary,
                    maxLines = 1
                )
            } else {
                CompactBreadcrumbNode(
                    text = crumb.label,
                    selected = crumb.path == currentPath,
                    onClick = { onNavigate(crumb.path) }
                )
            }
        }
    }
}

@Composable
internal fun DirectoryFolderRowV3(
    title: String,
    onClick: () -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colorScheme.primary.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = null,
            tint = colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge,
            color = colorScheme.textPrimary
        )
        Icon(
            imageVector = Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = colorScheme.textSecondary,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
internal fun DirectoryBatchBarEmbeddedV4(
    targets: List<PlaylistAddTarget>,
    summaryText: String,
    hintText: String,
    showActions: Boolean,
    onAddToFavorites: (List<MediaItem>) -> Unit,
    onOpenBatchPlaylistPicker: (List<MediaItem>) -> Unit,
    onAddMediaItemsToQueue: (List<MediaItem>) -> Unit
) {
    val mediaItems = remember(targets) { targets.map { it.toMediaItem() } }
    val hasMediaItems = mediaItems.isNotEmpty()
    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = summaryText,
                style = MaterialTheme.typography.titleSmall,
                color = AsmrTheme.colorScheme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = hintText,
                style = MaterialTheme.typography.labelSmall,
                color = AsmrTheme.colorScheme.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (showActions) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DirectoryActionGroupButton(
                    text = "收藏",
                    icon = Icons.Default.FavoriteBorder,
                    enabled = hasMediaItems,
                    onClick = { onAddToFavorites(mediaItems) }
                )
                Text("|", color = AsmrTheme.colorScheme.textTertiary, style = MaterialTheme.typography.labelMedium)
                DirectoryActionGroupButton(
                    text = "列表",
                    icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                    enabled = hasMediaItems,
                    onClick = { onOpenBatchPlaylistPicker(mediaItems) }
                )
                Text("|", color = AsmrTheme.colorScheme.textTertiary, style = MaterialTheme.typography.labelMedium)
                DirectoryActionGroupButton(
                    text = "队列",
                    icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                    enabled = hasMediaItems,
                    onClick = { onAddMediaItemsToQueue(mediaItems) }
                )
            }
        }
    }
}

@Composable
internal fun DirectoryBatchBarEmbeddedV5(
    targets: List<PlaylistAddTarget>,
    summaryText: String,
    hintText: String,
    showActions: Boolean,
    modifier: Modifier = Modifier,
    onAddToFavorites: (List<MediaItem>) -> Unit,
    onOpenBatchPlaylistPicker: (List<MediaItem>) -> Unit,
    onAddMediaItemsToQueue: (List<MediaItem>) -> Unit
) {
    val mediaItems = remember(targets) { targets.map { it.toMediaItem() } }
    val hasMediaItems = mediaItems.isNotEmpty()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = summaryText,
                style = MaterialTheme.typography.titleSmall,
                color = AsmrTheme.colorScheme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = hintText,
                style = MaterialTheme.typography.labelSmall,
                color = AsmrTheme.colorScheme.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (showActions) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DirectoryActionGroupButton(
                    text = "收藏",
                    icon = Icons.Default.FavoriteBorder,
                    enabled = hasMediaItems,
                    onClick = { onAddToFavorites(mediaItems) }
                )
                Text("|", color = AsmrTheme.colorScheme.textTertiary, style = MaterialTheme.typography.labelMedium)
                DirectoryActionGroupButton(
                    text = "列表",
                    icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                    enabled = hasMediaItems,
                    onClick = {
                        if (hasMediaItems) {
                            onOpenBatchPlaylistPicker(mediaItems.toList())
                        }
                    }
                )
                Text("|", color = AsmrTheme.colorScheme.textTertiary, style = MaterialTheme.typography.labelMedium)
                DirectoryActionGroupButton(
                    text = "队列",
                    icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                    enabled = hasMediaItems,
                    onClick = { onAddMediaItemsToQueue(mediaItems) }
                )
            }
        }
    }
}

@Composable
internal fun DirectoryBrowserPanelV4(
    panelKey: String,
    currentPath: String,
    breadcrumbs: List<DirectoryBreadcrumbSegment>,
    batchTargets: List<PlaylistAddTarget>,
    folders: List<DirectoryFolderItem>,
    files: List<DirectoryFileItem>,
    onNavigate: (String) -> Unit,
    onAddToFavorites: (List<MediaItem>) -> Unit,
    onOpenBatchPlaylistPicker: (List<MediaItem>) -> Unit,
    onAddMediaItemsToQueue: (List<MediaItem>) -> Unit,
    preferredPath: String = "",
    onTogglePreferredPath: ((Boolean) -> Unit)? = null,
    folderKeyPrefix: String,
    fileKeyPrefix: String,
    emptyText: String = "当前目录暂无文件",
    fileContent: @Composable (
        file: DirectoryFileItem,
        selectionMode: Boolean,
        selected: Boolean,
        enterSelectionMode: () -> Unit,
        onSelectedChange: (Boolean) -> Unit
    ) -> Unit
) {
    val browserListState = rememberSaveable("dir-panel-v4:$panelKey", saver = LazyListState.Saver) {
        LazyListState()
    }
    var selectionMode by remember(panelKey, currentPath) { mutableStateOf(false) }
    var preferredPathState by rememberSaveable(panelKey) { mutableStateOf(preferredPath.trim().trim('/')) }
    val selectedPaths = remember(panelKey, currentPath) { mutableStateListOf<String>() }
    val selectedFiles = remember(files, selectedPaths.toList()) {
        val selectedSet = selectedPaths.toSet()
        files.filter { selectedSet.contains(it.path) }
    }
    val activeTargets = remember(selectionMode, batchTargets, selectedFiles) {
        if (selectionMode) selectedFiles.mapNotNull { it.playlistTarget } else batchTargets
    }
    val batchSummaryText = remember(selectionMode, selectedPaths.size, batchTargets.size) {
        if (selectionMode) "已选 ${selectedPaths.size} 项" else "媒体 ${batchTargets.size} 项"
    }
    val batchHintText = remember(selectionMode) {
        if (selectionMode) "点击文件可增减选择" else "长按可批量操作"
    }
    LaunchedEffect(preferredPath) {
        preferredPathState = preferredPath.trim().trim('/')
    }
    val normalizedCurrentPath = remember(currentPath) { currentPath.trim().trim('/') }
    val isPreferredPath = remember(preferredPathState, normalizedCurrentPath) {
        preferredPathState == normalizedCurrentPath
    }
    val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
    val fixedHeight = remember(screenHeight) {
        (screenHeight * 0.48f).coerceIn(240.dp, 460.dp)
    }

    LaunchedEffect(panelKey, currentPath) {
        browserListState.scrollToItem(0)
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 1.dp,
        color = AsmrTheme.colorScheme.surface.copy(alpha = 0.44f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            CompactDirectoryBreadcrumbContentV3(
                currentPath = currentPath,
                breadcrumbs = breadcrumbs,
                onNavigate = onNavigate
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 12.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
            )
            Row(
                modifier = dlsiteElasticItemModifier(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DirectoryBatchBarEmbeddedV5(
                    targets = activeTargets,
                    summaryText = batchSummaryText,
                    hintText = batchHintText,
                    showActions = selectionMode,
                    modifier = Modifier.weight(1f),
                    onAddToFavorites = onAddToFavorites,
                    onOpenBatchPlaylistPicker = onOpenBatchPlaylistPicker,
                    onAddMediaItemsToQueue = onAddMediaItemsToQueue
                )
                if (onTogglePreferredPath != null && !selectionMode) {
                    val preferredIcon = if (isPreferredPath) Icons.Default.Bookmark else Icons.Default.BookmarkBorder
                    val preferredTextColor = if (isPreferredPath) AsmrTheme.colorScheme.primary else AsmrTheme.colorScheme.textSecondary
                    val preferredContainerColor = AsmrTheme.colorScheme.primary.copy(alpha = if (AsmrTheme.colorScheme.isDark) 0.24f else 0.14f)
                    val preferredButton: @Composable (@Composable () -> Unit) -> Unit = { content ->
                        if (isPreferredPath) {
                            FilledTonalButton(
                                onClick = {
                                    preferredPathState = ""
                                    onTogglePreferredPath(false)
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = preferredContainerColor,
                                    contentColor = preferredTextColor
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.height(34.dp)
                            ) { content() }
                        } else {
                            TextButton(
                                onClick = {
                                    preferredPathState = normalizedCurrentPath
                                    onTogglePreferredPath(true)
                                },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = preferredTextColor
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.height(34.dp)
                            ) { content() }
                        }
                    }
                    preferredButton {
                        Icon(
                            imageVector = preferredIcon,
                            contentDescription = null,
                            tint = preferredTextColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "默认打开",
                            style = MaterialTheme.typography.labelMedium,
                            color = preferredTextColor
                        )
                    }
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 12.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
            )
            LazyColumn(
                state = browserListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(fixedHeight)
                    .thinScrollbar(browserListState),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                if (folders.isEmpty() && files.isEmpty()) {
                    item(key = "empty") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(fixedHeight - 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = emptyText,
                                color = AsmrTheme.colorScheme.textSecondary
                            )
                        }
                    }
                } else {
                    items(
                        items = folders,
                        key = { folder -> "$folderKeyPrefix:${folder.path}" },
                        contentType = { "folder" }
                    ) { folder ->
                        DirectoryFolderRowV3(
                            title = folder.title,
                            onClick = { onNavigate(folder.path) }
                        )
                    }
                    items(
                        items = files,
                        key = { file -> "$fileKeyPrefix:${file.path}" },
                        contentType = { "file" }
                    ) { file ->
                        val isSelected = selectedPaths.contains(file.path)
                        fileContent(
                            file,
                            selectionMode,
                            isSelected,
                            {
                                selectionMode = true
                                if (!selectedPaths.contains(file.path)) {
                                    selectedPaths.add(file.path)
                                }
                            },
                            { checked ->
                                if (checked) {
                                    if (!selectedPaths.contains(file.path)) {
                                        selectedPaths.add(file.path)
                                    }
                                    selectionMode = true
                                } else {
                                    selectedPaths.remove(file.path)
                                    if (selectedPaths.isEmpty()) {
                                        selectionMode = false
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun DirectoryBrowserPanelV3(
    panelKey: String,
    currentPath: String,
    breadcrumbs: List<DirectoryBreadcrumbSegment>,
    batchTargets: List<PlaylistAddTarget>,
    folders: List<DirectoryFolderItem>,
    files: List<DirectoryFileItem>,
    onNavigate: (String) -> Unit,
    onAddToFavorites: (List<MediaItem>) -> Unit,
    onOpenBatchPlaylistPicker: (List<MediaItem>) -> Unit,
    onAddMediaItemsToQueue: (List<MediaItem>) -> Unit,
    preferredPath: String = "",
    onTogglePreferredPath: ((Boolean) -> Unit)? = null,
    folderKeyPrefix: String,
    fileKeyPrefix: String,
    emptyText: String = "当前目录暂无文件",
    fileContent: @Composable (
        file: DirectoryFileItem,
        selectionMode: Boolean,
        selected: Boolean,
        enterSelectionMode: () -> Unit,
        onSelectedChange: (Boolean) -> Unit
    ) -> Unit
) {
    val browserListState = rememberSaveable("dir-panel-v3:$panelKey", saver = LazyListState.Saver) {
        LazyListState()
    }
    var selectionMode by remember(panelKey, currentPath) { mutableStateOf(false) }
    val selectedPaths = remember(panelKey, currentPath) { mutableStateListOf<String>() }
    val selectedFiles = remember(files, selectedPaths.toList()) {
        val selectedSet = selectedPaths.toSet()
        files.filter { selectedSet.contains(it.path) }
    }
    val activeTargets = remember(selectionMode, batchTargets, selectedFiles) {
        if (selectionMode) selectedFiles.mapNotNull { it.playlistTarget } else batchTargets
    }
    val batchSummaryText = remember(selectionMode, selectedPaths.size, batchTargets.size) {
        if (selectionMode) "已选 ${selectedPaths.size} 项" else "媒体 ${batchTargets.size} 项"
    }
    val batchHintText = remember(selectionMode) {
        if (selectionMode) "点击文件可增减选择" else "长按可批量操作"
    }
    val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
    val fixedHeight = remember(screenHeight) {
        (screenHeight * 0.48f).coerceIn(240.dp, 460.dp)
    }

    LaunchedEffect(panelKey, currentPath) {
        browserListState.scrollToItem(0)
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 1.dp,
        color = AsmrTheme.colorScheme.surface.copy(alpha = 0.44f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            CompactDirectoryBreadcrumbContentV3(
                currentPath = currentPath,
                breadcrumbs = breadcrumbs,
                onNavigate = onNavigate
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 12.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
            )
            DirectoryBatchBarEmbeddedV4(
                targets = activeTargets,
                summaryText = batchSummaryText,
                hintText = batchHintText,
                showActions = selectionMode,
                onAddToFavorites = onAddToFavorites,
                onOpenBatchPlaylistPicker = onOpenBatchPlaylistPicker,
                onAddMediaItemsToQueue = onAddMediaItemsToQueue
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 12.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
            )
            LazyColumn(
                state = browserListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(fixedHeight)
                    .thinScrollbar(browserListState),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                if (folders.isEmpty() && files.isEmpty()) {
                    item(key = "empty") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(fixedHeight - 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = emptyText,
                                color = AsmrTheme.colorScheme.textSecondary
                            )
                        }
                    }
                } else {
                    items(
                        items = folders,
                        key = { folder -> "$folderKeyPrefix:${folder.path}" },
                        contentType = { "folder" }
                    ) { folder ->
                        DirectoryFolderRowV3(
                            title = folder.title,
                            onClick = { onNavigate(folder.path) }
                        )
                    }
                    items(
                        items = files,
                        key = { file -> "$fileKeyPrefix:${file.path}" },
                        contentType = { "file" }
                    ) { file ->
                        val isSelected = selectedPaths.contains(file.path)
                        fileContent(
                            file,
                            selectionMode,
                            isSelected,
                            {
                                selectionMode = true
                                if (!selectedPaths.contains(file.path)) {
                                    selectedPaths.add(file.path)
                                }
                            },
                            { checked ->
                                if (checked) {
                                    if (!selectedPaths.contains(file.path)) {
                                        selectedPaths.add(file.path)
                                    }
                                    selectionMode = true
                                } else {
                                    selectedPaths.remove(file.path)
                                    if (selectedPaths.isEmpty()) {
                                        selectionMode = false
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DirectoryFileRow(
    file: DirectoryFileItem,
    loadRemoteFileSize: suspend (String) -> Long?,
    onPrimary: () -> Unit,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onEnterSelectionMode: (() -> Unit)? = null,
    onSelectedChange: ((Boolean) -> Unit)? = null,
    onSetAsCover: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    onManageTags: (() -> Unit)? = null,
    onRemoveFromAlbum: (() -> Unit)? = null
) {
    val colorScheme = AsmrTheme.colorScheme
    val materialColorScheme = MaterialTheme.colorScheme
    val dynamicContainerColor = dynamicPageContainerColor(colorScheme)
    val context = LocalContext.current
    val icon = when (file.fileType) {
        TreeFileType.Audio -> Icons.Default.Audiotrack
        TreeFileType.Video -> Icons.Default.Movie
        TreeFileType.Image -> Icons.Default.Image
        TreeFileType.Subtitle -> Icons.Default.Subtitles
        TreeFileType.Text -> Icons.Default.Description
        TreeFileType.Pdf -> Icons.Default.PictureAsPdf
        TreeFileType.Other -> Icons.Default.InsertDriveFile
    }
    val iconTint = when (file.fileType) {
        TreeFileType.Audio -> colorScheme.primary
        TreeFileType.Video -> colorScheme.accent
        TreeFileType.Image -> colorScheme.textSecondary
        TreeFileType.Subtitle -> colorScheme.textSecondary
        TreeFileType.Text -> colorScheme.textTertiary
        TreeFileType.Pdf -> colorScheme.danger
        TreeFileType.Other -> colorScheme.textTertiary
    }
    val sizeText by produceState<String?>(initialValue = null, file.sizeSource) {
        value = when (val sizeSource = file.sizeSource) {
            FileSizeSource.None -> null
            is FileSizeSource.Local -> withContext(Dispatchers.IO) {
                queryLocalFileSize(context, sizeSource.path)
            }?.let(Formatting::formatFileSize)
            is FileSizeSource.Remote -> loadRemoteFileSize(sizeSource.url)?.let(Formatting::formatFileSize)
        }
    }
    val metaLine = remember(file.fileType, file.durationSeconds, sizeText) {
        listOf(
            fileTypeLabel(file.fileType),
            Formatting.formatTrackSeconds(file.durationSeconds).takeIf { it.isNotBlank() },
            sizeText
        ).filterNotNull().joinToString(" · ")
    }

    val showPrimaryAction = file.isPlayable
    val showMenu = showPrimaryAction || onDownload != null || onAddToQueue != null || onAddToPlaylist != null || onManageTags != null || onRemoveFromAlbum != null
    val showTrailing = selectionMode || onSetAsCover != null || file.showSubtitleStamp || showMenu

    Surface(
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (selectionMode && onSelectedChange != null) {
                        onSelectedChange(!selected)
                    } else {
                        onPrimary()
                    }
                },
                onLongClick = {
                    if (!selectionMode && onEnterSelectionMode != null && onSelectedChange != null) {
                        onEnterSelectionMode()
                        onSelectedChange(true)
                    }
                }
            )
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = file.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = colorScheme.textPrimary,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                )
            },
            supportingContent = {
                if (metaLine.isNotBlank()) {
                    Text(
                        text = metaLine,
                        color = colorScheme.textSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            leadingContent = {
                if (file.fileType == TreeFileType.Image && file.thumbnailModel != null) {
                    AsmrAsyncImage(
                        model = file.thumbnailModel,
                        contentDescription = null,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                        placeholderCornerRadius = 8
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }
            },
            trailingContent = if (showTrailing) {
                {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (file.showSubtitleStamp) {
                            SubtitleStamp(modifier = Modifier.padding(end = 8.dp))
                        }
                        if (selectionMode && onSelectedChange != null) {
                            Checkbox(
                                checked = selected,
                                onCheckedChange = { checked -> onSelectedChange(checked) }
                            )
                        } else if (onSetAsCover != null) {
                            IconButton(
                                onClick = onSetAsCover,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Photo,
                                    contentDescription = "设为封面",
                                    tint = colorScheme.textSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        if (!selectionMode && showMenu) {
                            var showMenuExpanded by rememberSaveable(file.path) { mutableStateOf(false) }
                            Box {
                                IconButton(
                                    onClick = { showMenuExpanded = true },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "更多操作",
                                        tint = colorScheme.textSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                MaterialTheme(
                                    colorScheme = materialColorScheme.copy(
                                        surface = dynamicContainerColor,
                                        surfaceContainer = dynamicContainerColor
                                    )
                                ) {
                                    DropdownMenu(
                                        expanded = showMenuExpanded,
                                        onDismissRequest = { showMenuExpanded = false },
                                        modifier = Modifier.background(dynamicContainerColor)
                                    ) {
                                        if (showPrimaryAction) {
                                            DropdownMenuItem(
                                                text = { Text("播放") },
                                                onClick = {
                                                    onPrimary()
                                                    showMenuExpanded = false
                                                },
                                                leadingIcon = {
                                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = colorScheme.primary)
                                                }
                                            )
                                        }
                                        if (onDownload != null) {
                                            DropdownMenuItem(
                                                text = { Text("下载") },
                                                onClick = {
                                                    onDownload()
                                                    showMenuExpanded = false
                                                },
                                                leadingIcon = {
                                                    Icon(Icons.Default.Download, contentDescription = null, tint = colorScheme.textSecondary)
                                                }
                                            )
                                        }
                                        if (onAddToQueue != null) {
                                            DropdownMenuItem(
                                                text = { Text("添加到播放队列") },
                                                onClick = {
                                                    onAddToQueue()
                                                    showMenuExpanded = false
                                                },
                                                leadingIcon = {
                                                    Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null, tint = colorScheme.textSecondary)
                                                }
                                            )
                                        }
                                        if (onAddToPlaylist != null) {
                                            DropdownMenuItem(
                                                text = { Text("添加到我的列表") },
                                                onClick = {
                                                    onAddToPlaylist()
                                                    showMenuExpanded = false
                                                },
                                                leadingIcon = {
                                                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, tint = colorScheme.textSecondary)
                                                }
                                            )
                                        }
                                        if (onManageTags != null) {
                                            DropdownMenuItem(
                                                text = { Text("标签管理") },
                                                onClick = {
                                                    onManageTags()
                                                    showMenuExpanded = false
                                                },
                                                leadingIcon = {
                                                    Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null, tint = colorScheme.textSecondary)
                                                }
                                            )
                                        }
                                        if (onRemoveFromAlbum != null) {
                                            DropdownMenuItem(
                                                text = { Text("从专辑移除") },
                                                onClick = {
                                                    onRemoveFromAlbum()
                                                    showMenuExpanded = false
                                                },
                                                leadingIcon = {
                                                    Icon(Icons.Default.Delete, contentDescription = null, tint = colorScheme.textSecondary)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else null,
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

@Composable
internal fun TreeFolderRow(
    title: String,
    depth: Int,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp)
    ) {
        ListItem(
            headlineContent = { 
                Text(
                    title, 
                    maxLines = 1, 
                    overflow = TextOverflow.Ellipsis,
                    color = colorScheme.textPrimary,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                ) 
            },
            leadingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (depth > 0) Spacer(modifier = Modifier.width((depth * 12).dp))
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            trailingContent = {
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colorScheme.textTertiary,
                    modifier = Modifier.size(20.dp)
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

@Composable
internal fun TreeFileRow(
    title: String,
    depth: Int,
    fileType: TreeFileType,
    isPlayable: Boolean,
    showSubtitleStamp: Boolean = false,
    thumbnailModel: Any? = null,
    onPrimary: () -> Unit,
    onSetAsCover: (() -> Unit)? = null,
    onDownload: (() -> Unit)?,
    onAddToQueue: (() -> Unit)?,
    onAddToPlaylist: (() -> Unit)?,
    onManageTags: (() -> Unit)? = null,
    onRemoveFromAlbum: (() -> Unit)? = null
) {
    val colorScheme = AsmrTheme.colorScheme
    val materialColorScheme = MaterialTheme.colorScheme
    val dynamicContainerColor = dynamicPageContainerColor(colorScheme)
    val icon = when (fileType) {
        TreeFileType.Audio -> Icons.Default.Audiotrack
        TreeFileType.Video -> Icons.Default.Movie
        TreeFileType.Image -> Icons.Default.Image
        TreeFileType.Subtitle -> Icons.Default.Subtitles
        TreeFileType.Text -> Icons.Default.Description
        TreeFileType.Pdf -> Icons.Default.PictureAsPdf
        TreeFileType.Other -> Icons.Default.InsertDriveFile
    }
    val iconTint = when (fileType) {
        TreeFileType.Audio -> colorScheme.primary
        TreeFileType.Video -> colorScheme.accent
        TreeFileType.Image -> colorScheme.textSecondary
        TreeFileType.Subtitle -> colorScheme.textSecondary
        TreeFileType.Text -> colorScheme.textTertiary
        TreeFileType.Pdf -> colorScheme.danger
        TreeFileType.Other -> colorScheme.textTertiary
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPrimary)
            .padding(vertical = 2.dp)
    ) {
        val showPrimaryAction = isPlayable
        val showMenu = showPrimaryAction || onDownload != null || onAddToQueue != null || onAddToPlaylist != null || onManageTags != null || onRemoveFromAlbum != null
        val showTrailing = onSetAsCover != null || showMenu
        ListItem(
            headlineContent = { 
                Text(
                    title, 
                    maxLines = 1, 
                    overflow = TextOverflow.Ellipsis,
                    color = colorScheme.textSecondary,
                    style = MaterialTheme.typography.bodyMedium
                ) 
            },
            leadingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (depth > 0) Spacer(modifier = Modifier.width((depth * 12).dp))
                    if (fileType == TreeFileType.Image && thumbnailModel != null) {
                        AsmrAsyncImage(
                            model = thumbnailModel,
                            contentDescription = null,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Crop,
                            placeholderCornerRadius = 6,
                        )
                    } else {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            },
            trailingContent = if (showTrailing) {
                {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (showSubtitleStamp) {
                            SubtitleStamp(modifier = Modifier.padding(end = 8.dp))
                        }
                        if (onSetAsCover != null) {
                            IconButton(
                                onClick = onSetAsCover,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Photo,
                                    contentDescription = "设为封面",
                                    tint = colorScheme.textSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        if (showMenu) {
                            var showMenuExpanded by rememberSaveable { mutableStateOf(false) }
                            Box {
                                IconButton(
                                    onClick = { showMenuExpanded = true },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "更多操作",
                                        tint = colorScheme.textSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                MaterialTheme(
                                    colorScheme = materialColorScheme.copy(
                                        surface = dynamicContainerColor,
                                        surfaceContainer = dynamicContainerColor
                                    )
                                ) {
                                    val dividerColor = materialColorScheme.outlineVariant.copy(alpha = 0.3f)
                                    var hasVisibleItem = false
                                    @Composable
                                    fun addDividerIfNeeded() {
                                        if (hasVisibleItem) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(horizontal = 8.dp),
                                                thickness = 0.5.dp,
                                                color = dividerColor
                                            )
                                        }
                                        hasVisibleItem = true
                                    }
                                    DropdownMenu(
                                        expanded = showMenuExpanded,
                                        onDismissRequest = { showMenuExpanded = false },
                                        modifier = Modifier.background(dynamicContainerColor)
                                    ) {
                                        if (showPrimaryAction) {
                                            addDividerIfNeeded()
                                            DropdownMenuItem(
                                                text = { Text("播放") },
                                                onClick = {
                                                    onPrimary()
                                                    showMenuExpanded = false
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.Default.PlayArrow,
                                                        contentDescription = null,
                                                        tint = colorScheme.primary
                                                    )
                                                }
                                            )
                                        }
                                        if (onDownload != null) {
                                            addDividerIfNeeded()
                                            DropdownMenuItem(
                                                text = { Text("下载") },
                                                onClick = {
                                                    onDownload.invoke()
                                                    showMenuExpanded = false
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.Default.Download,
                                                        contentDescription = null,
                                                        tint = colorScheme.textSecondary
                                                    )
                                                }
                                            )
                                        }
                                        if (onAddToQueue != null) {
                                            addDividerIfNeeded()
                                            DropdownMenuItem(
                                                text = { Text("添加到播放队列") },
                                                onClick = {
                                                    onAddToQueue.invoke()
                                                    showMenuExpanded = false
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.AutoMirrored.Filled.PlaylistPlay,
                                                        contentDescription = null,
                                                        tint = colorScheme.textSecondary
                                                    )
                                                }
                                            )
                                        }
                                        if (onAddToPlaylist != null) {
                                            addDividerIfNeeded()
                                            DropdownMenuItem(
                                                text = { Text("添加到我的列表") },
                                                onClick = {
                                                    onAddToPlaylist.invoke()
                                                    showMenuExpanded = false
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.AutoMirrored.Filled.PlaylistAdd,
                                                        contentDescription = null,
                                                        tint = colorScheme.textSecondary
                                                    )
                                                }
                                            )
                                        }
                                        if (onManageTags != null) {
                                            addDividerIfNeeded()
                                            DropdownMenuItem(
                                                text = { Text("标签管理") },
                                                onClick = {
                                                    onManageTags.invoke()
                                                    showMenuExpanded = false
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.AutoMirrored.Filled.Label,
                                                        contentDescription = null,
                                                        tint = colorScheme.textSecondary
                                                    )
                                                }
                                            )
                                        }
                                        if (onRemoveFromAlbum != null) {
                                            addDividerIfNeeded()
                                            DropdownMenuItem(
                                                text = { Text("从专辑移除") },
                                                onClick = {
                                                    onRemoveFromAlbum.invoke()
                                                    showMenuExpanded = false
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.Default.Delete,
                                                        contentDescription = null,
                                                        tint = colorScheme.textSecondary
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else null,
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

