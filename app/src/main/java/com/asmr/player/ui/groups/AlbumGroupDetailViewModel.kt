package com.asmr.player.ui.groups

import com.asmr.player.i18n.appString
import com.asmr.player.R

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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlbumGroupDetailState(
    val groupId: Long,
    val tracks: List<AlbumGroupTrackRow> = emptyList(),
)

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
    val content: StateFlow<AlbumGroupDetailState> = groupIdFlow
        .flatMapLatest { id ->
            if (id <= 0L) kotlinx.coroutines.flow.flowOf(AlbumGroupDetailState(id))
            else groupRepository.observeGroupTracks(id).map { AlbumGroupDetailState(id, it) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlbumGroupDetailState(groupId))

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
            messageManager.showInfo(appString(R.string.ui_removed_from_group))
        }
    }

    fun removeAlbum(albumId: Long) {
        val id = groupIdFlow.value
        if (id <= 0L || albumId <= 0L) return
        viewModelScope.launch {
            groupRepository.removeAlbumFromGroup(id, albumId)
            messageManager.showInfo(appString(R.string.ui_removed_from_group))
        }
    }

    fun moveTrackToTop(albumId: Long, mediaId: String) {
        val id = groupIdFlow.value
        if (id <= 0L || albumId <= 0L || mediaId.isBlank()) return
        viewModelScope.launch {
            if (groupRepository.moveTrackToTop(id, albumId, mediaId)) {
                messageManager.showInfo(appString(R.string.ui_moved_to_top))
            }
        }
    }

    fun moveTrackToBottom(albumId: Long, mediaId: String) {
        val id = groupIdFlow.value
        if (id <= 0L || albumId <= 0L || mediaId.isBlank()) return
        viewModelScope.launch {
            if (groupRepository.moveTrackToBottom(id, albumId, mediaId)) {
                messageManager.showInfo(appString(R.string.ui_moved_to_end))
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
