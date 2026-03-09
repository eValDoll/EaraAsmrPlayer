package com.asmr.player.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.asmr.player.data.local.db.entities.AlbumPlayStatEntity

@Dao
interface PlayStatDao {
    @Query("UPDATE album_play_stats SET lastPlayedAt = :playedAt, playCount = playCount + 1 WHERE albumId = :albumId")
    suspend fun incrementPlay(albumId: Long, playedAt: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertInitial(stat: AlbumPlayStatEntity)

    @Transaction
    suspend fun markAlbumPlayed(albumId: Long, playedAt: Long) {
        val updated = incrementPlay(albumId, playedAt)
        if (updated == 0) {
            insertInitial(AlbumPlayStatEntity(albumId = albumId, lastPlayedAt = playedAt, playCount = 1))
        }
    }
}

