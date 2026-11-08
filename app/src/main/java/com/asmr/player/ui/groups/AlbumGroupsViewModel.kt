package com.asmr.player.ui.groups

import com.asmr.player.i18n.appString
import com.asmr.player.R

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asmr.player.data.local.db.dao.AlbumGroupStatsRow
import com.asmr.player.data.local.db.entities.AlbumGroupEntity
import com.asmr.player.data.repository.AlbumGroupRepository
import com.asmr.player.data.repository.RenameAlbumGroupResult
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
class AlbumGroupsViewModel @Inject constructor(
    private val groupRepository: AlbumGroupRepository,
    private val messageManager: MessageManager
) : ViewModel() {
    private val initialContentReadiness = InitialContentReadiness()

    val groups: StateFlow<List<AlbumGroupStatsRow>> = groupRepository.observeGroupsWithStats()
        .let(initialContentReadiness::track)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun awaitInitialContent() {
        initialContentReadiness.awaitReady()
    }

    fun createGroup(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val id = groupRepository.createGroup(trimmed)
            if (id != null) {
                messageManager.showSuccess(appString(R.string.ui_group_created, trimmed))
            } else {
                messageManager.showError(appString(R.string.ui_group_name_already_exists, trimmed))
            }
        }
    }

    fun deleteGroup(group: AlbumGroupEntity) {
        viewModelScope.launch {
            groupRepository.deleteGroup(group)
            messageManager.showInfo(appString(R.string.ui_deleted_group, group.name))
        }
    }

    fun renameGroup(groupId: Long, newName: String) {
        val trimmed = newName.trim()
        viewModelScope.launch {
            when (groupRepository.renameGroup(groupId, trimmed)) {
                RenameAlbumGroupResult.RENAMED -> messageManager.showSuccess(appString(R.string.ui_renamed_to, trimmed))
                RenameAlbumGroupResult.DUPLICATE -> messageManager.showError(appString(R.string.ui_group_name_already_exists, trimmed))
                RenameAlbumGroupResult.INVALID -> messageManager.showError(appString(R.string.ui_group_name_cannot_be_empty))
                RenameAlbumGroupResult.NOT_FOUND -> messageManager.showError(appString(R.string.ui_group_does_not_exist))
            }
        }
    }

    fun addAlbumToGroupInBackground(
        groupId: Long,
        albumId: Long,
        onComplete: () -> Unit = {},
        onFailure: (Throwable) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                addAlbumToGroup(groupId, albumId)
                onComplete()
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                messageManager.showError(appString(R.string.ui_failed_to_add_to_group_please_try_again))
                onFailure(t)
            }
        }
    }

    suspend fun addAlbumToGroup(groupId: Long, albumId: Long) {
        if (groupId <= 0L || albumId <= 0L) return
        groupRepository.addAlbumToGroup(groupId, albumId)
        val group = groupRepository.getGroupById(groupId)
        val name = group?.name.orEmpty()
        messageManager.showSuccess(appString(R.string.ui_added_to_group, name))
    }
}
