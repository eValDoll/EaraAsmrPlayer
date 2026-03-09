package com.asmr.player.playback

import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.asmr.player.domain.model.Album
import com.asmr.player.domain.model.Track
import java.io.File

object MediaItemFactory {
    fun fromTrack(album: Album, track: Track): MediaItem {
        val uri = toPlayableUri(track.path)
        val artworkUri = toArtworkUri(album.coverPath, album.coverUrl)
        val mimeType = guessMimeType(track.path)
        val circle = album.circle.trim()
        val cv = album.cv.trim()
        val artist = when {
            circle.isNotBlank() && cv.isNotBlank() -> "$circle · $cv"
            cv.isNotBlank() -> cv
            circle.isNotBlank() -> circle
            else -> ""
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(artist)
            .setAlbumTitle(album.title)
            .setArtworkUri(artworkUri)
            .setExtras(
                Bundle().apply {
                    putLong("album_id", album.id)
                    putLong("track_id", track.id)
                    putString("rj_code", album.rjCode)
                }
            )
            .build()

        return MediaItem.Builder()
            .setUri(uri)
            .setMediaId(track.path)
            .setMimeType(mimeType)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun toPlayableUri(path: String): Uri {
        val trimmed = path.trim()
        return if (trimmed.startsWith("http", ignoreCase = true) || trimmed.startsWith("content://")) {
            trimmed.toUri()
        } else {
            Uri.fromFile(File(trimmed))
        }
    }

    private fun toArtworkUri(coverPath: String, coverUrl: String): Uri? {
        val local = coverPath.trim()
        if (local.isNotBlank()) {
            if (local.startsWith("content://", ignoreCase = true) || local.startsWith("file://", ignoreCase = true)) {
                return local.toUri()
            }
            val f = File(local)
            if (f.exists()) return Uri.fromFile(f)
        }
        val url = coverUrl.trim()
        return if (url.startsWith("http", ignoreCase = true)) url.toUri() else null
    }

    private fun guessMimeType(path: String): String? {
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
            else -> null
        }
    }
}
