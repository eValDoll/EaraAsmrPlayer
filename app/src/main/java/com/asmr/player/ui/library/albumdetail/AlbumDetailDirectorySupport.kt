package com.asmr.player.ui.library

import com.asmr.player.util.sanitizeFolderName

import com.asmr.player.util.isJapaneseAsmrResource

import com.asmr.player.util.ChapterMediaReference



import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import com.asmr.player.data.local.db.AppDatabaseProvider
import com.asmr.player.data.local.db.entities.LocalTreeCacheEntity
import com.asmr.player.data.local.db.entities.OnlineSavedResourceEntity
import com.asmr.player.data.remote.api.AsmrOneTrackNodeResponse
import com.asmr.player.domain.model.Album
import com.asmr.player.domain.model.Track
import com.asmr.player.subtitle.SubtitleGenerationPolicy
import com.asmr.player.util.SubtitleMatchSupport
import com.asmr.player.util.SmartSortKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import java.io.File

import com.asmr.player.ui.common.ImagePreviewItem
import com.asmr.player.ui.common.ImagePreviewRequest
import com.asmr.player.ui.theme.AsmrColorScheme
import com.asmr.player.util.RemoteSubtitleSource
import com.asmr.player.util.TrackKeyNormalizer
import com.asmr.player.util.isOnlineTrackPath
import java.util.ArrayDeque

private const val LOCAL_TREE_CACHE_PARSER_VERSION = 2L

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


internal enum class DirectoryFolderPosition {
    Single,
    First,
    Middle,
    Last,
}

internal fun directorySelectedItemPosition(
    selected: Boolean,
    previousSelected: Boolean,
    nextSelected: Boolean,
): DirectoryFolderPosition {
    if (!selected) return DirectoryFolderPosition.Single
    return when {
        !previousSelected && !nextSelected -> DirectoryFolderPosition.Single
        !previousSelected -> DirectoryFolderPosition.First
        !nextSelected -> DirectoryFolderPosition.Last
        else -> DirectoryFolderPosition.Middle
    }
}

internal enum class TreeFileType {
    Audio,
    Video,
    Image,
    Subtitle,
    Text,
    Pdf,
    Archive,
    Document,
    Spreadsheet,
    Presentation,
    Code,
    Ebook,
    Font,
    AppPackage,
    Other
}

internal fun treeFileTypeForName(fileName: String): TreeFileType {
    return treeFileTypeForExtension(fileExtensionFromName(fileName))
}

internal fun treeFileTypeForNode(title: String, url: String?, remoteType: String? = null): TreeFileType {
    val fromTitle = treeFileTypeForName(title)

    val urlName = url
        ?.substringBefore('#')
        ?.substringBefore('?')
        ?.substringAfterLast('/')
        ?.substringAfterLast('\\')
        .orEmpty()
    val fromUrl = if (urlName.isNotBlank()) treeFileTypeForName(urlName) else TreeFileType.Other
    val fromRemoteType = treeFileTypeForRemoteType(remoteType)

    return when {
        fromTitle != TreeFileType.Other -> fromTitle
        fromUrl != TreeFileType.Other -> fromUrl
        fromRemoteType != TreeFileType.Other -> fromRemoteType
        !url.isNullOrBlank() -> TreeFileType.Audio
        else -> TreeFileType.Other
    }
}

internal fun isDownloadableTreeFileType(fileType: TreeFileType): Boolean {
    return fileType != TreeFileType.Other && fileType != TreeFileType.Subtitle
}

internal fun isLibraryResourceSavableTreeFileType(fileType: TreeFileType): Boolean {
    return fileType != TreeFileType.Other && fileType != TreeFileType.Subtitle
}

internal fun isPlayableTreeFileType(fileType: TreeFileType): Boolean {
    return fileType == TreeFileType.Audio || fileType == TreeFileType.Video
}

private fun fileExtensionFromName(name: String): String {
    val fileName = name
        .trim()
        .substringAfterLast('/')
        .substringAfterLast('\\')
    val withoutQuery = fileName.substringBefore('?')
    val fragmentIndex = withoutQuery.indexOf('#')
    val clean = if (fragmentIndex > 0 && withoutQuery.substring(0, fragmentIndex).contains('.')) {
        withoutQuery.substring(0, fragmentIndex)
    } else {
        withoutQuery
    }
    val ext = clean.substringAfterLast('.', missingDelimiterValue = "")
        .lowercase()
        .trim()
    return ext.takeIf { it.length in 1..12 && it.none { ch -> ch == '/' || ch == '\\' } }.orEmpty()
}

private fun treeFileTypeForExtension(ext: String): TreeFileType {
    return when (ext.lowercase()) {
        "mp3", "wav", "flac", "m4a", "m4b", "ogg", "oga", "aac", "opus", "wma", "alac",
        "aiff", "aif", "ape", "amr", "mka", "mid", "midi" -> TreeFileType.Audio

        "mp4", "mkv", "webm", "mov", "m4v", "avi", "wmv", "flv", "mpeg", "mpg", "ts",
        "m2ts", "3gp", "rm", "rmvb", "m3u8" -> TreeFileType.Video

        "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "tif", "tiff",
        "avif", "svg", "ico" -> TreeFileType.Image

        "lrc", "srt", "vtt", "ass", "ssa", "smi", "sbv", "ttml", "dfxp", "sub", "idx" -> TreeFileType.Subtitle

        "txt", "md", "markdown", "nfo", "log", "cue", "ks", "readme" -> TreeFileType.Text

        "pdf" -> TreeFileType.Pdf

        "zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz", "lz", "lzma", "zst",
        "cab", "iso", "dmg" -> TreeFileType.Archive

        "doc", "docx", "odt", "rtf", "pages", "tex" -> TreeFileType.Document

        "xls", "xlsx", "ods", "csv", "tsv", "numbers" -> TreeFileType.Spreadsheet

        "ppt", "pptx", "odp", "key" -> TreeFileType.Presentation

        "json", "xml", "html", "htm", "css", "js", "jsx", "tsx", "kt", "kts",
        "java", "go", "rs", "py", "rb", "php", "c", "cc", "cpp", "h", "hpp", "cs",
        "swift", "sh", "bash", "zsh", "bat", "cmd", "ps1", "gradle", "properties",
        "yaml", "yml", "toml", "ini", "sql", "lua", "dart" -> TreeFileType.Code

        "epub", "mobi", "azw", "azw3", "fb2", "cbz", "cbr" -> TreeFileType.Ebook

        "ttf", "otf", "woff", "woff2", "eot" -> TreeFileType.Font

        "apk", "aab", "ipa", "exe", "msi", "appx", "deb", "rpm", "pkg" -> TreeFileType.AppPackage

        else -> TreeFileType.Other
    }
}

private fun treeFileTypeForRemoteType(remoteType: String?): TreeFileType {
    val normalized = remoteType
        ?.trim()
        ?.lowercase()
        ?.replace('-', '_')
        .orEmpty()
    if (normalized.isBlank()) return TreeFileType.Other
    return when {
        normalized == "audio" || normalized.startsWith("audio/") -> TreeFileType.Audio
        normalized == "video" || normalized.startsWith("video/") -> TreeFileType.Video
        normalized == "image" || normalized.startsWith("image/") -> TreeFileType.Image
        normalized == "subtitle" || normalized == "subtitles" || normalized == "caption" -> TreeFileType.Subtitle
        normalized == "text" || normalized.startsWith("text/") -> TreeFileType.Text
        normalized == "pdf" || normalized == "application/pdf" -> TreeFileType.Pdf
        normalized == "archive" || normalized == "compressed" -> TreeFileType.Archive
        normalized == "document" -> TreeFileType.Document
        normalized == "spreadsheet" -> TreeFileType.Spreadsheet
        normalized == "presentation" -> TreeFileType.Presentation
        normalized == "code" || normalized == "source" -> TreeFileType.Code
        normalized == "ebook" -> TreeFileType.Ebook
        normalized == "font" || normalized.startsWith("font/") -> TreeFileType.Font
        normalized == "package" || normalized == "app" -> TreeFileType.AppPackage
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
    data class Local(val path: String, val sizeBytes: Long? = null) : FileSizeSource()
    data class Remote(val url: String) : FileSizeSource()
}

internal data class DirectoryBreadcrumbSegment(
    val label: String,
    val path: String
)

internal data class DirectoryFolderItem(
    val path: String,
    val title: String,
    val descendantTrackIds: List<Long> = emptyList(),
    val hasLocalContent: Boolean = false,
)

internal data class DirectoryFileItem(
    val path: String,
    val title: String,
    val fileType: TreeFileType,
    val isPlayable: Boolean,
    val isOnline: Boolean = false,
    val durationSeconds: Double? = null,
    val sizeSource: FileSizeSource = FileSizeSource.None,
    val absolutePath: String = "",
    val url: String = "",
    val track: Track? = null,
    val thumbnailModel: Any? = null,
    val playlistTarget: PlaylistAddTarget? = null,
    val subtitleSources: List<RemoteSubtitleSource> = emptyList(),
    val showSubtitleStamp: Boolean = false,
    val dlsitePlayImageCrypt: Boolean = false,
    val dlsitePlayImageWidth: Int? = null,
    val dlsitePlayImageHeight: Int? = null,
    val dlsitePlayOptimizedName: String? = null
)

internal data class LocalTreeDeletionTarget(
    val title: String,
    val relativePath: String,
    val absolutePath: String? = null,
    val isDirectory: Boolean,
    val trackIds: List<Long> = emptyList(),
    val hasLocalContent: Boolean,
)

internal fun isOnlineDirectoryAudio(fileType: TreeFileType, absolutePath: String, track: Track?): Boolean {
    if (fileType != TreeFileType.Audio) return false
    val path = track?.path?.trim().orEmpty().ifBlank { absolutePath.trim() }
    return isOnlineTrackPath(path)
}

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

internal fun buildDirectoryImagePreviewRequest(
    files: List<DirectoryFileItem>,
    clickedPath: String,
    toPreviewItem: (DirectoryFileItem) -> ImagePreviewItem?
): ImagePreviewRequest? {
    val imageFiles = files.filter { it.fileType == TreeFileType.Image }
    if (imageFiles.isEmpty()) return null
    val items = imageFiles.mapNotNull(toPreviewItem)
    if (items.isEmpty()) return null
    val initialIndex = items.indexOfFirst { it.key == clickedPath }
    if (initialIndex < 0) return null
    return ImagePreviewRequest(items = items, initialIndex = initialIndex)
}

internal fun buildGalleryImagePreviewRequest(
    galleryItems: List<ImagePreviewItem>,
    clickedKey: String
): ImagePreviewRequest? {
    if (galleryItems.isEmpty()) return null
    val initialIndex = galleryItems.indexOfFirst { it.key == clickedKey }
    if (initialIndex < 0) return null
    return ImagePreviewRequest(items = galleryItems, initialIndex = initialIndex)
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

internal fun normalizeLocalTreeRelativePath(path: String): String? {
    val segments = path
        .replace('\\', '/')
        .trim()
        .trim('/')
        .split('/')
        .filter { it.isNotBlank() }
    if (segments.isEmpty() || segments.any { it == "." || it == ".." }) return null
    return segments.joinToString("/")
}

internal fun localTreePathMatchesTarget(
    candidatePath: String,
    targetPath: String,
    targetIsDirectory: Boolean,
): Boolean {
    val candidate = normalizeLocalTreeRelativePath(candidatePath) ?: return false
    val target = normalizeLocalTreeRelativePath(targetPath) ?: return false
    return candidate == target || (targetIsDirectory && candidate.startsWith("$target/"))
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

internal data class AsmrTreeUiResult(
    val entries: List<AsmrTreeUiEntry>
)

internal class LocalTreeNode(
    val name: String,
    val path: String,
    val children: MutableMap<String, LocalTreeNode> = linkedMapOf(),
    var absolutePath: String? = null,
    var fileType: TreeFileType = TreeFileType.Other,
    var track: Track? = null,
    var sizeBytes: Long? = null
)

internal data class LocalTreeIndex(
    val root: LocalTreeNode
)

internal data class RemoteSelectionFileRef(
    val relativePath: String,
    val url: String
)

internal data class LocalSelectionFileRef(
    val relativePath: String,
    val absolutePath: String,
    val track: Track?
)

internal data class LocalIncrementalSelectionPaths(
    val downloadedPaths: Set<String> = emptySet(),
    val savedPaths: Set<String> = emptySet()
)

internal fun collectLocalSelectionFiles(index: LocalTreeIndex): List<LocalSelectionFileRef> {
    val files = mutableListOf<LocalSelectionFileRef>()

    fun collect(node: LocalTreeNode) {
        if (node.children.isEmpty()) {
            val absolutePath = node.absolutePath?.trim().orEmpty()
            if (node.path.isNotBlank() && absolutePath.isNotBlank()) {
                files += LocalSelectionFileRef(
                    relativePath = node.path,
                    absolutePath = absolutePath,
                    track = node.track
                )
            }
            return
        }
        node.children.values.forEach(::collect)
    }

    collect(index.root)
    return files
}

internal fun resolveExistingRemoteSelectionPaths(
    remoteFiles: List<RemoteSelectionFileRef>,
    localFiles: List<LocalSelectionFileRef>,
    includeOnlineFiles: Boolean
): Set<String> {
    data class MatchCandidate(
        val normalizedRelativePath: String,
        val canonicalUrl: String,
        val fileName: String,
        val trackKey: String,
        val trackKeyWithoutGroup: String,
        val extension: String,
        val fileType: TreeFileType,
        val japaneseAsmr: Boolean
    )

    data class RemoteCandidate(
        val file: RemoteSelectionFileRef,
        val normalizedRelativePath: String,
        val canonicalUrl: String,
        val fileName: String,
        val trackKey: String,
        val trackKeyWithoutGroup: String,
        val extension: String,
        val fileType: TreeFileType,
        val japaneseAsmr: Boolean
    )

    fun normalizeRelativePath(path: String): String {
        return path.replace('\\', '/').trim().trim('/').lowercase()
    }

    fun canonicalUrl(value: String): String {
        val trimmed = value.trim()
        if (!isOnlineTrackPath(trimmed)) return ""
        val chapter = ChapterMediaReference.parse(trimmed)
        if (chapter != null && chapter.downloadUrl.isNullOrBlank()) {
            return "${chapter.streamUrl.substringBefore('?')}#eara-chapter=${chapter.startMs},${chapter.endMs ?: ""}"
        }
        return (chapter?.downloadUrl ?: trimmed).substringBefore('#').substringBefore('?')
    }

    fun fileName(path: String): String {
        return normalizeRelativePath(path).substringAfterLast('/')
    }

    fun remoteTrackKey(path: String, includeGroup: Boolean): String {
        val normalized = path.replace('\\', '/').trim().trim('/')
        val title = normalized.substringAfterLast('/').substringBeforeLast('.')
        val group = if (includeGroup) normalized.substringBeforeLast('/', "") else ""
        return TrackKeyNormalizer.buildKey(title, group, null)
    }

    fun resolvedExtension(relativePath: String, sourcePath: String): String {
        return fileExtensionFromName(relativePath)
            .ifBlank { fileExtensionFromName(sourcePath) }
    }

    fun resolvedFileType(relativePath: String, sourcePath: String): TreeFileType {
        return treeFileTypeForName(relativePath).takeIf { it != TreeFileType.Other }
            ?: treeFileTypeForName(sourcePath)
    }

    val candidates = localFiles.asSequence()
        .filter { local ->
            includeOnlineFiles || !isOnlineTrackPath(local.track?.path.orEmpty().ifBlank { local.absolutePath })
        }
        .map { local ->
            val track = local.track
            val fallbackGroup = local.relativePath.replace('\\', '/').substringBeforeLast('/', "")
            val sourcePath = track?.path.orEmpty().ifBlank { local.absolutePath }
            MatchCandidate(
                normalizedRelativePath = normalizeRelativePath(local.relativePath),
                canonicalUrl = canonicalUrl(sourcePath),
                fileName = fileName(local.relativePath),
                trackKey = track?.let {
                    TrackKeyNormalizer.buildKey(it.title, it.group.ifBlank { fallbackGroup }, null)
                }.orEmpty(),
                trackKeyWithoutGroup = track?.let {
                    TrackKeyNormalizer.buildKey(it.title, "", null)
                }.orEmpty(),
                extension = resolvedExtension(local.relativePath, sourcePath),
                fileType = resolvedFileType(local.relativePath, sourcePath),
                japaneseAsmr = isJapaneseAsmrResource(sourcePath, track?.group.orEmpty()) ||
                    local.relativePath.split('/').contains("japaneseasmr.com")
            )
        }
        .toList()

    val remotes = remoteFiles.map { remote ->
        RemoteCandidate(
            file = remote,
            normalizedRelativePath = normalizeRelativePath(remote.relativePath),
            canonicalUrl = canonicalUrl(remote.url),
            fileName = fileName(remote.relativePath),
            trackKey = remoteTrackKey(remote.relativePath, includeGroup = true),
            trackKeyWithoutGroup = remoteTrackKey(remote.relativePath, includeGroup = false),
            extension = resolvedExtension(remote.relativePath, remote.url),
            fileType = treeFileTypeForNode(remote.relativePath, remote.url),
            japaneseAsmr = remote.relativePath.startsWith("japaneseasmr.com/")
        )
    }

    fun buildCandidateIndex(key: (MatchCandidate) -> String): Map<String, List<Int>> {
        val index = linkedMapOf<String, MutableList<Int>>()
        candidates.forEachIndexed { candidateIndex, candidate ->
            val value = key(candidate)
            if (value.isNotBlank()) {
                index.getOrPut(value) { mutableListOf() }.add(candidateIndex)
            }
        }
        return index
    }

    val relativePathIndex = buildCandidateIndex(MatchCandidate::normalizedRelativePath)
    val canonicalUrlIndex = buildCandidateIndex(MatchCandidate::canonicalUrl)
    val trackKeyIndex = buildCandidateIndex(MatchCandidate::trackKey)
    val fileNameIndex = buildCandidateIndex(MatchCandidate::fileName)
    val trackKeyWithoutGroupIndex = buildCandidateIndex(MatchCandidate::trackKeyWithoutGroup)
    val available = BooleanArray(candidates.size) { true }
    val unmatchedRemotes = BooleanArray(remotes.size) { true }
    val matched = linkedSetOf<String>()

    fun consume(
        remoteIndex: Int,
        index: Map<String, List<Int>>,
        key: String,
        isCompatible: (MatchCandidate) -> Boolean = { true }
    ) {
        if (!unmatchedRemotes[remoteIndex] || key.isBlank()) return
        val candidateIndex = index[key]
            ?.firstOrNull { available[it] && candidates[it].japaneseAsmr == remotes[remoteIndex].japaneseAsmr && isCompatible(candidates[it]) }
            ?: return
        available[candidateIndex] = false
        unmatchedRemotes[remoteIndex] = false
        matched += remotes[remoteIndex].file.relativePath
    }

    fun consumePass(
        index: Map<String, List<Int>>,
        key: (RemoteCandidate) -> String,
        isCompatible: (RemoteCandidate, MatchCandidate) -> Boolean = { _, _ -> true }
    ) {
        remotes.forEachIndexed { remoteIndex, remote ->
            consume(remoteIndex, index, key(remote)) { local ->
                isCompatible(remote, local)
            }
        }
    }

    fun hasCompatibleFormat(remote: RemoteCandidate, local: MatchCandidate): Boolean {
        if (remote.extension.isNotBlank() && local.extension.isNotBlank()) {
            return remote.extension == local.extension
        }
        return remote.fileType != TreeFileType.Other && remote.fileType == local.fileType
    }

    // 先让所有远端条目完成强身份匹配，避免前面的模糊命中占用后续条目的精确候选。
    consumePass(relativePathIndex, RemoteCandidate::normalizedRelativePath)
    consumePass(canonicalUrlIndex, RemoteCandidate::canonicalUrl)

    // 目录与标题仍一致时优先匹配，同时要求具体扩展名兼容。
    consumePass(trackKeyIndex, RemoteCandidate::trackKey, ::hasCompatibleFormat)

    // 完整文件名包含扩展名，可用于目录结构变化后的精确格式兜底。
    consumePass(fileNameIndex, RemoteCandidate::fileName)

    // 忽略目录的标题匹配最宽松，最后执行且同样不得跨格式占用候选。
    consumePass(
        trackKeyWithoutGroupIndex,
        RemoteCandidate::trackKeyWithoutGroup,
        ::hasCompatibleFormat
    )

    return matched
}

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

private fun collectLocalTreeLeafNodes(root: LocalTreeNode): List<LocalTreeNode> {
    val leaves = mutableListOf<LocalTreeNode>()
    val pending = ArrayDeque<LocalTreeNode>()
    pending.add(root)
    while (pending.isNotEmpty()) {
        val node = pending.removeLast()
        if (node.children.isEmpty()) {
            if (node.absolutePath != null) leaves += node
        } else {
            node.children.values.forEach(pending::addLast)
        }
    }
    return leaves
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

internal fun isSupportedSubtitleGenerationAudioName(name: String): Boolean {
    return SubtitleGenerationPolicy.supportsFileName(name)
}

internal fun subtitleGenerationTrackForFile(
    file: DirectoryFileItem,
    unavailableTrackIds: Set<Long>
): Track? {
    val track = file.track ?: return null
    return track.takeIf {
        file.fileType == TreeFileType.Audio &&
            isSupportedSubtitleGenerationAudioName(file.path) &&
            it.id > 0L &&
            !file.isOnline &&
            file.sizeSource is FileSizeSource.Local &&
            !isOnlineTrackPath(it.path) &&
            it.id !in unavailableTrackIds
    }
}

internal fun collectSubtitleGenerationTracks(
    index: LocalTreeIndex,
    currentPath: String,
    unavailableTrackIds: Set<Long>
): List<Track> {
    val currentNode = findLocalTreeNode(index.root, currentPath) ?: return emptyList()
    val tracks = mutableListOf<Track>()

    fun collect(node: LocalTreeNode) {
        if (node.children.isEmpty()) {
            val track = node.track
            val absolutePath = node.absolutePath.orEmpty()
            if (
                node.fileType == TreeFileType.Audio &&
                isSupportedSubtitleGenerationAudioName(node.name) &&
                track != null &&
                track.id > 0L &&
                !isOnlineTrackPath(track.path) &&
                !absolutePath.startsWith("http", ignoreCase = true) &&
                track.id !in unavailableTrackIds
            ) {
                tracks += track
            }
            return
        }
        node.children.values
            .sortedBy { SmartSortKey.of(it.name) }
            .forEach(::collect)
    }

    collect(currentNode)
    return tracks.distinctBy { it.id }
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
            val descendantLeaves = collectLocalTreeLeafNodes(child)
            DirectoryFolderItem(
                path = child.path,
                title = child.name,
                descendantTrackIds = descendantLeaves
                    .mapNotNull { it.track?.id?.takeIf { id -> id > 0L } }
                    .distinct(),
                hasLocalContent = descendantLeaves.any { node ->
                    node.absolutePath?.startsWith("http", ignoreCase = true) == false
                },
            )
        }
        .toList()
    val files = currentNode.children.values
        .asSequence()
        .filter { it.children.isEmpty() && it.absolutePath != null }
        .sortedBy { SmartSortKey.of(it.name) }
        .mapNotNull { child ->
            val absolutePath = child.absolutePath ?: return@mapNotNull null
            val isRemoteResource = absolutePath.startsWith("http", ignoreCase = true)
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
                isOnline = isOnlineDirectoryAudio(child.fileType, absolutePath, child.track),
                durationSeconds = child.track?.duration?.takeIf { it > 0.0 },
                sizeSource = if (isRemoteResource) {
                    FileSizeSource.Remote(absolutePath)
                } else {
                    FileSizeSource.Local(path = absolutePath, sizeBytes = child.sizeBytes)
                },
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

internal fun canSetDirectoryImageAsLocalCover(file: DirectoryFileItem): Boolean {
    return file.fileType == TreeFileType.Image &&
        !file.absolutePath.startsWith("http", ignoreCase = true)
}

internal fun downloadableOnlineAudioTrack(file: DirectoryFileItem): Track? {
    if (file.fileType != TreeFileType.Audio || !file.isOnline) return null
    return file.track?.takeIf { isOnlineTrackPath(it.path) }
}

internal class RemoteTreeNode(
    val name: String,
    val path: String,
    val children: MutableMap<String, RemoteTreeNode> = linkedMapOf(),
    var fileType: TreeFileType = TreeFileType.Other,
    var url: String = "",
    var durationSeconds: Double? = null,
    var subtitleSources: List<RemoteSubtitleSource> = emptyList(),
    var playlistTarget: PlaylistAddTarget? = null,
    var dlsitePlayImageCrypt: Boolean = false,
    var dlsitePlayImageWidth: Int? = null,
    var dlsitePlayImageHeight: Int? = null,
    var dlsitePlayOptimizedName: String? = null
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


    data class LeafFile(
        val rawTitle: String,
        val safeTitle: String,
        val url: String,
        val duration: Double?,
        val fileType: TreeFileType,
        val dlsitePlayImageCrypt: Boolean,
        val dlsitePlayImageWidth: Int?,
        val dlsitePlayImageHeight: Int?,
        val dlsitePlayOptimizedName: String?
    ) {
        val ext: String = rawTitle.substringAfterLast('.', "").lowercase()
        val baseName: String = rawTitle.substringBeforeLast('.')
        val displayTitle: String = sanitizeFolderName(baseName).ifBlank { safeTitle.substringBeforeLast('.') }
    }

    val subtitleCandidates = mutableListOf<Pair<com.asmr.player.util.SubtitleMatchCandidate, LeafFile>>()

    fun collectSubtitleCandidates(nodes: List<AsmrOneTrackNodeResponse>, parentPath: String) {
        nodes.forEach { node ->
            val children = node.children.orEmpty()
            val url = node.playbackUrl
            val rawTitle = node.title?.trim().orEmpty().ifBlank { "item" }
            val safeTitle = sanitizeFolderName(rawTitle)
            val path = if (parentPath.isBlank()) safeTitle else "$parentPath/$safeTitle"
            if (children.isEmpty()) {
                if (!url.isNullOrBlank()) {
                    val leaf = LeafFile(
                        rawTitle = rawTitle,
                        safeTitle = safeTitle,
                        url = url,
                        duration = node.duration,
                        fileType = treeFileTypeForNode(rawTitle, url, node.type),
                        dlsitePlayImageCrypt = node.dlsitePlayImageCrypt,
                        dlsitePlayImageWidth = node.dlsitePlayImageWidth,
                        dlsitePlayImageHeight = node.dlsitePlayImageHeight,
                        dlsitePlayOptimizedName = node.dlsitePlayOptimizedName
                    )
                    if (subtitleExts.contains(leaf.ext)) {
                        val candidate = SubtitleMatchSupport.inferCandidate(path, leaf.url)
                        if (candidate != null) subtitleCandidates += candidate to leaf
                    }
                }
            } else {
                collectSubtitleCandidates(children, path)
            }
        }
    }

    fun walk(
        nodes: List<AsmrOneTrackNodeResponse>,
        parentNode: RemoteTreeNode,
        parentPath: String
    ) {
        val leafFiles = nodes.mapNotNull { node ->
            val children = node.children.orEmpty()
            val url = node.playbackUrl
            if (!url.isNullOrBlank() && children.isEmpty()) {
                val rawTitle = node.title?.trim().orEmpty().ifBlank { "item" }
                val safeTitle = sanitizeFolderName(rawTitle)
                LeafFile(
                    rawTitle = rawTitle,
                    safeTitle = safeTitle,
                    url = url,
                    duration = node.duration,
                    fileType = treeFileTypeForNode(rawTitle, url, node.type),
                    dlsitePlayImageCrypt = node.dlsitePlayImageCrypt,
                    dlsitePlayImageWidth = node.dlsitePlayImageWidth,
                    dlsitePlayImageHeight = node.dlsitePlayImageHeight,
                    dlsitePlayOptimizedName = node.dlsitePlayOptimizedName
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
                TreeFileType.Audio, TreeFileType.Video -> {
                    val matched = SubtitleMatchSupport.matchBest(path.substringBeforeLast('.'), subtitleCandidates.map { it.first })
                    if (matched != null) {
                        subtitleCandidates.firstOrNull { it.first.sourceRef == matched.sourceRef }?.second?.let { subtitleLeaf ->
                            listOf(
                                RemoteSubtitleSource(
                                    url = subtitleLeaf.url,
                                    language = matched.language,
                                    ext = subtitleLeaf.ext.ifBlank { "vtt" }
                                )
                            )
                        }.orEmpty()
                    } else {
                        emptyList()
                    }
                }
                else -> emptyList()
            }
            val playlistTarget = when (leaf.fileType) {
                TreeFileType.Audio -> PlaylistAddTarget.fromTrack(
                    album = album,
                    track = Track(
                        albumId = album.id,
                        title = leaf.displayTitle,
                        path = leaf.url,
                        duration = leaf.duration ?: 0.0,
                        group = path.substringBeforeLast('/', "").substringAfterLast('/', ""),
                        lyricsRelativePathNoExt = path.substringBeforeLast('.')
                    )
                ).copy(remoteSubtitleSources = subtitleSources)
                TreeFileType.Video -> PlaylistAddTarget.fromVideo(album, leaf.displayTitle, leaf.url)
                else -> null
            }
            child.fileType = leaf.fileType
            child.url = leaf.url
            child.durationSeconds = leaf.duration
            child.subtitleSources = subtitleSources
            child.playlistTarget = playlistTarget
            child.dlsitePlayImageCrypt = leaf.dlsitePlayImageCrypt
            child.dlsitePlayImageWidth = leaf.dlsitePlayImageWidth
            child.dlsitePlayImageHeight = leaf.dlsitePlayImageHeight
            child.dlsitePlayOptimizedName = leaf.dlsitePlayOptimizedName
        }

        nodes.forEach { node ->
            val children = node.children.orEmpty()
            if (children.isEmpty()) return@forEach
            val rawTitle = node.title?.trim().orEmpty().ifBlank { "item" }
            val safeTitle = sanitizeFolderName(rawTitle)
            val path = if (parentPath.isBlank()) safeTitle else "$parentPath/$safeTitle"
            val childNode = parentNode.children.getOrPut(safeTitle) {
                RemoteTreeNode(name = safeTitle, path = path)
            }
            walk(children, childNode, path)
        }
    }

    collectSubtitleCandidates(tree, "")
    walk(tree, root, "")
    return RemoteTreeIndex(root = root)
}

private fun RemoteTreeNode.toDirectoryFileItem(): DirectoryFileItem {
    return DirectoryFileItem(
        path = path,
        title = name.substringBeforeLast('.'),
        fileType = fileType,
        isPlayable = fileType == TreeFileType.Audio || fileType == TreeFileType.Video,
        isOnline = true,
        durationSeconds = durationSeconds,
        sizeSource = if (url.isNotBlank()) FileSizeSource.Remote(url) else FileSizeSource.None,
        absolutePath = url,
        url = url,
        playlistTarget = playlistTarget,
        subtitleSources = subtitleSources,
        showSubtitleStamp = subtitleSources.isNotEmpty(),
        dlsitePlayImageCrypt = dlsitePlayImageCrypt,
        dlsitePlayImageWidth = dlsitePlayImageWidth,
        dlsitePlayImageHeight = dlsitePlayImageHeight,
        dlsitePlayOptimizedName = dlsitePlayOptimizedName
    )
}

internal fun collectRemoteTreeImageFiles(index: RemoteTreeIndex): List<DirectoryFileItem> {
    val images = mutableListOf<DirectoryFileItem>()

    fun collect(node: RemoteTreeNode) {
        val children = node.children.values
        children.asSequence()
            .filter { it.children.isNotEmpty() }
            .sortedBy { SmartSortKey.of(it.name) }
            .forEach(::collect)
        children.asSequence()
            .filter { child ->
                child.children.isEmpty() &&
                    child.fileType == TreeFileType.Image &&
                    child.url.isNotBlank()
            }
            .sortedBy { SmartSortKey.of(it.name) }
            .mapTo(images) { it.toDirectoryFileItem() }
    }

    collect(index.root)
    return images
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
        .map(RemoteTreeNode::toDirectoryFileItem)
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
    val fileType: TreeFileType,
    val sizeBytes: Long? = null
)

internal fun onlineSavedResourceTreeLeaf(
    resource: OnlineSavedResourceEntity
): LocalTreeLeafCacheEntry? {
    val relativePath = resource.relativePath.replace('\\', '/').trim().trimStart('/')
    val url = resource.url.trim()
    if (relativePath.isBlank() || !url.startsWith("http", ignoreCase = true)) return null
    val fileType = runCatching { TreeFileType.valueOf(resource.fileType) }
        .getOrElse { treeFileTypeForNode(relativePath, url) }
    if (!isLibraryResourceSavableTreeFileType(fileType) || isPlayableTreeFileType(fileType)) return null
    return LocalTreeLeafCacheEntry(
        relativePath = relativePath,
        absolutePath = url,
        fileType = fileType
    )
}

internal data class LocalTreeIndexBuildResult(
    val index: LocalTreeIndex,
    val leaves: List<LocalTreeLeafCacheEntry>
)

internal enum class LocalTreeSourceKind {
    Imported,
    Downloaded,
}

internal data class LocalTreeSource(
    val path: String,
    val kind: LocalTreeSourceKind,
)

internal fun localTreeSourcesForAlbum(album: Album): List<LocalTreeSource> {
    val imported = buildList {
        val path = album.path.trim()
        if (path.isNotBlank() && !path.startsWith("http", ignoreCase = true) && !path.startsWith("web://", ignoreCase = true)) {
            add(path)
        }
        album.localPath?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
    }.distinctBy(::localTreeSourceIdentity)
    val downloaded = listOfNotNull(album.downloadPath?.trim()?.takeIf { it.isNotBlank() })
    val importedIdentities = imported.map(::localTreeSourceIdentity).toSet()
    return buildList {
        imported.forEach { add(LocalTreeSource(it, LocalTreeSourceKind.Imported)) }
        downloaded.filterNot { localTreeSourceIdentity(it) in importedIdentities }
            .forEach { add(LocalTreeSource(it, LocalTreeSourceKind.Downloaded)) }
    }.distinctBy { localTreeSourceIdentity(it.path) }
}

private fun localTreeSourceIdentity(path: String): String {
    val trimmed = path.trim()
    if (!trimmed.startsWith("content://", ignoreCase = true)) {
        return runCatching { File(trimmed).canonicalPath }.getOrDefault(File(trimmed).absolutePath)
    }
    val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return trimmed
    val documentId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
        ?: runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
        ?: return trimmed
    return "${uri.authority.orEmpty()}:${documentId.replace('\\', '/').trimEnd('/')}"
}

internal suspend fun loadOrBuildLocalTreeIndex(
    context: android.content.Context,
    albumId: Long,
    albumPaths: List<String>,
    tracks: List<Track>,
    onlineSavedResources: List<OnlineSavedResourceEntity> = emptyList(),
    sources: List<LocalTreeSource> = albumPaths.map { LocalTreeSource(it, LocalTreeSourceKind.Imported) },
): LocalTreeIndex {
    val gson = Gson()
    val normalizedSources = sources.filter { it.path.isNotBlank() }.distinctBy { localTreeSourceIdentity(it.path) }
    val cacheKey = normalizedSources
        .map { "${it.kind.name}:${it.path.trim()}" }
        .sorted()
        .joinToString("|")
    val stamp = computeLocalTreeCacheStamp(context, albumPaths, tracks)
    val dao = AppDatabaseProvider.get(context).localTreeCacheDao()
    val onlineTracks = tracks.filter { it.path.trim().startsWith("http", ignoreCase = true) }
    val onlineUrlSet = onlineTracks.map { it.path.trim() }.filter { it.isNotBlank() }.toSet()
    val savedResourceKeys = onlineSavedResources.mapNotNull { resource ->
        val relativePath = resource.relativePath.replace('\\', '/').trim().trimStart('/')
        val url = resource.url.trim()
        if (relativePath.isBlank() || url.isBlank()) null else relativePath to url
    }.toSet()


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
            val chapter = ChapterMediaReference.parse(url)
            val ext = chapter?.fileName?.substringAfterLast('.', "") ?: guessExtFromUrl(url)
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
                .joinToString("/") { sanitizeFolderName(it) }

            val baseName = sanitizeFolderName(t.title.ifBlank { "track" })
            val fileName = if (ext.isNotBlank() && !baseName.endsWith(".$ext", ignoreCase = true)) "$baseName.$ext" else baseName
            val rel = if (groupPath.isBlank()) fileName else "$groupPath/$fileName"
            LocalTreeLeafCacheEntry(relativePath = rel, absolutePath = url, fileType = type)
        }
    }

    fun buildSavedResourceLeaves(): List<LocalTreeLeafCacheEntry> {
        return onlineSavedResources.mapNotNull(::onlineSavedResourceTreeLeaf)
    }

    fun mergeLeaves(
        localLeaves: List<LocalTreeLeafCacheEntry>,
        onlineLeaves: List<LocalTreeLeafCacheEntry>,
        savedResourceLeaves: List<LocalTreeLeafCacheEntry>
    ): List<LocalTreeLeafCacheEntry> {
        val filteredLocal = localLeaves.filter { leaf ->
            val abs = leaf.absolutePath.trim()
            !(
                abs.startsWith("http", ignoreCase = true) &&
                    !onlineUrlSet.contains(abs) &&
                    !savedResourceKeys.contains(leaf.relativePath to abs)
            )
        }
        val byRel = linkedMapOf<String, LocalTreeLeafCacheEntry>()
        filteredLocal.forEach { byRel[it.relativePath] = it }

        fun mergeRemoteLeaf(leaf: LocalTreeLeafCacheEntry) {
            val existing = byRel[leaf.relativePath]
            if (existing == null) {
                byRel[leaf.relativePath] = leaf
            } else {
                val abs = existing.absolutePath.trim()
                if (abs.startsWith("http", ignoreCase = true)) byRel[leaf.relativePath] = leaf
            }
        }

        onlineLeaves.forEach(::mergeRemoteLeaf)
        savedResourceLeaves.forEach(::mergeRemoteLeaf)
        return byRel.values.toList()
    }
    val onlineLeaves = buildOnlineLeaves()
    val savedResourceLeaves = buildSavedResourceLeaves()

    val cached = dao.getByAlbumAndKey(albumId = albumId, cacheKey = cacheKey)
    if (cached != null && cached.stamp == stamp && cached.payloadJson.isNotBlank()) {
        val type = object : TypeToken<List<LocalTreeLeafCacheEntry>>() {}.type
        val leaves = runCatching { gson.fromJson<List<LocalTreeLeafCacheEntry>>(cached.payloadJson, type) }
            .getOrDefault(emptyList())
        val merged = mergeLeaves(
            localLeaves = leaves,
            onlineLeaves = onlineLeaves,
            savedResourceLeaves = savedResourceLeaves
        )
        val missingLocalSizeMetadata = leaves.any { leaf ->
            val abs = leaf.absolutePath.trim()
            abs.isNotBlank() &&
                !abs.startsWith("http", ignoreCase = true) &&
                leaf.sizeBytes == null
        }
        if (merged.isNotEmpty() && !missingLocalSizeMetadata) {
            return buildLocalTreeIndexFromLeaves(leaves = merged, tracks = tracks)
        }
    }

    val built = buildLocalTreeIndexByScanningSources(
        context = context,
        sources = normalizedSources,
        tracks = tracks,
    )
    val merged = mergeLeaves(
        localLeaves = built.leaves,
        onlineLeaves = onlineLeaves,
        savedResourceLeaves = savedResourceLeaves
    )
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

private fun buildLocalTreeIndexByScanningSources(
    context: android.content.Context,
    sources: List<LocalTreeSource>,
    tracks: List<Track>,
): LocalTreeIndexBuildResult {
    val hasImported = sources.any { it.kind == LocalTreeSourceKind.Imported }
    val hasDownloaded = sources.any { it.kind == LocalTreeSourceKind.Downloaded }
    val separateSources = hasImported && hasDownloaded &&
        sources.map { localTreeSourceIdentity(it.path) }.distinct().size > 1
    if (!separateSources) {
        return buildLocalTreeIndexByScanning(
            context = context,
            albumPaths = sources.map { it.path },
            tracks = tracks,
        )
    }

    val leaves = sources.flatMap { source ->
        val prefix = when (source.kind) {
            LocalTreeSourceKind.Imported -> "导入内容"
            LocalTreeSourceKind.Downloaded -> "下载内容"
        }
        buildLocalTreeIndexByScanning(
            context = context,
            albumPaths = listOf(source.path),
            tracks = tracks,
        ).leaves.map { leaf ->
            leaf.copy(relativePath = "$prefix/${leaf.relativePath}")
        }
    }
    return LocalTreeIndexBuildResult(
        index = buildLocalTreeIndexFromLeaves(leaves, tracks),
        leaves = leaves,
    )
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

internal fun computeLocalTreeCacheStamp(
    context: android.content.Context,
    albumPaths: List<String>,
    tracks: List<Track>
): Long {
    return combineLocalTreeCacheStamp(computeAlbumPathsStamp(context, albumPaths), tracks)
}

internal fun combineLocalTreeCacheStamp(albumPathsStamp: Long, tracks: List<Track>): Long {
    var acc = (albumPathsStamp xor LOCAL_TREE_CACHE_PARSER_VERSION) * 1099511628211L
    val localTrackPaths = tracks.asSequence()
        .map { it.path.trim() }
        .filter { it.isNotBlank() && !it.startsWith("http", ignoreCase = true) }
        .sorted()
        .toList()
    localTrackPaths.forEach { path ->
        acc = (acc xor path.hashCode().toLong()) * 1099511628211L
    }
    acc = (acc xor localTrackPaths.size.toLong()) * 1099511628211L
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
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE
                ), null, null, null)?.use { cursor ->
                    val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(idIdx)
                        val name = cursor.getString(nameIdx)
                        val mime = cursor.getString(mimeIdx)
                        val sizeBytes = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else 0L
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
                                        child.sizeBytes = null
                                    } else {
                                        child.absolutePath = fileUri
                                        child.track = track
                                        child.sizeBytes = sizeBytes.takeIf { it > 0L }
                                    }
                                } else {
                                    child.absolutePath = null
                                    child.track = null
                                    child.sizeBytes = null
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
                runCatching { query(rootDocId, "") }
            }
        } else {
            val rootDir = java.io.File(albumPath)
            if (rootDir.exists()) {
                rootDir.walkTopDown().forEach fileLoop@{ file ->
                    if (!file.isFile) return@fileLoop
                    val type = treeFileTypeForName(file.name)
                    if (type == TreeFileType.Other || type == TreeFileType.Subtitle) return@fileLoop
                    val track = trackByAbsolutePath[file.absolutePath]
                    if (type == TreeFileType.Audio && track == null) return@fileLoop

                    val rawRel = runCatching { file.relativeTo(rootDir).path }.getOrElse { file.name }
                    val rel = rawRel.replace('\\', '/').trim().trimStart('/')
                    val segments = rel.split('/').filter { it.isNotBlank() }
                    if (segments.isEmpty()) return@fileLoop

                    var cur = root
                    segments.forEachIndexed { idx, seg ->
                        val nextPath = if (cur.path.isBlank()) seg else "${cur.path}/$seg"
                        val child = cur.children.getOrPut(seg) { LocalTreeNode(name = seg, path = nextPath) }
                        if (idx == segments.lastIndex) {
                            child.absolutePath = file.absolutePath
                            child.fileType = type
                            child.track = track
                            child.sizeBytes = file.length().takeIf { it > 0L }
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
                    fileType = node.fileType,
                    sizeBytes = node.sizeBytes
                )
            )
            return
        }
        node.children.values.forEach { child -> collectLeaves(child, out) }
    }

    val leaves = mutableListOf<LocalTreeLeafCacheEntry>()
    collectLeaves(root, leaves)
    return LocalTreeIndexBuildResult(
        index = LocalTreeIndex(root = root),
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
                child.sizeBytes = leaf.sizeBytes
            }
            cur = child
        }
    }

    return LocalTreeIndex(root = root)
}

internal data class AsmrOneLeafUi(
    val relativePath: String,
    val title: String,
    val url: String,
    val duration: Double?,
    val subtitles: List<com.asmr.player.util.RemoteSubtitleSource>
) {
fun toTrack(): Track {
        val normalizedRelativePath = relativePath.replace('\\', '/').trim().trimStart('/')
        val group = normalizedRelativePath.substringBeforeLast('/', "").substringAfterLast('/', "")
        return Track(
            albumId = 0,
            title = title,
            path = url,
            duration = duration ?: 0.0,
            group = group,
            lyricsRelativePathNoExt = normalizedRelativePath.substringBeforeLast('.'),
            remoteSubtitleSources = subtitles
        )
    }
}

internal fun flattenAsmrOneTracksForUi(tree: List<AsmrOneTrackNodeResponse>): List<AsmrOneLeafUi> {
    val out = mutableListOf<AsmrOneLeafUi>()

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

    val subtitleCandidates = mutableListOf<Pair<com.asmr.player.util.SubtitleMatchCandidate, LeafFile>>()

    fun collectSubtitleCandidates(nodes: List<AsmrOneTrackNodeResponse>, parentPath: String) {
        nodes.forEach { node ->
            val children = node.children.orEmpty()
            val url = node.playbackUrl
            val rawTitle = node.title?.trim().orEmpty().ifBlank { "item" }
            val safeTitle = sanitizeFolderName(rawTitle)
            val path = if (parentPath.isBlank()) safeTitle else "$parentPath/$safeTitle"
            if (children.isEmpty()) {
                if (url.isNullOrBlank()) return@forEach
                val leaf = LeafFile(rawTitle = rawTitle, safeTitle = safeTitle, url = url, duration = node.duration)
                if (subtitleExts.contains(leaf.ext)) {
                    val candidate = SubtitleMatchSupport.inferCandidate(path, leaf.url)
                    if (candidate != null) subtitleCandidates += candidate to leaf
                }
            } else {
                collectSubtitleCandidates(children, path)
            }
        }
    }

    fun walk(nodes: List<AsmrOneTrackNodeResponse>, parentPath: String) {
        val leaves = nodes.mapNotNull { node ->
            val children = node.children.orEmpty()
            val url = node.playbackUrl
            if (!url.isNullOrBlank() && children.isEmpty()) {
                val rawTitle = node.title?.trim().orEmpty().ifBlank { "item" }
                val safeTitle = sanitizeFolderName(rawTitle)
                LeafFile(rawTitle = rawTitle, safeTitle = safeTitle, url = url, duration = node.duration)
            } else null
        }

        leaves.filter { it.ext.isBlank() || audioExts.contains(it.ext) }.forEach { leaf ->
            val path = if (parentPath.isBlank()) leaf.safeTitle else "$parentPath/${leaf.safeTitle}"
            val displayTitle = sanitizeFolderName(leaf.baseName).ifBlank { leaf.safeTitle }
            val matched = SubtitleMatchSupport.matchBest(path.substringBeforeLast('.'), subtitleCandidates.map { it.first })
            val subs = if (matched != null) {
                subtitleCandidates.firstOrNull { it.first.sourceRef == matched.sourceRef }?.second?.let { subtitleLeaf ->
                    listOf(com.asmr.player.util.RemoteSubtitleSource(url = subtitleLeaf.url, language = matched.language, ext = subtitleLeaf.ext))
                }.orEmpty()
            } else {
                emptyList()
            }
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
            val safeTitle = sanitizeFolderName(title)
            val path = if (parentPath.isBlank()) safeTitle else "$parentPath/$safeTitle"
            walk(children, path)
        }
    }

    collectSubtitleCandidates(tree, "")
    walk(tree, "")
    return out
}

internal fun flattenAsmrOneTreeForUi(
    tree: List<AsmrOneTrackNodeResponse>,
    expanded: Set<String>
): AsmrTreeUiResult {
    val out = mutableListOf<AsmrTreeUiEntry>()

    fun walk(nodes: List<AsmrOneTrackNodeResponse>, parentPath: String, depth: Int) {
        nodes.forEach { node ->
            val title = node.title?.trim().orEmpty().ifBlank { "item" }
            val safeTitle = sanitizeFolderName(title)
            val path = if (parentPath.isBlank()) safeTitle else "$parentPath/$safeTitle"
            val children = node.children.orEmpty()
            val url = node.playbackUrl
            if (children.isEmpty()) {
                val type = treeFileTypeForNode(title, url, node.type)
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
    TreeFileType.Archive -> "压缩包"
    TreeFileType.Document -> "文档"
    TreeFileType.Spreadsheet -> "表格"
    TreeFileType.Presentation -> "演示文稿"
    TreeFileType.Code -> "代码"
    TreeFileType.Ebook -> "电子书"
    TreeFileType.Font -> "字体"
    TreeFileType.AppPackage -> "安装包"
    TreeFileType.Other -> "文件"
}

internal fun directoryFileTypeLabel(file: DirectoryFileItem): String {
    return if (file.fileType == TreeFileType.Audio) {
        if (file.isOnline) "在线音频" else "本地音频"
    } else {
        fileTypeLabel(file.fileType)
    }
}

internal fun treeFileTypeIcon(fileType: TreeFileType): ImageVector = when (fileType) {
    TreeFileType.Audio -> Icons.Rounded.Audiotrack
    TreeFileType.Video -> Icons.Rounded.Movie
    TreeFileType.Image -> Icons.Rounded.Image
    TreeFileType.Subtitle -> Icons.Rounded.Subtitles
    TreeFileType.Text -> Icons.Rounded.Description
    TreeFileType.Pdf -> Icons.Rounded.PictureAsPdf
    TreeFileType.Archive -> Icons.Rounded.FolderZip
    TreeFileType.Document -> Icons.AutoMirrored.Rounded.Article
    TreeFileType.Spreadsheet -> Icons.Rounded.TableChart
    TreeFileType.Presentation -> Icons.Rounded.Slideshow
    TreeFileType.Code -> Icons.Rounded.Code
    TreeFileType.Ebook -> Icons.AutoMirrored.Rounded.MenuBook
    TreeFileType.Font -> Icons.Rounded.FontDownload
    TreeFileType.AppPackage -> Icons.Rounded.Android
    TreeFileType.Other -> Icons.AutoMirrored.Rounded.InsertDriveFile
}

internal fun treeFileTypeTint(fileType: TreeFileType, colorScheme: AsmrColorScheme): Color = when (fileType) {
    TreeFileType.Audio -> colorScheme.primary
    TreeFileType.Video -> colorScheme.accent
    TreeFileType.Image -> colorScheme.textSecondary
    TreeFileType.Subtitle -> colorScheme.textSecondary
    TreeFileType.Text -> colorScheme.textTertiary
    TreeFileType.Pdf -> colorScheme.danger
    TreeFileType.Archive -> colorScheme.accent
    TreeFileType.Document -> colorScheme.textSecondary
    TreeFileType.Spreadsheet -> colorScheme.primary
    TreeFileType.Presentation -> colorScheme.accent
    TreeFileType.Code -> colorScheme.primary
    TreeFileType.Ebook -> colorScheme.textSecondary
    TreeFileType.Font -> colorScheme.textTertiary
    TreeFileType.AppPackage -> colorScheme.primaryStrong
    TreeFileType.Other -> colorScheme.textTertiary
}

internal fun queryLocalFileSize(context: android.content.Context, path: String): Long? {
    val trimmed = path.trim()
    if (trimmed.isBlank()) return null
    return when {
        trimmed.startsWith("content://", ignoreCase = true) -> {
            runCatching {
                context.contentResolver.query(
                    Uri.parse(trimmed),
                    arrayOf(DocumentsContract.Document.COLUMN_SIZE, OpenableColumns.SIZE),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (!cursor.moveToFirst()) {
                        null
                    } else {
                        val documentIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                        val openableIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        when {
                            documentIndex >= 0 && !cursor.isNull(documentIndex) -> cursor.getLong(documentIndex)
                            openableIndex >= 0 && !cursor.isNull(openableIndex) -> cursor.getLong(openableIndex)
                            else -> null
                        }
                    }
                }
            }.getOrNull()
        }
        else -> runCatching { File(trimmed).takeIf { it.exists() }?.length() }.getOrNull()
    }?.takeIf { it > 0L }
}
