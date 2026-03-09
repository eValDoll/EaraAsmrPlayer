package com.asmr.player.ui.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asmr.player.data.local.db.dao.AlbumGroupStatsRow
import com.asmr.player.data.local.db.entities.AlbumGroupEntity
import com.asmr.player.data.repository.AlbumGroupRepository
import com.asmr.player.data.repository.RenameAlbumGroupResult
import com.asmr.player.util.MessageManager
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val groups: StateFlow<List<AlbumGroupStatsRow>> = groupRepository.observeGroupsWithStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun createGroup(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val id = groupRepository.createGroup(trimmed)
            if (id != null) {
                messageManager.showSuccess("已创建分组：$trimmed")
            } else {
                messageManager.showError("分组名称已存在：$trimmed")
            }
        }
    }

    fun deleteGroup(group: AlbumGroupEntity) {
        viewModelScope.launch {
            groupRepository.deleteGroup(group)
            messageManager.showInfo("已删除分组：${group.name}")
        }
    }

    fun renameGroup(groupId: Long, newName: String) {
        val trimmed = newName.trim()
        viewModelScope.launch {
            when (groupRepository.renameGroup(groupId, trimmed)) {
                RenameAlbumGroupResult.RENAMED -> messageManager.showSuccess("已重命名为：$trimmed")
                RenameAlbumGroupResult.DUPLICATE -> messageManager.showError("分组名称已存在：$trimmed")
                RenameAlbumGroupResult.INVALID -> messageManager.showError("分组名称不能为空")
                RenameAlbumGroupResult.NOT_FOUND -> messageManager.showError("分组不存在")
            }
        }
    }

    fun addAlbumToGroup(groupId: Long, albumId: Long) {
        if (groupId <= 0L || albumId <= 0L) return
        viewModelScope.launch {
            groupRepository.addAlbumToGroup(groupId, albumId)
            val group = groupRepository.getGroupById(groupId)
            val name = group?.name.orEmpty()
            messageManager.showSuccess("已添加到分组：$name")
        }
    }
}
