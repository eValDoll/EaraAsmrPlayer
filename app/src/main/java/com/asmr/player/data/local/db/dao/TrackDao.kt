package com.asmr.player.data.local.db.dao

import androidx.room.*
import androidx.paging.PagingSource
import androidx.sqlite.db.SupportSQLiteQuery
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.data.local.db.entities.AlbumFtsEntity
import com.asmr.player.data.local.db.entities.AlbumTagEntity
import com.asmr.player.data.local.db.entities.TagEntity
import com.asmr.player.data.local.db.entities.TrackEntity
import com.asmr.player.data.local.db.entities.SubtitleEntity
import com.asmr.player.data.local.db.entities.TrackTagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks WHERE albumId = :albumId")
    fun getTracksForAlbum(albumId: Long): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE albumId = :albumId")
    suspend fun getTracksForAlbumOnce(albumId: Long): List<TrackEntity>

    @Query("SELECT COUNT(*) FROM tracks WHERE albumId = :albumId")
    suspend fun countTracksForAlbum(albumId: Long): Long

    @Query(
        """
        SELECT albumId AS albumId,
               COUNT(*) AS totalCount
        FROM tracks
        WHERE albumId IN (:albumIds)
        GROUP BY albumId
        """
    )
    fun observeTrackCountsForAlbums(albumIds: List<Long>): Flow<List<AlbumTrackCountRow>>

    @Query("SELECT id, path FROM tracks WHERE albumId = :albumId AND duration <= 0 ORDER BY id ASC LIMIT :limit")
    suspend fun getTracksNeedingDuration(albumId: Long, limit: Int): List<TrackIdPathRow>

    @Query("UPDATE tracks SET duration = :duration WHERE id = :trackId")
    suspend fun updateTrackDuration(trackId: Long, duration: Double)

    @Query("SELECT * FROM tracks WHERE path = :path LIMIT 1")
    suspend fun getTrackByPathOnce(path: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE id = :id LIMIT 1")
    suspend fun getTrackByIdOnce(id: Long): TrackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: TrackEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<TrackEntity>): List<Long>

    @Query("DELETE FROM subtitles WHERE trackId IN (SELECT id FROM tracks WHERE albumId = :albumId)")
    suspend fun deleteSubtitlesForAlbum(albumId: Long)

    @Query("DELETE FROM subtitles WHERE trackId = :trackId")
    suspend fun deleteSubtitlesForTrack(trackId: Long)

    @Query("DELETE FROM subtitles WHERE trackId IN (:trackIds)")
    suspend fun deleteSubtitlesForTracks(trackIds: List<Long>)

    @Query("DELETE FROM tracks WHERE albumId = :albumId")
    suspend fun deleteTracksForAlbum(albumId: Long)

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun deleteTrackById(id: Long)

    @Query("DELETE FROM tracks WHERE id IN (:ids)")
    suspend fun deleteTracksByIds(ids: List<Long>)

    @Query("UPDATE tracks SET albumId = :toAlbumId WHERE albumId = :fromAlbumId")
    suspend fun moveTracksToAlbum(fromAlbumId: Long, toAlbumId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubtitle(subtitle: SubtitleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubtitles(subtitles: List<SubtitleEntity>): List<Long>

    @Query("SELECT * FROM subtitles WHERE trackId = :trackId")
    suspend fun getSubtitlesForTrack(trackId: Long): List<SubtitleEntity>

    @Query("SELECT DISTINCT trackId FROM subtitles WHERE trackId IN (:trackIds)")
    suspend fun getTrackIdsWithSubtitles(trackIds: List<Long>): List<Long>

    @RawQuery(
        observedEntities = [
            TrackEntity::class,
            AlbumEntity::class,
            AlbumFtsEntity::class,
            TagEntity::class,
            AlbumTagEntity::class,
            TrackTagEntity::class
        ]
    )
    fun queryLibraryTracks(query: SupportSQLiteQuery): Flow<List<LibraryTrackRow>>

    @RawQuery(
        observedEntities = [
            TrackEntity::class,
            AlbumEntity::class,
            AlbumFtsEntity::class,
            TagEntity::class,
            AlbumTagEntity::class,
            TrackTagEntity::class
        ]
    )
    fun queryLibraryTracksPaged(query: SupportSQLiteQuery): PagingSource<Int, LibraryTrackRow>

    @RawQuery(
        observedEntities = [
            TrackEntity::class,
            AlbumEntity::class,
            AlbumFtsEntity::class,
            TagEntity::class,
            AlbumTagEntity::class,
            TrackTagEntity::class
        ]
    )
    fun queryLibraryTrackAlbumHeaders(query: SupportSQLiteQuery): Flow<List<LibraryTrackAlbumHeaderRow>>

    @RawQuery(
        observedEntities = [
            TrackEntity::class,
            AlbumEntity::class,
            AlbumFtsEntity::class,
            TagEntity::class,
            AlbumTagEntity::class,
            TrackTagEntity::class
        ]
    )
    fun queryLibraryTrackAlbumHeadersPaged(query: SupportSQLiteQuery): PagingSource<Int, LibraryTrackAlbumHeaderRow>
}

data class AlbumTrackCountRow(
    val albumId: Long,
    val totalCount: Long
)
