package com.asmr.player.playback

import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.asmr.player.domain.model.Album
import com.asmr.player.domain.model.Track
import java.io.File

data class MediaItemRequest(
    val mediaId: String,
    val uri: String,
    val title: String,
    val artist: String = "",
    val albumTitle: String = "",
    val artworkUri: String = "",
    val albumId: Long = 0L,
    val trackId: Long = 0L,
    val rjCode: String = "",
    val mimeType: String? = null,
    val isVideo: Boolean = false
)

object MediaItemFactory {
    fun fromTrack(album: Album, track: Track): MediaItem {
        val circle = album.circle.trim()
        val cv = album.cv.trim()
        val artist = when {
            circle.isNotBlank() && cv.isNotBlank() -> "$circle / $cv"
            cv.isNotBlank() -> cv
            circle.isNotBlank() -> circle
            else -> ""
        }
        return fromRequest(
            MediaItemRequest(
                mediaId = track.path,
                uri = track.path,
                title = track.title,
                artist = artist,
                albumTitle = album.title,
                artworkUri = album.coverPath.ifBlank { album.coverUrl },
                albumId = album.id,
                trackId = track.id,
                rjCode = album.rjCode
            )
        )
    }

    fun fromDetails(
        mediaId: String,
        uri: String,
        title: String,
        artist: String = "",
        albumTitle: String = "",
        artworkUri: String = "",
        albumId: Long = 0L,
        trackId: Long = 0L,
        rjCode: String = "",
        mimeType: String? = null,
        isVideo: Boolean = false
    ): MediaItem {
        return fromRequest(
            MediaItemRequest(
                mediaId = mediaId,
                uri = uri,
                title = title,
                artist = artist,
                albumTitle = albumTitle,
                artworkUri = artworkUri,
                albumId = albumId,
                trackId = trackId,
                rjCode = rjCode,
                mimeType = mimeType,
                isVideo = isVideo
            )
        )
    }

    fun fromRequest(request: MediaItemRequest): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(request.title)
            .setArtist(request.artist)
            .setArtworkUri(toArtworkUri(request.artworkUri))
            .setExtras(
                Bundle().apply {
                    if (request.albumId > 0L) putLong("album_id", request.albumId)
                    if (request.trackId > 0L) putLong("track_id", request.trackId)
                    if (request.rjCode.isNotBlank()) putString("rj_code", request.rjCode)
                    if (request.isVideo) putBoolean("is_video", true)
                }
            )
        if (request.albumTitle.isNotBlank()) {
            metadata.setAlbumTitle(request.albumTitle)
        }
        return MediaItem.Builder()
            .setUri(toPlayableUri(request.uri))
            .setMediaId(request.mediaId.ifBlank { request.uri })
            .setMimeType(request.mimeType ?: guessMimeType(request.uri))
            .setMediaMetadata(metadata.build())
            .build()
    }

    fun toPlayableUri(path: String): Uri {
        val trimmed = path.trim()
        return if (
            trimmed.startsWith("http", ignoreCase = true) ||
                trimmed.startsWith("content://", ignoreCase = true) ||
                trimmed.startsWith("file://", ignoreCase = true)
        ) {
            trimmed.toUri()
        } else {
            Uri.fromFile(File(trimmed))
        }
    }

    fun toArtworkUri(raw: String): Uri? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null
        return when {
            trimmed.startsWith("content://", ignoreCase = true) ||
                trimmed.startsWith("file://", ignoreCase = true) ||
                trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) -> trimmed.toUri()
            else -> {
                val file = File(trimmed)
                if (file.exists()) Uri.fromFile(file) else null
            }
        }
    }

    fun guessMimeType(path: String): String? {
        val trimmed = path.trim()
        val ext = trimmed.substringBefore('#').substringBefore('?').substringAfterLast('.', "").lowercase()
        return when (ext) {
            "mp3" -> "audio/mpeg"
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "ogg" -> "audio/ogg"
            "opus" -> "audio/opus"
            "mp4", "m4v" -> "video/mp4"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "mov" -> "video/quicktime"
            else -> null
        }
    }
}
