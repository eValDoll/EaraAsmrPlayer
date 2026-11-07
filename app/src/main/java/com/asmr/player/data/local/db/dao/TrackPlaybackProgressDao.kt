package com.asmr.player.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.asmr.player.data.local.db.entities.TrackPlaybackProgressEntity

@Dao
interface TrackPlaybackProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TrackPlaybackProgressEntity)

    @Query("SELECT * FROM track_playback_progress WHERE mediaId = :mediaId")
    suspend fun getByMediaId(mediaId: String): TrackPlaybackProgressEntity?

    @Query("UPDATE track_playback_progress SET albumId = :toAlbumId WHERE albumId = :fromAlbumId")
    suspend fun moveToAlbum(fromAlbumId: Long, toAlbumId: Long)

    @Query("UPDATE track_playback_progress SET trackId = :toTrackId, albumId = :albumId WHERE trackId = :fromTrackId")
    suspend fun moveToTrack(fromTrackId: Long, toTrackId: Long, albumId: Long)

    @Query("DELETE FROM track_playback_progress WHERE trackId IN (:trackIds)")
    suspend fun deleteByTrackIds(trackIds: List<Long>)

    @Query("DELETE FROM track_playback_progress WHERE albumId = :albumId")
    suspend fun deleteByAlbumId(albumId: Long)
}
