package com.asmr.player.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import com.asmr.player.data.local.db.dao.PlaylistStatsRow
import com.asmr.player.data.local.db.entities.PlaylistEntity
import com.asmr.player.data.repository.PlaylistAddSummary
import com.asmr.player.data.repository.PlaylistRepository
import com.asmr.player.data.repository.RenamePlaylistResult
import com.asmr.player.util.MessageManager
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val playlists: StateFlow<List<PlaylistStatsRow>> = playlistRepository.observePlaylistsWithStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
                messageManager.showSuccess("已创建播放列表：$trimmed")
            } else {
                messageManager.showError("列表名称已存在：$trimmed")
            }
        }
    }

    fun deletePlaylist(playlist: PlaylistEntity) {
        viewModelScope.launch {
            playlistRepository.deletePlaylist(playlist)
            messageManager.showInfo("已删除播放列表：${playlist.name}")
        }
    }

    fun renamePlaylist(playlistId: Long, newName: String) {
        val trimmed = newName.trim()
        viewModelScope.launch {
            when (playlistRepository.renamePlaylist(playlistId, trimmed)) {
                RenamePlaylistResult.RENAMED -> messageManager.showSuccess("已重命名为：$trimmed")
                RenamePlaylistResult.DUPLICATE -> messageManager.showError("列表名称已存在：$trimmed")
                RenamePlaylistResult.INVALID -> messageManager.showError("列表名称不能为空")
                RenamePlaylistResult.NOT_FOUND -> messageManager.showError("列表不存在")
            }
        }
    }

    suspend fun addItemToPlaylist(playlistId: Long, item: MediaItem): Boolean {
        return addItemsToPlaylist(playlistId, listOf(item)).addedCount > 0
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
                messageManager.showSuccess("已添加 ${summary.addedCount} 项到$playlistName，跳过 ${summary.skippedCount} 项")
            summary.addedCount > 0 ->
                messageManager.showSuccess("已添加 ${summary.addedCount} 项到$playlistName")
            summary.totalCount > 0 ->
                messageManager.showInfo("所选项目已在$playlistName")
            else -> Unit
        }
    }
}
