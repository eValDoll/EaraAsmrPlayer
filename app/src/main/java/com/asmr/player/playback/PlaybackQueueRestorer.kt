package com.asmr.player.playback

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.asmr.player.data.local.db.dao.AlbumDao
import com.asmr.player.data.local.db.dao.TrackDao
import com.asmr.player.data.lyrics.EXTRA_REMOTE_SUBTITLE_SOURCES_JSON
import com.asmr.player.domain.model.Album
import com.asmr.player.domain.model.Track
import com.asmr.player.util.RemoteSubtitleSource
import com.asmr.player.util.encodeRemoteSubtitleSources
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLDecoder
import javax.inject.Inject

internal data class RestoredPlaybackQueue(
    val items: List<MediaItem>,
    val state: PersistedPlaybackStateV2,
)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackQueueRestorer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trackDao: TrackDao,
    private val albumDao: AlbumDao,
    private val playbackStateStore: PlaybackStateStore,
) {
    internal suspend fun restore(): RestoredPlaybackQueue? = withContext(Dispatchers.IO) {
        val saved = playbackStateStore.load() ?: return@withContext null
        val originalQueue = runCatching { saved.queue }.getOrNull().orEmpty()
        val originalIndices = mutableListOf<Int>()
        val persistedItems = mutableListOf<PersistedPlaybackQueueItem>()
        val mediaItems = mutableListOf<MediaItem>()
        val sourceAvailability = mutableMapOf<String, Boolean>()
        originalQueue.forEachIndexed { index, entry ->
            val normalized = normalizePersistedItem(entry) ?: return@forEachIndexed
            val mediaItem = buildMediaItem(normalized) ?: return@forEachIndexed
            val source = mediaItem.localConfiguration?.uri?.toString().orEmpty()
            val available = sourceAvailability.getOrPut(source) {
                isPersistedLocalSourceAvailable(context, source)
            }
            if (!available) return@forEachIndexed
            originalIndices += index
            persistedItems += normalized
            mediaItems += mediaItem
        }
        if (mediaItems.isEmpty()) {
            playbackStateStore.clear()
            return@withContext null
        }

        val originalIndex = saved.currentIndex.coerceIn(originalQueue.indices)
        // 保留原曲目及进度；原曲目失效时选择其后第一首，末尾失效则退到最后一首。
        val index = originalIndices.indexOfFirst { it >= originalIndex }
            .takeIf { it >= 0 } ?: originalIndices.lastIndex
        val state = saved.copy(
            queue = persistedItems,
            currentIndex = index,
            positionMs = if (originalIndices[index] == originalIndex) saved.positionMs.coerceAtLeast(0L) else 0L,
            playWhenReady = false,
            speed = saved.speed.takeIf { it.isFinite() }?.coerceIn(0.5f, 2f) ?: 1f,
            pitch = saved.pitch.takeIf { it.isFinite() }?.coerceIn(0.5f, 2f) ?: 1f,
        )
        // 在交给播放器 prepare 之前清理持久化队列，避免下一次启动重复恢复失效来源。
        playbackStateStore.save(state)
        RestoredPlaybackQueue(mediaItems, state)
    }

    private fun normalizePersistedItem(item: PersistedPlaybackQueueItem?): PersistedPlaybackQueueItem? {
        if (item == null) return null
        val mediaId = runCatching { item.mediaId }.getOrNull().orEmpty().trim()
        if (mediaId.isBlank()) return null
        val uri = runCatching { item.uri }.getOrNull().orEmpty().trim()
        val sources = runCatching { item.remoteSubtitleSources }.getOrNull().orEmpty()
            .mapNotNull { source ->
                val url = runCatching { source.url }.getOrNull().orEmpty().trim()
                if (url.isBlank()) return@mapNotNull null
                PersistedRemoteSubtitleSource(url, source.language, source.ext)
            }
        return item.copy(mediaId = mediaId, uri = uri, remoteSubtitleSources = sources)
    }

    private suspend fun buildMediaItem(persisted: PersistedPlaybackQueueItem): MediaItem? {
        val id = persisted.mediaId.trim()
        if (id.isBlank()) return null
        val track = runCatching { trackDao.getTrackByPathOnce(id) }.getOrNull()
        if (track == null && persisted.trackId?.let { it > 0L } == true) {
            return null
        }
        val persistedAlbumId = persisted.albumId
        if (track == null && persistedAlbumId != null && persistedAlbumId > 0L) {
            val albumExists = runCatching {
                albumDao.getAlbumById(persistedAlbumId) != null
            }.getOrDefault(true)
            if (!albumExists) return null
        }
        return if (track != null) {
            val albumEntity = runCatching { albumDao.getAlbumById(track.albumId) }.getOrNull()
            val album = Album(
                id = albumEntity?.id ?: 0L,
                title = albumEntity?.title.orEmpty(),
                path = albumEntity?.path.orEmpty(),
                localPath = albumEntity?.localPath,
                downloadPath = albumEntity?.downloadPath,
                circle = albumEntity?.circle.orEmpty(),
                cv = albumEntity?.cv.orEmpty(),
                tags = albumEntity?.tags?.split(",")?.filter { it.isNotBlank() }.orEmpty(),
                coverUrl = albumEntity?.coverUrl.orEmpty(),
                coverPath = albumEntity?.coverPath.orEmpty(),
                coverThumbPath = albumEntity?.coverThumbPath.orEmpty(),
                workId = albumEntity?.workId.orEmpty(),
                rjCode = albumEntity?.rjCode.orEmpty().ifBlank { albumEntity?.workId.orEmpty() }
            )
            val t = Track(
                id = track.id,
                albumId = track.albumId,
                title = track.title,
                path = track.path,
                duration = track.duration,
                group = track.group,
                lyricsRelativePathNoExt = "",
                remoteSubtitleSources = persisted.remoteSubtitleSources.mapNotNull subtitleSource@{ persistedSource ->
                    val url = persistedSource.url.trim()
                    if (url.isBlank()) return@subtitleSource null
                    RemoteSubtitleSource(
                        url = url,
                        language = persistedSource.language.orEmpty().ifBlank { "default" },
                        ext = persistedSource.ext.orEmpty().ifBlank { url.substringAfterLast('.', "vtt") }
                    )
                }
            )
            MediaItemFactory.fromTrack(album, t)
        } else {
            val restoredRemoteSubtitleSources = persisted.remoteSubtitleSources.mapNotNull subtitleSource@{ persistedSource ->
                val url = persistedSource.url.trim()
                if (url.isBlank()) return@subtitleSource null
                RemoteSubtitleSource(
                    url = url,
                    language = persistedSource.language.orEmpty().ifBlank { "default" },
                    ext = persistedSource.ext.orEmpty().ifBlank { url.substringAfterLast('.', "vtt") }
                )
            }
            val uri = MediaItemFactory.toPlayableUri(persisted.uri.ifBlank { id })
            val title = persisted.title.orEmpty().ifBlank { deriveTitleFromId(id) }
            val meta = MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(persisted.artist.orEmpty())
                .setDurationMs(persisted.durationMs)
                .setAlbumTitle(persisted.albumTitle.orEmpty())
                .setArtworkUri(parsePossiblyEncodedUri(persisted.artworkUri))
                .setExtras(
                    android.os.Bundle().apply {
                        persisted.albumCv?.let { putString(EXTRA_ALBUM_CV, it) }
                        if (persisted.albumId != null) putLong("album_id", persisted.albumId)
                        if (persisted.trackId != null) putLong("track_id", persisted.trackId)
                        if (!persisted.rjCode.isNullOrBlank()) putString("rj_code", persisted.rjCode)
                        encodeRemoteSubtitleSources(restoredRemoteSubtitleSources)?.let { encoded ->
                            putString(EXTRA_REMOTE_SUBTITLE_SOURCES_JSON, encoded)
                        }
                    }
                )
                .build()
            MediaItem.Builder()
                .setUri(uri)
                .setMediaId(id)
                .setMimeType(persisted.mimeType)
                .setMediaMetadata(meta)
                .setClippingConfiguration(MediaItemFactory.chapterClipping(id, persisted.uri))
                .build()
        }
    }

    private fun deriveTitleFromId(mediaId: String): String {
        val id = mediaId.trim()
        if (id.isBlank()) return ""
        if (id.startsWith("http", ignoreCase = true)) {
            val last = runCatching { id.toUri().lastPathSegment }.getOrNull().orEmpty().ifBlank { id.substringAfterLast('/') }
            val clean = last.substringBefore('?').substringBefore('#')
            val decoded = runCatching { URLDecoder.decode(clean, "UTF-8") }.getOrDefault(clean)
            return decoded.substringBeforeLast('.', decoded).ifBlank { id }
        }
        return runCatching { File(id).nameWithoutExtension }.getOrDefault(id).ifBlank { id }
    }

    private fun parsePossiblyEncodedUri(value: String?): Uri? {
        val raw = value.orEmpty().trim()
        if (raw.isBlank()) return null
        val decoded = if (
            raw.startsWith("http%3A", ignoreCase = true) ||
                raw.startsWith("https%3A", ignoreCase = true) ||
                raw.startsWith("content%3A", ignoreCase = true) ||
                raw.startsWith("file%3A", ignoreCase = true)
        ) {
            runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
        } else {
            raw
        }
        return runCatching { decoded.toUri() }.getOrNull()
    }
}
