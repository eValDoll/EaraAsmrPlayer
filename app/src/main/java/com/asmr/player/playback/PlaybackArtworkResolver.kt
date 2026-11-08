package com.asmr.player.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import com.asmr.player.data.local.db.dao.AlbumDao
import com.asmr.player.data.local.db.dao.TrackDao
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.util.EmbeddedMediaExtractor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal data class PlaybackArtworkSource(
    val albumId: Long = 0L,
    val manualCoverPath: String = "",
    val defaultCoverPath: String = "",
)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackArtworkResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val albumDao: AlbumDao,
    private val trackDao: TrackDao,
) {
    internal fun observeSource(item: MediaItem): Flow<PlaybackArtworkSource> = flow {
        val albumId = findAlbumId(item)
        if (albumId > 0L) {
            emitAll(albumDao.observeAlbumById(albumId).map { album -> sourceForAlbum(album, item) })
        } else {
            emit(sourceForAlbum(null, item))
        }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    internal suspend fun resolve(item: MediaItem, source: PlaybackArtworkSource): Uri? =
        withContext(Dispatchers.IO) {
            val manualUri = MediaItemFactory.toArtworkUri(source.manualCoverPath)
            if (manualUri != null) {
                return@withContext manualUri
            }
            val embeddedUri = if (canExtractArtwork(item)) embeddedArtworkUri(item) else null
            embeddedUri ?: MediaItemFactory.toArtworkUri(source.defaultCoverPath)
        }

    internal suspend fun isCurrentSource(source: PlaybackArtworkSource): Boolean =
        withContext(Dispatchers.IO) {
            if (source.albumId <= 0L) return@withContext true
            val album = albumDao.getAlbumById(source.albumId) ?: return@withContext false
            source == sourceForAlbum(album, null)
        }

    private suspend fun findAlbumId(item: MediaItem): Long {
        item.mediaMetadata.extras?.getLong("album_id", 0L)?.takeIf { it > 0L }?.let { return it }
        item.mediaMetadata.extras?.getLong("track_id", 0L)?.takeIf { it > 0L }?.let { trackId ->
            trackDao.getTrackByIdOnce(trackId)?.let { return it.albumId }
        }
        val uri = item.localConfiguration?.uri
        val paths = listOfNotNull(
            item.mediaId.takeIf { it.isNotBlank() },
            uri?.toString(),
            uri?.takeIf { it.scheme == "file" }?.path,
        ).distinct()
        for (path in paths) {
            trackDao.getTrackByPathOnce(path)?.let { return it.albumId }
        }
        return 0L
    }

    private fun sourceForAlbum(album: AlbumEntity?, item: MediaItem?): PlaybackArtworkSource =
        if (album == null) {
            PlaybackArtworkSource(defaultCoverPath = item?.mediaMetadata?.artworkUri?.toString().orEmpty())
        } else {
            PlaybackArtworkSource(
                albumId = album.id,
                manualCoverPath = album.manualCoverPath.trim(),
                defaultCoverPath = album.coverPath.trim().ifBlank { album.coverUrl.trim() },
            )
        }

    private fun canExtractArtwork(item: MediaItem): Boolean {
        val uri = item.localConfiguration?.uri ?: return false
        if (uri.scheme in setOf("http", "https")) return false
        val mime = item.localConfiguration?.mimeType.orEmpty()
        val extension = uri.lastPathSegment.orEmpty().substringAfterLast('.', "").lowercase()
        return item.mediaMetadata.extras?.getBoolean("is_video") != true &&
            !mime.startsWith("video/") && extension !in setOf("mp4", "m4v", "webm", "mkv", "mov")
    }

    private fun embeddedArtworkUri(item: MediaItem): Uri? {
        val uri = item.localConfiguration?.uri ?: return null
        val cacheKey = "track:" + item.mediaId.ifBlank { uri.toString() }
        val file = EmbeddedMediaExtractor.getArtworkCacheFile(context, cacheKey)
        if (!file.exists() || file.length() <= 0L) {
            val path = if (uri.scheme == "file") uri.path.orEmpty() else uri.toString()
            val bitmap = EmbeddedMediaExtractor.extractArtwork(context, path) ?: return null
            try {
                EmbeddedMediaExtractor.saveArtworkToCache(context, cacheKey, bitmap) ?: return null
            } finally {
                bitmap.recycle()
            }
        }
        return file.takeIf { it.exists() && it.length() > 0L }?.let(Uri::fromFile)
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun MediaItem.withPlaybackArtwork(artworkUri: Uri?): MediaItem {
    val metadata = mediaMetadata.buildUpon()
        .setArtworkUri(artworkUri)
        .setArtworkData(null, null)
        .build()
    return if (metadata == mediaMetadata) this else buildUpon().setMediaMetadata(metadata).build()
}
