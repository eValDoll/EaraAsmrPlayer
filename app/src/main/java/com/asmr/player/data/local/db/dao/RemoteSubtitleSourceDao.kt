package com.asmr.player.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.asmr.player.data.local.db.entities.RemoteSubtitleSourceEntity

@Dao
interface RemoteSubtitleSourceDao {
    @Query("SELECT * FROM remote_subtitle_sources WHERE trackId = :trackId")
    suspend fun getSourcesForTrackOnce(trackId: Long): List<RemoteSubtitleSourceEntity>

    @Query("SELECT DISTINCT trackId FROM remote_subtitle_sources WHERE trackId IN (:trackIds)")
    suspend fun getTrackIdsWithRemoteSources(trackIds: List<Long>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sources: List<RemoteSubtitleSourceEntity>): List<Long>

    @Query("DELETE FROM remote_subtitle_sources WHERE trackId = :trackId")
    suspend fun deleteByTrackId(trackId: Long)

    @Query("DELETE FROM remote_subtitle_sources WHERE trackId IN (:trackIds)")
    suspend fun deleteByTrackIds(trackIds: List<Long>)

    @Query("DELETE FROM remote_subtitle_sources WHERE trackId IN (SELECT id FROM tracks WHERE albumId = :albumId)")
    suspend fun deleteByAlbumId(albumId: Long)
}
