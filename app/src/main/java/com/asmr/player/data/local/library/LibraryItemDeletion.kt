package com.asmr.player.data.local.library

import android.net.Uri
import androidx.room.withTransaction
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.util.AlbumWorkNo

/** 用于真正移除库内容；重建索引或合并记录时保留原有关联。 */
suspend fun AppDatabase.deleteLibraryTracks(trackIds: List<Long>) {
    val validTrackIds = trackIds.filter { it > 0L }.distinct()
    if (validTrackIds.isEmpty()) return
    withTransaction {
        validTrackIds.chunked(400).forEach { ids ->
            val paths = trackDao().getTracksByIdsOnce(ids).map { it.path }.filter(String::isNotBlank)
            val mediaIds = paths.flatMap { path ->
                if (path.startsWith("/")) {
                    listOf(path, Uri.Builder().scheme("file").authority("").path(path).build().toString())
                } else {
                    listOf(path)
                }
            }.distinct()
            playlistItemDao().deleteByTrackIds(ids)
            playlistDao().deleteTrackReferences(ids)
            mediaIds.chunked(400).forEach { mediaIdBatch ->
                playlistItemDao().deleteByMediaIds(mediaIdBatch)
                albumGroupItemDao().deleteByMediaIds(mediaIdBatch)
            }
            subtitleTaskDao().deleteItemsForTracks(ids)
            trackDao().deleteTracksByIds(ids)
        }
        subtitleTaskDao().deleteTasksWithoutItems()
    }
}

suspend fun AppDatabase.deleteLibraryAlbum(album: AlbumEntity) {
    if (album.id <= 0L) return
    withTransaction {
        val workNos = listOf(album.rjCode, album.workId).map { AlbumWorkNo.normalizeWorkNo(it) }.filter(String::isNotBlank).distinct()
        playlistItemDao().deleteByAlbumId(album.id)
        if (workNos.isNotEmpty()) playlistItemDao().deleteUnboundItemsByWorkNos(workNos)
        deleteLibraryTracks(trackDao().getTracksForAlbumOnce(album.id).map { it.id })
        albumDao().deleteAlbum(album)
    }
}
