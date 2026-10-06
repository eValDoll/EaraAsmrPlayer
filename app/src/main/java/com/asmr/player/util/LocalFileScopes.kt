package com.asmr.player.util

import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import java.io.File
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.data.local.db.entities.DownloadTaskEntity
import com.asmr.player.data.remote.download.DownloadStorageGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class LocalFileScopes(
    private val database: AppDatabase,
    private val storage: DownloadStorageGateway,
) {
    fun create(workNos: List<String> = emptyList(), roots: List<String> = emptyList(), albumId: Long = 0L): LocalFileScope {
        val identities = workNos.mapNotNull { DlsiteWorkNo.extractWorkNo(it).uppercase().takeIf(String::isNotBlank) }
            .mapTo(mutableSetOf()) { "work:$it" }
        if (albumId > 0L) identities += "album:$albumId"
        val paths = roots.filter { it.isNotBlank() && !isOnlineTrackPath(it) && !it.startsWith("web://") }
            .flatMapTo(mutableSetOf(), ::pathIdentities)
        return LocalFileScope(identities, paths)
    }

    private fun pathIdentities(reference: String): Set<String> {
        val uri = Uri.parse(reference)
        if (uri.scheme != "content") {
            val path = if (uri.scheme == "file") uri.path.orEmpty() else reference
            return setOf(storage.stableIdentity(path).replace('\\', '/').trimEnd('/'))
        }
        val stable = storage.stableIdentity(reference).trimEnd('/')
        if (uri.authority != "com.android.externalstorage.documents") {
            // 不透明文档 ID 无法判断父子关系，同一提供器保守串行。
            return setOf(stable, "provider:${uri.authority}")
        }
        val id = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
            ?: runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
            ?: return setOf(stable, "provider:${uri.authority}")
        val path = if (id.startsWith("raw:")) id.removePrefix("raw:") else {
            val volume = id.substringBefore(':')
            val root = if (volume.equals("primary", true)) Environment.getExternalStorageDirectory().path else "/storage/$volume"
            File(root, id.substringAfter(':', "")).path
        }
        return setOf(stable, storage.stableIdentity(path).replace('\\', '/').trimEnd('/'))
    }

    fun album(album: AlbumEntity): LocalFileScope = create(
        listOf(album.rjCode, album.workId), listOfNotNull(album.path, album.localPath, album.downloadPath), album.id
    )

    suspend fun album(albumId: Long): LocalFileScope = withContext(Dispatchers.IO) {
        database.albumDao().getAlbumById(albumId)?.let(::album) ?: create(albumId = albumId)
    }

    suspend fun tracks(trackIds: List<Long>): LocalFileScope = withContext(Dispatchers.IO) {
        database.trackDao().getTracksByIdsOnce(trackIds).map { it.albumId }.distinct()
            .fold(LocalFileScope()) { scope, albumId -> scope + album(albumId) }
    }

    fun download(task: DownloadTaskEntity): LocalFileScope = create(
        listOf(task.albumRjCode, task.albumWorkId, task.title), listOf(task.albumRootDir, task.rootDir)
    )

    suspend fun hasDownloads(scope: LocalFileScope, inFlightOnly: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        database.downloadDao().getBlockingTasks(inFlightOnly).any { scope.overlaps(download(it)) }
    }

    suspend fun hasSubtitles(scope: LocalFileScope): Boolean = withContext(Dispatchers.IO) {
        val ids = database.subtitleTaskDao().getAllItems().map { it.trackId }.distinct()
        ids.isNotEmpty() && scope.overlaps(tracks(ids))
    }
}
