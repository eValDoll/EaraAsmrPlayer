package com.asmr.player.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.asmr.player.data.local.db.entities.TrackTagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackTagDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTrackTags(refs: List<TrackTagEntity>)

    @Query("DELETE FROM track_tag WHERE trackId = :trackId")
    suspend fun deleteTrackTagsByTrackId(trackId: Long)

    @Query("DELETE FROM track_tag WHERE trackId = :trackId AND source = :source")
    suspend fun deleteTrackTagsByTrackIdAndSource(trackId: Long, source: Int)

    @Query("DELETE FROM track_tag WHERE trackId = :trackId AND source != :keepSource")
    suspend fun deleteTrackTagsByTrackIdExceptSource(trackId: Long, keepSource: Int)

    @Query("DELETE FROM track_tag WHERE tagId = :tagId")
    suspend fun deleteTrackTagsByTagId(tagId: Long)

    @Query("UPDATE OR IGNORE track_tag SET tagId = :toTagId WHERE tagId = :fromTagId")
    suspend fun moveTrackTagsToAnotherTag(fromTagId: Long, toTagId: Long)

    @Query(
        """
        SELECT tt.trackId AS trackId,
               GROUP_CONCAT(t.name, ',') AS tagsCsv
        FROM track_tag tt
        JOIN tags t ON t.id = tt.tagId
        WHERE tt.source = :source
        GROUP BY tt.trackId
        """
    )
    fun getTrackTagsBySource(source: Int): Flow<List<TrackTagsCsv>>

    @Query(
        """
        SELECT GROUP_CONCAT(t.name, ',')
        FROM track_tag tt
        JOIN tags t ON t.id = tt.tagId
        WHERE tt.trackId = :trackId AND tt.source = :source
        """
    )
    suspend fun getTrackTagsCsvOnce(trackId: Long, source: Int): String?
}

data class TrackTagsCsv(
    val trackId: Long,
    val tagsCsv: String?
)
