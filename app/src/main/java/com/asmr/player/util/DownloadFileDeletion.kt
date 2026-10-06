package com.asmr.player.util

import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.entities.DownloadItemEntity
import com.asmr.player.data.local.db.entities.DownloadTaskEntity

/** 停止目标下载后等待写入退出；其他文件下载不阻止单文件删除。 */
internal suspend fun AppDatabase.withDownloadDeletion(
    scopes: LocalFileScopes,
    task: DownloadTaskEntity,
    item: DownloadItemEntity? = null,
    stopDownload: suspend () -> Unit,
    delete: suspend () -> Unit,
): Boolean {
    val scope = item?.let(scopes::downloadFile) ?: scopes.download(task)
    suspend fun isBlocked(): Boolean = if (item == null) {
        scopes.hasSubtitles(scope) || downloadDao().getBlockingTasks(inFlightOnly = true)
            .any { it.id != task.id && scope.overlaps(scopes.download(it)) }
    } else {
        scopes.hasSubtitleFiles(scope) || downloadDao().getBlockingItems()
            .any { it.workId != item.workId && scope.overlaps(scopes.downloadFile(it)) }
    }
    if (isBlocked()) return false
    stopDownload()
    return LocalFileOperationCoordinator.shared.withOperation(LocalFileOperation.DELETE, scope = scope) {
        if (isBlocked()) false else {
            delete()
            true
        }
    }
}
