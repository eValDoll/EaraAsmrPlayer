package com.asmr.player.data.lyrics

import androidx.media3.common.MediaItem
import com.asmr.player.domain.model.Album
import com.asmr.player.domain.model.Track
import com.asmr.player.playback.MediaItemRequest
import com.asmr.player.util.TrackKeyNormalizer

const val EXTRA_ALBUM_WORK_ID = "album_work_id"
const val EXTRA_LYRICS_RELATIVE_PATH_NO_EXT = "lyrics_relative_path_no_ext"
const val EXTRA_TRACK_GROUP = "track_group"

data class LyricsTargetContext(
    val mediaId: String,
    val title: String,
    val group: String,
    val exactTargetKey: String,
    val fallbackTargetKey: String,
    val canonicalMediaId: String,
    val trackId: Long,
    val albumId: Long,
    val albumIdentity: String,
    val relativePathNoExt: String
)

fun lyricsTargetContextFromMediaItem(item: MediaItem?): LyricsTargetContext? {
    item ?: return null
    val mediaId = item.mediaId.trim()
    if (mediaId.isBlank()) return null

    val extras = item.mediaMetadata.extras
    val title = item.mediaMetadata.title?.toString().orEmpty().trim()
    val group = extras?.getString(EXTRA_TRACK_GROUP).orEmpty()
    val trackId = extras?.getLong("track_id") ?: 0L
    val albumId = extras?.getLong("album_id") ?: 0L
    val rjCode = extras?.getString("rj_code").orEmpty()
    val workId = extras?.getString(EXTRA_ALBUM_WORK_ID).orEmpty()
    val albumIdentity = rjCode.trim().ifBlank { workId.trim() }
    val relativePathNoExt = extras?.getString(EXTRA_LYRICS_RELATIVE_PATH_NO_EXT).orEmpty()
    return buildLyricsTargetContext(
        mediaId = mediaId,
        title = title,
        group = group,
        trackId = trackId,
        albumId = albumId,
        albumIdentity = albumIdentity,
        relativePathNoExt = relativePathNoExt
    )
}

fun lyricsTargetContextFromTrack(album: Album, track: Track): LyricsTargetContext? {
    val mediaId = track.path.trim()
    if (mediaId.isBlank()) return null
    val albumIdentity = album.rjCode.trim().ifBlank { album.workId.trim() }
    val relativePathNoExt = deriveLyricsRelativePathNoExt(
        mediaPath = mediaId,
        albumRoots = album.getAllLocalPaths()
    )
    return buildLyricsTargetContext(
        mediaId = mediaId,
        title = track.title,
        group = track.group,
        trackId = track.id,
        albumId = album.id,
        albumIdentity = albumIdentity,
        relativePathNoExt = relativePathNoExt
    )
}

fun lyricsTargetContextFromRequest(request: MediaItemRequest): LyricsTargetContext? {
    val mediaId = request.mediaId.trim().ifBlank { request.uri.trim() }
    if (mediaId.isBlank()) return null
    val albumIdentity = request.rjCode.trim().ifBlank { request.albumWorkId.trim() }
    return buildLyricsTargetContext(
        mediaId = mediaId,
        title = request.title,
        group = request.trackGroup,
        trackId = request.trackId,
        albumId = request.albumId,
        albumIdentity = albumIdentity,
        relativePathNoExt = request.lyricsRelativePathNoExt
    )
}

fun deriveLyricsRelativePathNoExt(mediaPath: String, albumRoots: List<String>): String {
    val trimmed = mediaPath.trim()
    if (trimmed.isBlank()) return ""
    return albumRoots.asSequence()
        .mapNotNull { root -> TrackKeyNormalizer.tryRelativePathFromFilePath(trimmed, root) }
        .firstOrNull()
        .orEmpty()
}

private fun buildLyricsTargetContext(
    mediaId: String,
    title: String,
    group: String,
    trackId: Long,
    albumId: Long,
    albumIdentity: String,
    relativePathNoExt: String
): LyricsTargetContext {
    val normalizedRelativePath = TrackKeyNormalizer.normalizeRelativePath(relativePathNoExt)
    val exactTargetKey = buildExactTargetKey(
        albumIdentity = albumIdentity,
        relativePathNoExt = normalizedRelativePath,
        title = title,
        group = group,
        mediaId = mediaId
    )
    val fallbackTargetKey = buildFallbackTargetKey(
        albumIdentity = albumIdentity,
        title = title,
        group = group,
        mediaId = mediaId
    )
    return LyricsTargetContext(
        mediaId = mediaId,
        title = title,
        group = group,
        exactTargetKey = exactTargetKey,
        fallbackTargetKey = fallbackTargetKey,
        canonicalMediaId = buildCanonicalMediaId(
            mediaId = mediaId,
            albumIdentity = albumIdentity,
            relativePathNoExt = normalizedRelativePath,
            title = title,
            group = group
        ),
        trackId = trackId,
        albumId = albumId,
        albumIdentity = normalizeAlbumIdentity(albumIdentity),
        relativePathNoExt = normalizedRelativePath
    )
}

private fun buildCanonicalMediaId(
    mediaId: String,
    albumIdentity: String,
    relativePathNoExt: String,
    title: String,
    group: String
): String {
    val identity = normalizeAlbumIdentity(albumIdentity)
    return when {
        identity.isNotBlank() && relativePathNoExt.isNotBlank() -> "album_rel\u0000$identity\u0000$relativePathNoExt"
        identity.isNotBlank() -> "album_name\u0000$identity\u0000${TrackKeyNormalizer.buildKey(title, group, null)}"
        else -> mediaId.trim()
    }
}

private fun buildExactTargetKey(
    albumIdentity: String,
    relativePathNoExt: String,
    title: String,
    group: String,
    mediaId: String
): String {
    val identity = normalizeAlbumIdentity(albumIdentity)
    return when {
        identity.isNotBlank() && relativePathNoExt.isNotBlank() -> "album_rel\u0000$identity\u0000$relativePathNoExt"
        identity.isNotBlank() -> "album_name\u0000$identity\u0000${TrackKeyNormalizer.buildKey(title, group, null)}"
        else -> "media\u0000${mediaId.trim()}"
    }
}

private fun buildFallbackTargetKey(
    albumIdentity: String,
    title: String,
    group: String,
    mediaId: String
): String {
    val identity = normalizeAlbumIdentity(albumIdentity)
    return when {
        identity.isNotBlank() -> "album_name\u0000$identity\u0000${TrackKeyNormalizer.buildKey(title, group, null)}"
        else -> "media\u0000${mediaId.trim()}"
    }
}

private fun normalizeAlbumIdentity(value: String): String {
    return value.trim().uppercase()
}
