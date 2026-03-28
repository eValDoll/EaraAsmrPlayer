package com.asmr.player.ui.groups

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asmr.player.data.local.db.dao.AlbumGroupTrackRow
import com.asmr.player.data.repository.AlbumGroupRepository
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
class AlbumGroupDetailViewModel @Inject constructor(
    private val groupRepository: AlbumGroupRepository,
    private val messageManager: MessageManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val groupId = savedStateHandle.get<Long>("groupId")
        ?: savedStateHandle.get<String>("groupId")?.toLongOrNull()
        ?: -1L
    private val groupIdFlow = MutableStateFlow(groupId)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val tracks: StateFlow<List<AlbumGroupTrackRow>> = groupIdFlow
        .flatMapLatest { id ->
            if (id <= 0L) kotlinx.coroutines.flow.flowOf(emptyList())
            else groupRepository.observeGroupTracks(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setGroupId(id: Long) {
        if (groupIdFlow.value != id) {
            groupIdFlow.value = id
        }
    }

    fun removeTrack(mediaId: String) {
        val id = groupIdFlow.value
        if (id <= 0L || mediaId.isBlank()) return
        viewModelScope.launch {
            groupRepository.removeTrackFromGroup(id, mediaId)
            messageManager.showInfo("\u5DF2\u4ECE\u5206\u7EC4\u79FB\u9664")
        }
    }

    fun removeAlbum(albumId: Long) {
        val id = groupIdFlow.value
        if (id <= 0L || albumId <= 0L) return
        viewModelScope.launch {
            groupRepository.removeAlbumFromGroup(id, albumId)
            messageManager.showInfo("\u5DF2\u4ECE\u5206\u7EC4\u79FB\u9664")
        }
    }

    fun moveTrackToTop(albumId: Long, mediaId: String) {
        val id = groupIdFlow.value
        if (id <= 0L || albumId <= 0L || mediaId.isBlank()) return
        viewModelScope.launch {
            if (groupRepository.moveTrackToTop(id, albumId, mediaId)) {
                messageManager.showInfo("\u5DF2\u79FB\u81F3\u9876\u90E8")
            }
        }
    }

    fun moveTrackToBottom(albumId: Long, mediaId: String) {
        val id = groupIdFlow.value
        if (id <= 0L || albumId <= 0L || mediaId.isBlank()) return
        viewModelScope.launch {
            if (groupRepository.moveTrackToBottom(id, albumId, mediaId)) {
                messageManager.showInfo("\u5DF2\u79FB\u81F3\u672B\u5C3E")
            }
        }
    }

    fun saveAlbumTrackOrder(albumId: Long, orderedMediaIds: List<String>) {
        val id = groupIdFlow.value
        if (id <= 0L || albumId <= 0L || orderedMediaIds.isEmpty()) return
        viewModelScope.launch {
            groupRepository.reorderAlbumTracks(id, albumId, orderedMediaIds)
        }
    }
}
