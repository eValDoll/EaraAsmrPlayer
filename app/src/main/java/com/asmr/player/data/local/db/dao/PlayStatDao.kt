package com.asmr.player.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.asmr.player.data.local.db.entities.AlbumPlayStatEntity

@Dao
interface PlayStatDao {
    @Query("SELECT * FROM album_play_stats WHERE albumId = :albumId LIMIT 1")
    suspend fun getByAlbumId(albumId: Long): AlbumPlayStatEntity?

    @Query("UPDATE album_play_stats SET lastPlayedAt = :playedAt, playCount = playCount + 1 WHERE albumId = :albumId")
    suspend fun incrementPlay(albumId: Long, playedAt: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertInitial(stat: AlbumPlayStatEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stat: AlbumPlayStatEntity)

    @Query("DELETE FROM album_play_stats WHERE albumId = :albumId")
    suspend fun deleteByAlbumId(albumId: Long)

    @Transaction
    suspend fun markAlbumPlayed(albumId: Long, playedAt: Long) {
        val updated = incrementPlay(albumId, playedAt)
        if (updated == 0) {
            insertInitial(AlbumPlayStatEntity(albumId = albumId, lastPlayedAt = playedAt, playCount = 1))
        }
    }
}

