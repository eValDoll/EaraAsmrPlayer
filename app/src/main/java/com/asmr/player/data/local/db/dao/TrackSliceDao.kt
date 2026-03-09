package com.asmr.player.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.asmr.player.data.local.db.entities.TrackSliceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackSliceDao {
    @Query(
        """
        SELECT *
        FROM track_slices
        WHERE trackMediaId = :trackMediaId
        ORDER BY startMs ASC, endMs ASC, id ASC
        """
    )
    fun observeSlices(trackMediaId: String): Flow<List<TrackSliceEntity>>

    @Query(
        """
        SELECT *
        FROM track_slices
        WHERE trackMediaId = :trackMediaId
        ORDER BY startMs ASC, endMs ASC, id ASC
        """
    )
    suspend fun getSlicesOnce(trackMediaId: String): List<TrackSliceEntity>

    @Query("SELECT * FROM track_slices WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TrackSliceEntity?

    @Query(
        """
        SELECT EXISTS(
            SELECT 1
            FROM track_slices
            WHERE trackMediaId = :trackMediaId
              AND id != :excludeId
              AND startMs < :endMs
              AND endMs > :startMs
        )
        """
    )
    suspend fun hasOverlap(trackMediaId: String, startMs: Long, endMs: Long, excludeId: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(slice: TrackSliceEntity): Long

    @Query(
        """
        UPDATE track_slices
        SET startMs = :startMs, endMs = :endMs, updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun updateRange(id: Long, startMs: Long, endMs: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM track_slices WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM track_slices WHERE trackMediaId = :trackMediaId")
    suspend fun deleteByTrack(trackMediaId: String)
}
