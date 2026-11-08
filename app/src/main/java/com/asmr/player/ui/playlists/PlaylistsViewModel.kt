package com.asmr.player.ui.playlists

import com.asmr.player.i18n.appString
import com.asmr.player.R

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import com.asmr.player.data.local.db.dao.PlaylistStatsRow
import com.asmr.player.data.local.db.entities.PlaylistEntity
import com.asmr.player.data.repository.PlaylistAddSummary
import com.asmr.player.data.repository.PlaylistRepository
import com.asmr.player.data.repository.RenamePlaylistResult
import com.asmr.player.ui.common.InitialContentReadiness
import com.asmr.player.util.MessageManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val messageManager: MessageManager
) : ViewModel() {
    private val initialContentReadiness = InitialContentReadiness()

    val playlists: StateFlow<List<PlaylistStatsRow>> = playlistRepository.observePlaylistsWithStats()
        .let(initialContentReadiness::track)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun awaitInitialContent() {
        initialContentReadiness.awaitReady()
    }

    init {
        viewModelScope.launch {
            playlistRepository.ensureSystemPlaylists()
        }
    }

    fun createPlaylist(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val id = playlistRepository.createUserPlaylist(trimmed)
            if (id != null) {
                messageManager.showSuccess(appString(R.string.ui_playlist_created, trimmed))
            } else {
                messageManager.showError(appString(R.string.ui_list_name_already_exists, trimmed))
            }
        }
    }

    fun deletePlaylist(playlist: PlaylistEntity) {
        viewModelScope.launch {
            playlistRepository.deletePlaylist(playlist)
            messageManager.showInfo(appString(R.string.ui_deleted_playlist, playlist.name))
        }
    }

    fun renamePlaylist(playlistId: Long, newName: String) {
        val trimmed = newName.trim()
        viewModelScope.launch {
            when (playlistRepository.renamePlaylist(playlistId, trimmed)) {
                RenamePlaylistResult.RENAMED -> messageManager.showSuccess(appString(R.string.ui_renamed_to, trimmed))
                RenamePlaylistResult.DUPLICATE -> messageManager.showError(appString(R.string.ui_list_name_already_exists, trimmed))
                RenamePlaylistResult.INVALID -> messageManager.showError(appString(R.string.ui_list_name_cannot_be_empty))
                RenamePlaylistResult.NOT_FOUND -> messageManager.showError(appString(R.string.ui_list_does_not_exist))
            }
        }
    }

    suspend fun addItemToPlaylist(playlistId: Long, item: MediaItem): Boolean {
        return addItemsToPlaylist(playlistId, listOf(item)).addedCount > 0
    }

    fun addItemsToFavoritesInBackground(items: List<MediaItem>) {
        viewModelScope.launch {
            try {
                addItemsToFavorites(items)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                messageManager.showError(appString(R.string.ui_failed_to_add_to_favorites_please_try_again))
            }
        }
    }

    fun addItemsToPlaylistInBackground(
        playlistId: Long,
        items: List<MediaItem>,
        onComplete: () -> Unit = {},
        onFailure: (Throwable) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                addItemsToPlaylist(playlistId, items)
                onComplete()
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                messageManager.showError(appString(R.string.ui_failed_to_add_to_list_please_try_again))
                onFailure(t)
            }
        }
    }

    suspend fun addItemsToFavorites(items: List<MediaItem>): PlaylistAddSummary {
        val favoritesId = playlistRepository.getOrCreateFavoritesPlaylistId()
        val summary = playlistRepository.addItemsToPlaylist(favoritesId, items)
        showAddSummary(PlaylistRepository.PLAYLIST_FAVORITES, summary)
        return summary
    }

    suspend fun addItemsToPlaylist(playlistId: Long, items: List<MediaItem>): PlaylistAddSummary {
        val summary = playlistRepository.addItemsToPlaylist(playlistId, items)
        val name = playlistRepository.getPlaylistById(playlistId)?.name.orEmpty()
        showAddSummary(name, summary)
        return summary
    }

    private fun showAddSummary(playlistName: String, summary: PlaylistAddSummary) {
        when {
            summary.addedCount > 0 && summary.skippedCount > 0 ->
                messageManager.showSuccess(appString(R.string.ui_added_items_to_skipping_items, summary.addedCount, playlistName, summary.skippedCount))
            summary.addedCount > 0 ->
                messageManager.showSuccess(appString(R.string.ui_added_items_to, summary.addedCount, playlistName))
            summary.totalCount > 0 ->
                messageManager.showInfo(appString(R.string.ui_the_selected_item_is_already_in, playlistName))
            else -> Unit
        }
    }
}
