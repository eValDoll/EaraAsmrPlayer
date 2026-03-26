package com.asmr.player.data.repository

import android.net.Uri
import androidx.media3.common.MediaItem
import com.asmr.player.data.local.db.dao.AlbumDao
import com.asmr.player.data.local.db.dao.PlaylistDao
import com.asmr.player.data.local.db.dao.PlaylistItemDao
import com.asmr.player.data.local.db.dao.PlaylistStatsRow
import com.asmr.player.data.local.db.dao.TrackDao
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.data.local.db.entities.PlaylistEntity
import com.asmr.player.data.local.db.entities.PlaylistItemEntity
import com.asmr.player.data.local.db.entities.PlaylistItemWithSubtitles
import com.asmr.player.util.ManualItemOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class PlaylistAddSummary(
    val addedCount: Int,
    val skippedCount: Int
) {
    val totalCount: Int get() = addedCount + skippedCount
}

@Singleton
class PlaylistRepository @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val playlistItemDao: PlaylistItemDao,
    private val trackDao: TrackDao,
    private val albumDao: AlbumDao
) {
    companion object {
        const val CATEGORY_SYSTEM = "system"
        const val CATEGORY_USER = "user"

        const val PLAYLIST_FAVORITES = "我的收藏"
    }

    fun observeAllPlaylists(): Flow<List<PlaylistEntity>> = playlistDao.getAllPlaylists()

    fun observePlaylistsWithStats(): Flow<List<PlaylistStatsRow>> = playlistDao.observePlaylistsWithStats()

    suspend fun ensureSystemPlaylists() {
        ensurePlaylist(name = PLAYLIST_FAVORITES, category = CATEGORY_SYSTEM)
    }

    suspend fun getOrCreateFavoritesPlaylistId(): Long = ensurePlaylist(PLAYLIST_FAVORITES, CATEGORY_SYSTEM)

    suspend fun getPlaylistById(id: Long): PlaylistEntity? = playlistDao.getPlaylistByIdOnce(id)

    fun observePlaylistItems(playlistId: Long): Flow<List<PlaylistItemEntity>> {
        return playlistItemDao.observeItems(playlistId)
    }

    fun observePlaylistItemsWithSubtitles(playlistId: Long): Flow<List<PlaylistItemWithSubtitles>> {
        return playlistItemDao.observeItemsWithSubtitles(playlistId)
    }

    suspend fun replacePlaylistWithMediaItems(playlistId: Long, items: List<MediaItem>) {
        val entities = items.mapIndexed { index, item ->
            PlaylistItemEntity(
                playlistId = playlistId,
                mediaId = item.mediaId.ifBlank { item.localConfiguration?.uri.toString() },
                title = item.mediaMetadata.title?.toString().orEmpty(),
                artist = item.mediaMetadata.artist?.toString().orEmpty(),
                uri = item.localConfiguration?.uri.toString(),
                artworkUri = resolvePlaylistItemArtwork(item),
                itemOrder = index
            )
        }
        playlistItemDao.replaceAll(playlistId, entities)
    }

    suspend fun createUserPlaylist(name: String): Long? {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return null
        val existing = playlistDao.getPlaylistByNameOnce(trimmed)
        if (existing != null) return null
        return playlistDao.insertPlaylist(PlaylistEntity(name = trimmed, category = CATEGORY_USER))
    }

    suspend fun deletePlaylist(playlist: PlaylistEntity) {
        playlistItemDao.clearPlaylist(playlist.id)
        playlistDao.deletePlaylist(playlist)
    }

    suspend fun renamePlaylist(playlistId: Long, newName: String): RenamePlaylistResult {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return RenamePlaylistResult.INVALID
        val current = playlistDao.getPlaylistByIdOnce(playlistId) ?: return RenamePlaylistResult.NOT_FOUND
        val conflict = playlistDao.getPlaylistByNameOnce(trimmed)
        if (conflict != null && conflict.id != playlistId) return RenamePlaylistResult.DUPLICATE
        if (!current.name.equals(trimmed, ignoreCase = true)) {
            playlistDao.updatePlaylistName(playlistId, trimmed)
        }
        return RenamePlaylistResult.RENAMED
    }

    suspend fun addItemToPlaylist(playlistId: Long, item: MediaItem): Boolean {
        return addItemsToPlaylist(playlistId, listOf(item)).addedCount > 0
    }

    suspend fun addItemsToPlaylist(playlistId: Long, items: List<MediaItem>): PlaylistAddSummary {
        if (playlistId <= 0L || items.isEmpty()) {
            return PlaylistAddSummary(addedCount = 0, skippedCount = items.size)
        }
        val currentItems = playlistItemDao.getItemsOnce(playlistId)
        val existingIds = currentItems.map { it.mediaId }.toHashSet()
        val stagedIds = linkedSetOf<String>()
        val toInsert = mutableListOf<PlaylistItemEntity>()
        var nextOrder = (currentItems.maxOfOrNull { it.itemOrder } ?: -1) + 1
        var skipped = 0

        items.forEach { item ->
            val mediaId = item.mediaId.ifBlank { item.localConfiguration?.uri.toString().orEmpty() }.trim()
            if (mediaId.isBlank() || !existingIds.add(mediaId) || !stagedIds.add(mediaId)) {
                skipped += 1
                return@forEach
            }
            toInsert += PlaylistItemEntity(
                playlistId = playlistId,
                mediaId = mediaId,
                title = item.mediaMetadata.title?.toString().orEmpty(),
                artist = item.mediaMetadata.artist?.toString().orEmpty(),
                uri = item.localConfiguration?.uri.toString(),
                artworkUri = resolvePlaylistItemArtwork(item),
                itemOrder = nextOrder++
            )
        }

        if (toInsert.isNotEmpty()) {
            playlistItemDao.upsertItems(toInsert)
        }
        return PlaylistAddSummary(
            addedCount = toInsert.size,
            skippedCount = skipped
        )
    }

    suspend fun reorderPlaylistItems(playlistId: Long, orderedMediaIds: List<String>): Boolean {
        if (playlistId <= 0L) return false
        val current = playlistItemDao.getItemsOnce(playlistId)
        if (current.size < 2) return false
        val reordered = ManualItemOrder.reorderByKeys(current, orderedMediaIds) { it.mediaId }
        return persistPlaylistOrder(current = current, ordered = reordered)
    }

    suspend fun movePlaylistItemToTop(playlistId: Long, mediaId: String): Boolean {
        if (playlistId <= 0L || mediaId.isBlank()) return false
        val current = playlistItemDao.getItemsOnce(playlistId)
        if (current.size < 2) return false
        val reordered = ManualItemOrder.moveKeyToStart(current, mediaId) { it.mediaId }
        return persistPlaylistOrder(current = current, ordered = reordered)
    }

    suspend fun movePlaylistItemToBottom(playlistId: Long, mediaId: String): Boolean {
        if (playlistId <= 0L || mediaId.isBlank()) return false
        val current = playlistItemDao.getItemsOnce(playlistId)
        if (current.size < 2) return false
        val reordered = ManualItemOrder.moveKeyToEnd(current, mediaId) { it.mediaId }
        return persistPlaylistOrder(current = current, ordered = reordered)
    }

    suspend fun removeItemFromPlaylist(playlistId: Long, mediaId: String) {
        playlistItemDao.deleteItem(playlistId, mediaId)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observeIsFavorite(mediaId: String): Flow<Boolean> {
        return playlistDao.getPlaylistByName(PLAYLIST_FAVORITES).map { playlist ->
            playlist?.id ?: -1L
        }.flatMapLatest { id ->
            if (id == -1L) kotlinx.coroutines.flow.flowOf(false)
            else playlistItemDao.observeIsItemInPlaylist(id, mediaId)
        }
    }

    private suspend fun ensurePlaylist(name: String, category: String): Long {
        val existing = playlistDao.getPlaylistByNameOnce(name)
        if (existing != null) return existing.id
        return playlistDao.insertPlaylist(
            PlaylistEntity(name = name, category = category)
        )
    }

    private suspend fun resolvePlaylistItemArtwork(item: MediaItem): String {
        val extras = item.mediaMetadata.extras
        android.util.Log.d("PlaylistRepository", "resolvePlaylistItemArtwork - START - mediaId: ${item.mediaId}, extras: $extras")
        val albumId = extras?.getLong("album_id") ?: 0L
        android.util.Log.d("PlaylistRepository", "resolvePlaylistItemArtwork - albumId: $albumId")
        if (albumId > 0L) {
            val album = runCatching { albumDao.getAlbumById(albumId) }.getOrNull()
            android.util.Log.d("PlaylistRepository", "resolvePlaylistItemArtwork - album from db: $album")
            albumArtworkOrEmpty(album)?.let { 
                android.util.Log.d("PlaylistRepository", "resolvePlaylistItemArtwork - returning album artwork: '$it'")
                if (it.isNotBlank()) return it 
            }
        }

        val artwork = item.mediaMetadata.artworkUri?.toString().orEmpty().trim()
        android.util.Log.d("PlaylistRepository", "resolvePlaylistItemArtwork - artworkUri from metadata: '$artwork'")
        if (artwork.isNotBlank()) {
            android.util.Log.d("PlaylistRepository", "resolvePlaylistItemArtwork - returning artwork from metadata: '$artwork'")
            return artwork
        }

        val uriString = item.localConfiguration?.uri?.toString().orEmpty().trim()
        android.util.Log.d("PlaylistRepository", "resolvePlaylistItemArtwork - uriString: '$uriString'")
        val path = when {
            uriString.startsWith("file://", ignoreCase = true) -> runCatching {
                Uri.parse(uriString).path.orEmpty()
            }.getOrDefault("")
            uriString.startsWith("/") -> uriString
            else -> ""
        }.trim()
        android.util.Log.d("PlaylistRepository", "resolvePlaylistItemArtwork - path: '$path'")
        if (path.isNotBlank()) {
            val track = runCatching { trackDao.getTrackByPathOnce(path) }.getOrNull()
            android.util.Log.d("PlaylistRepository", "resolvePlaylistItemArtwork - track: $track")
            if (track != null) {
                val album = runCatching { albumDao.getAlbumById(track.albumId) }.getOrNull()
                android.util.Log.d("PlaylistRepository", "resolvePlaylistItemArtwork - album from track: $album")
                albumArtworkOrEmpty(album)?.let { 
                    android.util.Log.d("PlaylistRepository", "resolvePlaylistItemArtwork - returning track album artwork: '$it'")
                    if (it.isNotBlank()) return it 
                }
            }
        }
        android.util.Log.d("PlaylistRepository", "resolvePlaylistItemArtwork - END - returning empty string")
        return ""
    }

    private fun albumArtworkOrEmpty(album: AlbumEntity?): String? {
        if (album == null) return null
        val local = album.coverPath.trim()
        if (local.isNotBlank()) return local
        val url = album.coverUrl.trim()
        return url.takeIf { it.isNotBlank() }
    }

    private suspend fun persistPlaylistOrder(
        current: List<PlaylistItemEntity>,
        ordered: List<PlaylistItemEntity>
    ): Boolean {
        val updated = ManualItemOrder.reindex(ordered) { item, index ->
            item.copy(itemOrder = index)
        }
        if (current == updated) return false
        playlistItemDao.upsertItems(updated)
        return true
    }
}

enum class RenamePlaylistResult {
    RENAMED,
    DUPLICATE,
    INVALID,
    NOT_FOUND
}
