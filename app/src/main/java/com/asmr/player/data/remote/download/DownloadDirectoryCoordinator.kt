package com.asmr.player.data.remote.download

import androidx.room.withTransaction
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.data.local.db.entities.TrackEntity
import com.asmr.player.util.isOnlineTrackPath
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ResolvedDownloadPaths(
    val destinationRoot: String,
    val targetDir: String,
    val taskRootDir: String,
    val albumRootDir: String,
)

sealed interface DownloadDirectoryChangeResult {
    data object Changed : DownloadDirectoryChangeResult
    data object Unchanged : DownloadDirectoryChangeResult
    data object BlockedByUnfinishedTasks : DownloadDirectoryChangeResult
    data object DirectoryUnavailable : DownloadDirectoryChangeResult
    data class Failed(val cause: Throwable) : DownloadDirectoryChangeResult
}

@Singleton
class DownloadDirectoryCoordinator @Inject constructor(
    private val database: AppDatabase,
    private val destinationStore: DownloadDestinationStore,
    private val storage: DownloadStorageGateway,
) {
    private val mutex = Mutex()

    suspend fun hasUnfinishedDownloads(): Boolean = database.downloadDao().countUnfinishedItems() > 0

    suspend fun <T> withDirectoryLock(block: suspend () -> T): T = mutex.withLock { block() }

    suspend fun currentDestination(): DownloadDestination = destinationStore.current()

    suspend fun resolveLegacyPaths(targetDir: String, taskRootDir: String): ResolvedDownloadPaths {
        val destination = destinationStore.current()
        if (destination is DownloadDestination.DocumentTree && !storage.hasPersistedWritePermission(destination.root)) {
            error("下载目录权限已失效")
        }

        val defaultRoot = destinationStore.defaultDestination().root
        val targetRelative = relativeToDefaultRoot(targetDir, defaultRoot)
        val taskRelative = relativeToDefaultRoot(taskRootDir, defaultRoot)
        val albumRelative = taskRelative.substringBefore('/').ifBlank {
            targetRelative.substringBefore('/').ifBlank { File(taskRootDir).name }
        }
        return if (destination is DownloadDestination.Default) {
            ResolvedDownloadPaths(
                destinationRoot = destination.root,
                targetDir = targetDir,
                taskRootDir = taskRootDir,
                albumRootDir = File(destination.root, albumRelative).absolutePath,
            )
        } else {
            ResolvedDownloadPaths(
                destinationRoot = destination.root,
                targetDir = storage.resolveDirectory(destination.root, targetRelative),
                taskRootDir = storage.resolveDirectory(destination.root, taskRelative),
                albumRootDir = storage.resolveDirectory(destination.root, albumRelative),
            )
        }
    }

    suspend fun changeDestination(newDestination: DownloadDestination): DownloadDirectoryChangeResult = mutex.withLock {
        if (database.downloadDao().countUnfinishedItems() > 0) {
            return@withLock DownloadDirectoryChangeResult.BlockedByUnfinishedTasks
        }
        if (newDestination is DownloadDestination.DocumentTree && !storage.hasPersistedWritePermission(newDestination.root)) {
            return@withLock DownloadDirectoryChangeResult.DirectoryUnavailable
        }

        val oldDestination = destinationStore.current()
        if (storage.stableIdentity(oldDestination.root) == storage.stableIdentity(newDestination.root)) {
            return@withLock DownloadDirectoryChangeResult.Unchanged
        }

        try {
            database.withTransaction {
                removeDatabaseRecordsForRoot(oldDestination.root)
                destinationStore.set(newDestination)
            }
            DownloadDirectoryChangeResult.Changed
        } catch (error: Throwable) {
            runCatching { destinationStore.set(oldDestination) }
            DownloadDirectoryChangeResult.Failed(error)
        }
    }

    internal suspend fun removeDatabaseRecordsForRoot(oldRoot: String) {
        val downloadDao = database.downloadDao()
        val oldRootIdentity = storage.stableIdentity(oldRoot)
        val oldTasks = downloadDao.getAllTasksOnce().filter { task ->
            task.destinationRoot.isNotBlank() && storage.stableIdentity(task.destinationRoot) == oldRootIdentity ||
                storage.isSameOrDescendant(task.rootDir, oldRoot) ||
                storage.isSameOrDescendant(task.albumRootDir, oldRoot)
        }
        val taskAlbumRoots = oldTasks.mapNotNull { task ->
            task.albumRootDir.trim().ifBlank { task.rootDir.trim() }.takeIf { it.isNotBlank() }
        }.distinctBy(storage::stableIdentity)
        oldTasks.forEach { task -> downloadDao.deleteTaskById(task.id) }

        val albumDao = database.albumDao()
        val trackDao = database.trackDao()
        val albums = albumDao.getAllAlbumsOnce()
        albums.forEach { album ->
            val tracks = trackDao.getTracksForAlbumOnce(album.id)
            val recordedDownloadPath = album.downloadPath
                ?.trim()
                ?.takeIf { it.isNotBlank() && storage.isSameOrDescendant(it, oldRoot) }
            val downloadAlbumRoots = (taskAlbumRoots + listOfNotNull(recordedDownloadPath))
                .filter { root ->
                    recordedDownloadPath?.let { pathsOverlap(root, it) } == true ||
                        tracks.any { track -> storage.isSameOrDescendant(track.path, root) }
                }
                .distinctBy(storage::stableIdentity)
            if (recordedDownloadPath == null && downloadAlbumRoots.isEmpty()) return@forEach

            val protectedImportRoot = album.localPath?.trim()?.takeIf { it.isNotBlank() }
            val removableRoots = downloadAlbumRoots.filterNot { root ->
                protectedImportRoot?.let { storage.stableIdentity(it) == storage.stableIdentity(root) } == true
            }
            val removedTracks = tracks.filter { track ->
                !isOnlineTrackPath(track.path) && removableRoots.any { root ->
                    storage.isSameOrDescendant(track.path, root)
                }
            }
            deleteTrackRecords(removedTracks)

            val remainingTracks = tracks.filterNot { track -> removedTracks.any { it.id == track.id } }
            val localPath = album.localPath
            val downloadPath = album.downloadPath?.takeUnless { storage.isSameOrDescendant(it, oldRoot) }
            val pathWasRemoved = removableRoots.any { root -> storage.isSameOrDescendant(album.path, root) }
            val retainedPath = album.path.takeUnless { pathWasRemoved }
                ?.takeIf { it.isNotBlank() }
                ?: localPath
                ?: remainingTracks.firstOrNull { !isOnlineTrackPath(it.path) }?.path
                .orEmpty()
            val hasSurvivingLocalSource = !localPath.isNullOrBlank() ||
                remainingTracks.any { !isOnlineTrackPath(it.path) }

            if (!hasSurvivingLocalSource) {
                deleteAlbumCompletely(album, remainingTracks)
            } else {
                val removedCover = removableRoots.any { root -> storage.isSameOrDescendant(album.coverPath, root) }
                val updated = album.copy(
                    path = retainedPath,
                    localPath = localPath,
                    downloadPath = downloadPath,
                    coverPath = album.coverPath.takeUnless { removedCover }.orEmpty(),
                    coverThumbPath = album.coverThumbPath
                        .takeUnless { removedCover || storage.isSameOrDescendant(it, oldRoot) }
                        .orEmpty(),
                    audioTrackCount = remainingTracks.count { !isOnlineTrackPath(it.path) },
                    audioTotalDuration = remainingTracks.sumOf { it.duration },
                    audioTotalSizeBytes = 0L,
                )
                albumDao.updateAlbum(updated)
                database.localTreeCacheDao().deleteByAlbum(album.id)
            }
        }
        database.subtitleTaskDao().deleteTasksWithoutItems()
    }

    private fun pathsOverlap(first: String, second: String): Boolean {
        return storage.isSameOrDescendant(first, second) || storage.isSameOrDescendant(second, first)
    }

    private suspend fun deleteTrackRecords(tracks: List<TrackEntity>) {
        if (tracks.isEmpty()) return
        val trackIds = tracks.map { it.id }
        val mediaIds = tracks.map { it.path }.filter { it.isNotBlank() }
        database.subtitleTaskDao().deleteItemsForTracks(trackIds)
        database.trackDao().deleteSubtitlesForTracks(trackIds)
        database.remoteSubtitleSourceDao().deleteByTrackIds(trackIds)
        database.trackTagDao().deleteTrackTagsByTrackIds(trackIds)
        database.trackPlaybackProgressDao().deleteByTrackIds(trackIds)
        database.playlistItemDao().deleteByTrackIds(trackIds)
        database.playlistDao().deleteTrackReferences(trackIds)
        if (mediaIds.isNotEmpty()) {
            database.albumGroupItemDao().deleteByMediaIds(mediaIds)
            mediaIds.forEach { mediaId ->
                database.manualLyricsSourceDao().deleteByCanonicalMediaId(mediaId)
                database.trackSliceDao().deleteByTrack(mediaId)
            }
        }
        database.trackDao().deleteTracksByIds(trackIds)
    }

    private suspend fun deleteAlbumCompletely(album: AlbumEntity, remainingTracks: List<TrackEntity>) {
        deleteTrackRecords(remainingTracks)
        database.trackPlaybackProgressDao().deleteByAlbumId(album.id)
        database.playlistItemDao().deleteByAlbumId(album.id)
        database.playStatDao().deleteByAlbumId(album.id)
        database.tagDao().deleteAlbumTagsByAlbumId(album.id)
        database.albumFtsDao().deleteByAlbumId(album.id)
        database.onlineSavedResourceDao().deleteByAlbumId(album.id)
        database.localTreeCacheDao().deleteByAlbum(album.id)
        database.albumDao().deleteAlbum(album)
    }

    private fun relativeToDefaultRoot(path: String, defaultRoot: String): String {
        val target = runCatching { File(path).canonicalFile }.getOrDefault(File(path).absoluteFile)
        val root = runCatching { File(defaultRoot).canonicalFile }.getOrDefault(File(defaultRoot).absoluteFile)
        return runCatching { target.relativeTo(root).path }
            .getOrDefault(target.name)
            .replace('\\', '/')
            .trim('/')
    }
}
