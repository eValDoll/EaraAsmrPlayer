package com.asmr.player.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.asmr.player.data.local.db.entities.AlbumFtsEntity

@Dao
interface AlbumFtsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entries: List<AlbumFtsEntity>)

    @Query("DELETE FROM album_fts WHERE rowid = :albumId")
    suspend fun deleteByAlbumId(albumId: Long)

    @Query("DELETE FROM album_fts")
    suspend fun clear()
}

