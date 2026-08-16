package com.asmr.player.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.asmr.player.data.local.db.entities.OnlineSavedResourceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OnlineSavedResourceDao {
    @Query("SELECT * FROM online_saved_resources WHERE albumId = :albumId ORDER BY relativePath ASC, id ASC")
    fun observeForAlbum(albumId: Long): Flow<List<OnlineSavedResourceEntity>>

    @Query("SELECT * FROM online_saved_resources WHERE albumId = :albumId ORDER BY relativePath ASC, id ASC")
    suspend fun getForAlbumOnce(albumId: Long): List<OnlineSavedResourceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(resources: List<OnlineSavedResourceEntity>): List<Long>

    @Query("DELETE FROM online_saved_resources WHERE albumId = :albumId")
    suspend fun deleteByAlbumId(albumId: Long)

    @Query("DELETE FROM online_saved_resources WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("UPDATE online_saved_resources SET albumId = :toAlbumId WHERE albumId = :fromAlbumId")
    suspend fun moveToAlbum(fromAlbumId: Long, toAlbumId: Long)
}
