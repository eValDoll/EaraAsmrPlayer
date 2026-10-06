package com.asmr.player.util

import com.asmr.player.data.local.db.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged

/** 下载结束包含入库；等待数据库状态时不占用文件锁，允许下载和取消继续收尾。 */
internal suspend fun <T> AppDatabase.withSubtitleFileAccess(
    scopes: LocalFileScopes,
    scope: LocalFileScope,
    resource: String? = null,
    operation: LocalFileOperation = LocalFileOperation.SUBTITLE,
    block: suspend () -> T,
): T {
    while (true) {
        downloadDao().observeBlockingTasks().distinctUntilChanged().first { tasks ->
            tasks.none { scope.overlaps(scopes.download(it)) }
        }
        var result: Result<T>? = null
        LocalFileOperationCoordinator.shared.withOperation(operation, resource, scope) {
            if (!scopes.hasDownloads(scope)) {
                result = Result.success(block())
            }
        }
        result?.let { return it.getOrThrow() }
    }
}

internal suspend fun AppDatabase.tryBeginFileMutation(
    scopes: LocalFileScopes,
    scope: LocalFileScope = LocalFileScope.Global,
    allowSubtitleCancellation: Boolean = false,
    allowStoppedDownloads: Boolean = false,
): LocalFileOperationCoordinator.Lease? {
    val lease = LocalFileOperationCoordinator.shared.tryAcquire(LocalFileOperation.DELETE, scope = scope) ?: return null
    try {
        if (scopes.hasDownloads(scope, inFlightOnly = allowStoppedDownloads) || !allowSubtitleCancellation && scopes.hasSubtitles(scope)) {
            lease.close()
            return null
        }
        return lease
    } catch (error: Throwable) {
        lease.close()
        throw error
    }
}
