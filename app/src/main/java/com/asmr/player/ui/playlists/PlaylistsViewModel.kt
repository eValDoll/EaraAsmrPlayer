package com.asmr.player.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import com.asmr.player.data.local.db.dao.PlaylistStatsRow
import com.asmr.player.data.local.db.entities.PlaylistEntity
import com.asmr.player.data.repository.PlaylistRepository
import com.asmr.player.data.repository.RenamePlaylistResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.asmr.player.util.MessageManager

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
        val added = playlistRepository.addItemToPlaylist(playlistId, item)
        val playlist = playlistRepository.getPlaylistById(playlistId)
        val name = playlist?.name ?: ""
        if (added) {
            messageManager.showSuccess("已添加到播放列表：$name")
        } else {
            messageManager.showInfo("已在播放列表：$name")
        }
        return added
    }
}
