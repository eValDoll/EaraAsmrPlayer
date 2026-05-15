package com.asmr.player.ui.playlists

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asmr.player.data.local.db.entities.PlaylistItemWithSubtitles
import com.asmr.player.data.repository.PlaylistRepository
import com.asmr.player.util.MessageManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val messageManager: MessageManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val playlistId = savedStateHandle.get<Long>("playlistId")
        ?: savedStateHandle.get<String>("playlistId")?.toLongOrNull()
        ?: -1L
    private val playlistIdFlow = MutableStateFlow(playlistId)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val items: StateFlow<List<PlaylistItemWithSubtitles>> = playlistIdFlow
        .flatMapLatest { id ->
            if (id == -1L) kotlinx.coroutines.flow.flowOf(emptyList())
            else playlistRepository.observePlaylistItemsWithSubtitles(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setPlaylistId(id: Long) {
        if (playlistIdFlow.value != id) {
            playlistIdFlow.value = id
        }
    }

    fun removeItem(mediaId: String) {
        val id = playlistIdFlow.value
        if (id <= 0L || mediaId.isBlank()) return
        viewModelScope.launch {
            playlistRepository.removeItemFromPlaylist(id, mediaId)
            messageManager.showInfo("已从我的列表移除")
        }
    }

    fun moveItemToTop(mediaId: String) {
        val id = playlistIdFlow.value
        if (id <= 0L || mediaId.isBlank()) return
        viewModelScope.launch {
            if (playlistRepository.movePlaylistItemToTop(id, mediaId)) {
                messageManager.showInfo("已移至顶部")
            }
        }
    }

    fun moveItemToBottom(mediaId: String) {
        val id = playlistIdFlow.value
        if (id <= 0L || mediaId.isBlank()) return
        viewModelScope.launch {
            if (playlistRepository.movePlaylistItemToBottom(id, mediaId)) {
                messageManager.showInfo("已移至末尾")
            }
        }
    }

    fun saveManualOrder(orderedMediaIds: List<String>) {
        val id = playlistIdFlow.value
        if (id <= 0L || orderedMediaIds.isEmpty()) return
        viewModelScope.launch {
            playlistRepository.reorderPlaylistItems(id, orderedMediaIds)
        }
    }
}
