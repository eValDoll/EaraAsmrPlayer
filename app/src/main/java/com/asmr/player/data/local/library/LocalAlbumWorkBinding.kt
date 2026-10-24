package com.asmr.player.data.local.library

import androidx.room.withTransaction
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.dao.AlbumDao
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.data.remote.dlsite.resolveCloudSyncWorkId
import com.asmr.player.util.DlsiteWorkNo

internal sealed interface LocalAlbumWorkBindingResult {
    data class Bound(val previous: AlbumEntity, val album: AlbumEntity) : LocalAlbumWorkBindingResult
    data object Conflict : LocalAlbumWorkBindingResult
    data object Missing : LocalAlbumWorkBindingResult
}

internal suspend fun AlbumDao.isLocalWorkNumberAvailable(albumId: Long, workno: String): Boolean =
    getAlbumsByNormalizedWorkIdOnce(workno).none { it.id != albumId }

internal suspend fun bindLocalAlbumWorkNumber(
    database: AppDatabase,
    albumId: Long,
    workno: String,
): LocalAlbumWorkBindingResult {
    val normalized = DlsiteWorkNo.normalizeWorkNo(workno)
    require(normalized.isNotBlank()) { "无效的作品编号" }
    return database.withTransaction {
        val albumDao = database.albumDao()
        val previous = albumDao.getAlbumById(albumId)
            ?: return@withTransaction LocalAlbumWorkBindingResult.Missing
        if (!albumDao.isLocalWorkNumberAvailable(albumId, normalized)) {
            return@withTransaction LocalAlbumWorkBindingResult.Conflict
        }

        val previousWorkno = DlsiteWorkNo.normalizeWorkNo(previous.rjCode)
            .ifBlank { DlsiteWorkNo.normalizeWorkNo(previous.workId) }
        val identityChanged = previousWorkno != normalized
        val updated = previous.copy(
            workId = resolveCloudSyncWorkId(previous.workId, normalized),
            rjCode = normalized,
            displayTitle = previous.displayTitle.takeUnless { identityChanged }.orEmpty(),
            coverUrl = previous.coverUrl.takeUnless { identityChanged }.orEmpty(),
            coverPath = previous.coverPath.takeUnless { identityChanged }.orEmpty(),
            coverThumbPath = previous.coverThumbPath.takeUnless { identityChanged }.orEmpty(),
            manualCoverPath = previous.manualCoverPath.takeUnless { identityChanged }.orEmpty(),
        )
        albumDao.updateAlbum(updated)
        LocalAlbumWorkBindingResult.Bound(previous = previous, album = updated)
    }
}
