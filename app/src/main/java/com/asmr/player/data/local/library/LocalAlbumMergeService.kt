package com.asmr.player.data.local.library

import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.data.local.db.entities.AlbumFtsEntity
import com.asmr.player.data.local.db.entities.TrackEntity
import com.asmr.player.data.remote.download.DownloadStorageGateway
import com.asmr.player.util.DlsiteWorkNo
import javax.inject.Inject
import javax.inject.Singleton

/** Keeps every physical source for one normalized work number on a single album row. */
@Singleton
class LocalAlbumMergeService @Inject constructor(
    private val database: AppDatabase,
    private val storage: DownloadStorageGateway,
) {
    suspend fun resolveAndMerge(
        rj: String,
        fallbackPath: String,
        fallbackTitle: String,
        localPath: String?,
        downloadPath: String?,
    ): AlbumEntity? {
        val normalizedRj = DlsiteWorkNo.extractWorkNo(rj).ifBlank { rj.trim().uppercase() }
        if (normalizedRj.isNotBlank()) {
            val matches = database.albumDao().getAlbumsByNormalizedWorkIdOnce(normalizedRj)
            if (matches.isNotEmpty()) {
                val canonical = matches
                    .sortedWith(compareBy<AlbumEntity> { it.localPath.isNullOrBlank() }.thenBy { it.id })
                    .first()
                matches.filter { it.id != canonical.id }.forEach { other ->
                    migrateAlbumAssociations(other.id, canonical.id)
                    database.trackDao().moveTracksToAlbum(other.id, canonical.id)
                    database.onlineSavedResourceDao().moveToAlbum(other.id, canonical.id)
                    deleteRedundantAlbum(other)
                }
                mergeDuplicatePhysicalTracks(canonical.id)

                val mergedLocalPath = sequenceOf(canonical.localPath, localPath)
                    .plus(matches.asSequence().map { it.localPath })
                    .firstOrNull { !it.isNullOrBlank() }
                val mergedDownloadPath = sequenceOf(canonical.downloadPath, downloadPath)
                    .plus(matches.asSequence().map { it.downloadPath })
                    .firstOrNull { !it.isNullOrBlank() }
                val merged = canonical.copy(
                    title = canonical.title.ifBlank { fallbackTitle },
                    path = mergedLocalPath ?: mergedDownloadPath ?: canonical.path.ifBlank { fallbackPath },
                    localPath = mergedLocalPath,
                    downloadPath = mergedDownloadPath,
                    workId = canonical.workId.ifBlank { normalizedRj },
                    rjCode = normalizedRj,
                )
                database.albumDao().updateAlbum(merged)
                upsertFts(merged)
                return merged
            }
        }

        database.albumDao().getAlbumByPathOnce(fallbackPath)?.let { return it }
        if (fallbackTitle.isNotBlank() && fallbackTitle != "album") {
            return database.albumDao().getAllAlbumsOnce().firstOrNull { it.title == fallbackTitle }
        }
        return null
    }

    suspend fun deduplicateTracks(albumId: Long) {
        mergeDuplicatePhysicalTracks(albumId)
    }

    private suspend fun migrateAlbumAssociations(fromAlbumId: Long, toAlbumId: Long) {
        val tagDao = database.tagDao()
        tagDao.insertAlbumTags(
            tagDao.getAlbumTagsOnce(fromAlbumId).map { tag -> tag.copy(albumId = toAlbumId) },
        )
        tagDao.deleteAlbumTagsByAlbumId(fromAlbumId)
        database.trackPlaybackProgressDao().moveToAlbum(fromAlbumId, toAlbumId)
        database.listeningSessionDao().moveToAlbum(fromAlbumId, toAlbumId)
        database.playlistItemDao().moveToAlbum(fromAlbumId, toAlbumId)

        val playStatDao = database.playStatDao()
        val fromStat = playStatDao.getByAlbumId(fromAlbumId)
        if (fromStat != null) {
            val current = playStatDao.getByAlbumId(toAlbumId)
            playStatDao.upsert(
                fromStat.copy(
                    albumId = toAlbumId,
                    lastPlayedAt = maxOf(current?.lastPlayedAt ?: 0L, fromStat.lastPlayedAt),
                    playCount = (current?.playCount ?: 0L) + fromStat.playCount,
                ),
            )
            playStatDao.deleteByAlbumId(fromAlbumId)
        }
        database.localTreeCacheDao().deleteByAlbum(fromAlbumId)
        database.albumFtsDao().deleteByAlbumId(fromAlbumId)
    }

    private suspend fun mergeDuplicatePhysicalTracks(albumId: Long) {
        val trackDao = database.trackDao()
        trackDao.getTracksForAlbumOrderedOnce(albumId)
            .groupBy { track -> storage.stableIdentity(track.path) }
            .values
            .filter { it.size > 1 }
            .forEach { duplicates ->
                val keep = duplicates.minBy { it.id }
                duplicates.filter { it.id != keep.id }.forEach { duplicate ->
                    migrateTrackAssociations(duplicate, keep)
                    trackDao.deleteTrackById(duplicate.id)
                }
            }
    }

    private suspend fun migrateTrackAssociations(from: TrackEntity, to: TrackEntity) {
        val trackDao = database.trackDao()
        val existingSubtitles = trackDao.getSubtitlesForTrack(to.id)
            .map { listOf(it.startMs, it.endMs, it.text, it.japaneseText) }
            .toHashSet()
        val subtitles = trackDao.getSubtitlesForTrack(from.id)
            .filter { listOf(it.startMs, it.endMs, it.text, it.japaneseText) !in existingSubtitles }
            .map { it.copy(id = 0L, trackId = to.id) }
        if (subtitles.isNotEmpty()) trackDao.insertSubtitles(subtitles)

        val remoteDao = database.remoteSubtitleSourceDao()
        val existingRemote = remoteDao.getSourcesForTrackOnce(to.id)
            .map { Triple(it.url, it.language, it.ext) }
            .toHashSet()
        val remoteSources = remoteDao.getSourcesForTrackOnce(from.id)
            .filter { Triple(it.url, it.language, it.ext) !in existingRemote }
            .map { it.copy(id = 0L, trackId = to.id) }
        if (remoteSources.isNotEmpty()) remoteDao.insertAll(remoteSources)

        val trackTagDao = database.trackTagDao()
        trackTagDao.insertTrackTags(
            trackTagDao.getTrackTagsForTrack(from.id).map { tag -> tag.copy(trackId = to.id) },
        )
        database.trackPlaybackProgressDao().moveToTrack(from.id, to.id, albumId = to.albumId)
        database.playlistItemDao().moveToTrack(from.id, to.id, albumId = to.albumId)
        database.playlistDao().copyTrackReferences(from.id, to.id)
        database.playlistDao().deleteTrackReferences(from.id)

        val subtitleTaskDao = database.subtitleTaskDao()
        val fromTask = subtitleTaskDao.getItemForTrack(from.id)
        if (fromTask != null && subtitleTaskDao.getItemForTrack(to.id) == null) {
            subtitleTaskDao.updateItem(fromTask.copy(trackId = to.id, trackPath = to.path))
        }

        trackDao.deleteSubtitlesForTrack(from.id)
        remoteDao.deleteByTrackId(from.id)
        trackTagDao.deleteTrackTagsByTrackId(from.id)
    }

    private suspend fun deleteRedundantAlbum(album: AlbumEntity) {
        database.onlineSavedResourceDao().deleteByAlbumId(album.id)
        database.albumFtsDao().deleteByAlbumId(album.id)
        database.localTreeCacheDao().deleteByAlbum(album.id)
        database.albumDao().deleteAlbum(album)
    }

    private suspend fun upsertFts(album: AlbumEntity) {
        database.albumFtsDao().upsert(
            listOf(
                AlbumFtsEntity(
                    albumId = album.id,
                    title = album.title,
                    circle = album.circle,
                    cv = album.cv,
                    rjCode = album.rjCode,
                    workId = album.workId,
                    tagsToken = album.tags.replace(',', ' ').trim(),
                ),
            ),
        )
    }
}
