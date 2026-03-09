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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject
import javax.inject.Singleton

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
        val mediaId = item.mediaId.ifBlank { item.localConfiguration?.uri.toString() }
        if (mediaId.isNotBlank() && playlistItemDao.isItemInPlaylist(playlistId, mediaId)) {
            return false
        }
        val entity = PlaylistItemEntity(
            playlistId = playlistId,
            mediaId = mediaId,
            title = item.mediaMetadata.title?.toString().orEmpty(),
            artist = item.mediaMetadata.artist?.toString().orEmpty(),
            uri = item.localConfiguration?.uri.toString(),
            artworkUri = resolvePlaylistItemArtwork(item),
            itemOrder = Int.MAX_VALUE
        )
        playlistItemDao.upsertItems(listOf(entity))
        return true
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
        val artwork = item.mediaMetadata.artworkUri?.toString().orEmpty().trim()
        if (artwork.isNotBlank()) return artwork

        val extras = item.mediaMetadata.extras
        val albumId = extras?.getLong("album_id") ?: 0L
        if (albumId > 0L) {
            val album = runCatching { albumDao.getAlbumById(albumId) }.getOrNull()
            albumArtworkOrEmpty(album)?.let { if (it.isNotBlank()) return it }
        }

        val uriString = item.localConfiguration?.uri?.toString().orEmpty().trim()
        val path = when {
            uriString.startsWith("file://", ignoreCase = true) -> runCatching {
                Uri.parse(uriString).path.orEmpty()
            }.getOrDefault("")
            uriString.startsWith("/") -> uriString
            else -> ""
        }.trim()
        if (path.isNotBlank()) {
            val track = runCatching { trackDao.getTrackByPathOnce(path) }.getOrNull()
            if (track != null) {
                val album = runCatching { albumDao.getAlbumById(track.albumId) }.getOrNull()
                albumArtworkOrEmpty(album)?.let { if (it.isNotBlank()) return it }
            }
        }
        return ""
    }

    private fun albumArtworkOrEmpty(album: AlbumEntity?): String? {
        if (album == null) return null
        val local = album.coverPath.trim()
        if (local.isNotBlank()) return local
        val url = album.coverUrl.trim()
        return url.takeIf { it.isNotBlank() }
    }
}

enum class RenamePlaylistResult {
    RENAMED,
    DUPLICATE,
    INVALID,
    NOT_FOUND
}
