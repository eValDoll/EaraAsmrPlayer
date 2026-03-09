package com.asmr.player.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.asmr.player.data.local.db.entities.LocalTreeCacheEntity

@Dao
interface LocalTreeCacheDao {
    @Query("SELECT * FROM local_tree_cache WHERE albumId = :albumId AND cacheKey = :cacheKey LIMIT 1")
    suspend fun getByAlbumAndKey(albumId: Long, cacheKey: String): LocalTreeCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LocalTreeCacheEntity): Long

    @Query("DELETE FROM local_tree_cache WHERE albumId = :albumId")
    suspend fun deleteByAlbum(albumId: Long)
}

