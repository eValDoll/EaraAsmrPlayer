package com.asmr.player.data.repository

import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import com.asmr.player.data.lyrics.EXTRA_ALBUM_WORK_ID
import com.asmr.player.data.lyrics.EXTRA_LYRICS_RELATIVE_PATH_NO_EXT
import com.asmr.player.data.lyrics.EXTRA_TRACK_GROUP
import com.asmr.player.data.local.db.entities.PlaylistItemEntity
import com.asmr.player.playback.MediaItemFactory
import java.io.File

object PlaylistMediaItemMapper {
    fun fromMediaItem(
        playlistId: Long,
        item: MediaItem,
        itemOrder: Int
    ): PlaylistItemEntity {
        val metadata = item.mediaMetadata
        val extras = metadata.extras
        val normalizedUri = repairPlayableUri(item.localConfiguration?.uri?.toString().orEmpty())
        return PlaylistItemEntity(
            playlistId = playlistId,
            mediaId = item.mediaId.ifBlank { normalizedUri },
            title = metadata.title?.toString().orEmpty(),
            artist = metadata.artist?.toString().orEmpty(),
            albumTitle = metadata.albumTitle?.toString().orEmpty(),
            uri = normalizedUri,
            artworkUri = metadata.artworkUri?.toString().orEmpty(),
            albumId = extras?.getLong("album_id") ?: 0L,
            trackId = extras?.getLong("track_id") ?: 0L,
            rjCode = extras?.getString("rj_code").orEmpty(),
            albumWorkId = extras?.getString(EXTRA_ALBUM_WORK_ID).orEmpty(),
            trackGroup = extras?.getString(EXTRA_TRACK_GROUP).orEmpty(),
            lyricsRelativePathNoExt = extras?.getString(EXTRA_LYRICS_RELATIVE_PATH_NO_EXT).orEmpty(),
            mimeType = item.localConfiguration?.mimeType.orEmpty(),
            isVideo = extras?.getBoolean("is_video") == true,
            itemOrder = itemOrder
        )
    }

    fun toMediaItemOrNull(item: PlaylistItemEntity): MediaItem? {
        val normalizedUri = repairPlayableUri(item.uri)
        if (normalizedUri.isBlank() || normalizedUri.equals("null", ignoreCase = true)) return null
        return MediaItemFactory.fromDetails(
            mediaId = item.mediaId.trim().ifBlank { normalizedUri },
            uri = normalizedUri,
            title = item.title,
            artist = item.artist,
            albumTitle = item.albumTitle,
            artworkUri = item.artworkUri,
            albumId = item.albumId,
            trackId = item.trackId,
            rjCode = item.rjCode,
            albumWorkId = item.albumWorkId,
            trackGroup = item.trackGroup,
            lyricsRelativePathNoExt = item.lyricsRelativePathNoExt,
            mimeType = item.mimeType.ifBlank { null },
            isVideo = item.isVideo
        )
    }

    fun repairPlayableUri(raw: String): String {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("content://", ignoreCase = true)) return trimmed
        return repairDocumentUri(trimmed)
    }

    private fun repairDocumentUri(raw: String): String {
        val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return raw
        val segments = uri.pathSegments ?: return raw
        val docIndex = segments.indexOf("document")
        if (docIndex < 0 || segments.size <= docIndex + 2) return raw
        val docId = segments.subList(docIndex + 1, segments.size).joinToString("/")
        val encodedDocId = Uri.encode(docId)
        val encodedPath = "/" + segments.take(docIndex + 1).joinToString("/") + "/" + encodedDocId
        return uri.buildUpon().encodedPath(encodedPath).build().toString()
    }
}
