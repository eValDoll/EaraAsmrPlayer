package com.asmr.player.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.asmr.player.data.local.db.entities.TrackPlaybackProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackPlaybackProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TrackPlaybackProgressEntity)

    @Query("SELECT * FROM track_playback_progress WHERE mediaId = :mediaId")
    suspend fun getByMediaId(mediaId: String): TrackPlaybackProgressEntity?

    @Query("SELECT * FROM track_playback_progress WHERE albumId IN (:albumIds) ORDER BY albumId ASC, updatedAt DESC")
    fun observeProgressForAlbums(albumIds: List<Long>): Flow<List<TrackPlaybackProgressEntity>>

    @Query(
        """
        SELECT
            p.albumId AS albumId,
            p.mediaId AS mediaId,
            p.positionMs AS positionMs,
            p.updatedAt AS updatedAt,
            t.title AS trackTitle
        FROM track_playback_progress p
        LEFT JOIN tracks t ON t.path = p.mediaId
        WHERE p.albumId IN (:albumIds)
        ORDER BY p.albumId ASC, p.updatedAt DESC
        """
    )
    fun observeLastPlayedRowsForAlbums(albumIds: List<Long>): Flow<List<AlbumLastPlayedRow>>

    @Query(
        """
        SELECT albumId AS albumId,
               SUM(CASE WHEN completed = 1 THEN 1 ELSE 0 END) AS completedCount
        FROM track_playback_progress
        WHERE albumId IN (:albumIds)
        GROUP BY albumId
        """
    )
    suspend fun getCompletedCountsForAlbums(albumIds: List<Long>): List<AlbumCompletedCountRow>

    @Query(
        """
        SELECT albumId AS albumId,
               SUM(CASE WHEN completed = 1 THEN 1 ELSE 0 END) AS completedCount
        FROM track_playback_progress
        WHERE albumId IN (:albumIds)
        GROUP BY albumId
        """
    )
    fun observeCompletedCountsForAlbums(albumIds: List<Long>): Flow<List<AlbumCompletedCountRow>>
}

data class AlbumCompletedCountRow(
    val albumId: Long,
    val completedCount: Long
)

data class AlbumLastPlayedRow(
    val albumId: Long?,
    val mediaId: String,
    val positionMs: Long,
    val updatedAt: Long,
    val trackTitle: String?
)
