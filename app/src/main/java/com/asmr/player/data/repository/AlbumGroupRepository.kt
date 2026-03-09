package com.asmr.player.data.repository

import com.asmr.player.data.local.db.dao.AlbumGroupDao
import com.asmr.player.data.local.db.dao.AlbumGroupItemDao
import com.asmr.player.data.local.db.dao.AlbumGroupStatsRow
import com.asmr.player.data.local.db.dao.AlbumGroupTrackRow
import com.asmr.player.data.local.db.dao.TrackDao
import com.asmr.player.data.local.db.entities.AlbumGroupEntity
import com.asmr.player.data.local.db.entities.AlbumGroupItemEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlbumGroupRepository @Inject constructor(
    private val groupDao: AlbumGroupDao,
    private val groupItemDao: AlbumGroupItemDao,
    private val trackDao: TrackDao
) {
    fun observeGroupsWithStats(): Flow<List<AlbumGroupStatsRow>> = groupDao.observeGroupsWithStats()

    fun observeGroupTracks(groupId: Long): Flow<List<AlbumGroupTrackRow>> = groupItemDao.observeGroupTracks(groupId)

    suspend fun getGroupById(id: Long): AlbumGroupEntity? = groupDao.getGroupByIdOnce(id)

    suspend fun createGroup(name: String): Long? {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return null
        val existing = groupDao.getGroupByNameOnce(trimmed)
        if (existing != null) return null
        return groupDao.insertGroup(AlbumGroupEntity(name = trimmed))
    }

    suspend fun deleteGroup(group: AlbumGroupEntity) {
        groupItemDao.clearGroup(group.id)
        groupDao.deleteGroup(group)
    }

    suspend fun renameGroup(groupId: Long, newName: String): RenameAlbumGroupResult {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return RenameAlbumGroupResult.INVALID
        val current = groupDao.getGroupByIdOnce(groupId) ?: return RenameAlbumGroupResult.NOT_FOUND
        val conflict = groupDao.getGroupByNameOnce(trimmed)
        if (conflict != null && conflict.id != groupId) return RenameAlbumGroupResult.DUPLICATE
        if (!current.name.equals(trimmed, ignoreCase = true)) {
            groupDao.updateGroupName(groupId, trimmed)
        }
        return RenameAlbumGroupResult.RENAMED
    }

    suspend fun addAlbumToGroup(groupId: Long, albumId: Long) {
        if (groupId <= 0L || albumId <= 0L) return
        val tracks = trackDao.getTracksForAlbumOnce(albumId).filter { it.path.isNotBlank() }
        if (tracks.isEmpty()) return
        val items = tracks
            .sortedBy { it.path }
            .mapIndexed { index, t ->
                AlbumGroupItemEntity(
                    groupId = groupId,
                    mediaId = t.path,
                    itemOrder = index
                )
            }
        groupItemDao.upsertItems(items)
    }

    suspend fun removeTrackFromGroup(groupId: Long, mediaId: String) {
        if (groupId <= 0L || mediaId.isBlank()) return
        groupItemDao.deleteItem(groupId, mediaId)
    }

    suspend fun removeAlbumFromGroup(groupId: Long, albumId: Long) {
        if (groupId <= 0L || albumId <= 0L) return
        groupItemDao.deleteAlbumFromGroup(groupId, albumId)
    }
}

enum class RenameAlbumGroupResult {
    RENAMED,
    DUPLICATE,
    INVALID,
    NOT_FOUND
}
