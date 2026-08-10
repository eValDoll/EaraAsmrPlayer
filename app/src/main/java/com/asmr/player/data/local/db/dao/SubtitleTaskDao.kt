package com.asmr.player.data.local.db.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import com.asmr.player.data.local.db.entities.SubtitleCommittedCaptionEntity
import com.asmr.player.data.local.db.entities.SubtitleFallbackCaptionEntity
import com.asmr.player.data.local.db.entities.SubtitleTaskEntity
import com.asmr.player.data.local.db.entities.SubtitleTaskItemEntity
import com.asmr.player.data.local.db.entities.SubtitleTaskSnapshotEntity
import com.asmr.player.data.local.db.entities.SubtitleTranscriptionChunkEntity
import com.asmr.player.data.local.db.entities.SubtitleTranslationSourceEntity
import kotlinx.coroutines.flow.Flow

data class SubtitleTaskWithItems(
    @Embedded val task: SubtitleTaskEntity,
    @Relation(parentColumn = "id", entityColumn = "taskId")
    val items: List<SubtitleTaskItemEntity>
)

@Dao
interface SubtitleTaskDao {
    @Transaction
    @Query("SELECT * FROM subtitle_tasks ORDER BY createdAt DESC")
    fun observeTasks(): Flow<List<SubtitleTaskWithItems>>

    @Query("SELECT * FROM subtitle_tasks WHERE id = :taskId LIMIT 1")
    suspend fun getTask(taskId: String): SubtitleTaskEntity?

    @Query("SELECT * FROM subtitle_task_items WHERE id = :itemId LIMIT 1")
    suspend fun getItem(itemId: String): SubtitleTaskItemEntity?

    @Query("SELECT * FROM subtitle_task_items WHERE trackId = :trackId LIMIT 1")
    suspend fun getItemForTrack(trackId: Long): SubtitleTaskItemEntity?

    @Query("SELECT * FROM subtitle_task_items WHERE taskId = :taskId ORDER BY queueSequence")
    suspend fun getItemsForTask(taskId: String): List<SubtitleTaskItemEntity>

    @Query("SELECT * FROM subtitle_task_items WHERE trackId IN (:trackIds)")
    suspend fun getItemsForTracks(trackIds: List<Long>): List<SubtitleTaskItemEntity>

    @Query("SELECT * FROM subtitle_task_items ORDER BY queueSequence")
    suspend fun getAllItems(): List<SubtitleTaskItemEntity>

    @Query("SELECT COUNT(*) FROM subtitle_task_items")
    suspend fun countAllItems(): Int

    @Query(
        "SELECT * FROM subtitle_task_items WHERE state = 'QUEUED_TRANSCRIPTION' " +
            "ORDER BY queueSequence LIMIT 1"
    )
    suspend fun getNextTranscription(): SubtitleTaskItemEntity?

    @Query(
        "SELECT * FROM subtitle_task_items WHERE state IN " +
            "('QUEUED_TRANSLATION','WAITING_SLOT','WAITING_NETWORK','RETRY_WAIT') " +
            "AND nextAttemptAt <= :now ORDER BY queueSequence LIMIT :limit"
    )
    suspend fun getTranslationCandidates(now: Long, limit: Int): List<SubtitleTaskItemEntity>

    @Query("SELECT COUNT(*) FROM subtitle_task_items WHERE state NOT IN ('SUCCEEDED','CANCELED','FAILED','PAUSED','INTERRUPTED')")
    suspend fun countRunnableItems(): Int

    @Query("SELECT COALESCE(MAX(queueSequence), 0) FROM subtitle_task_items")
    suspend fun maxQueueSequence(): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTask(task: SubtitleTaskEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertItems(items: List<SubtitleTaskItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshots(snapshots: List<SubtitleTaskSnapshotEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChunk(chunk: SubtitleTranscriptionChunkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSources(sources: List<SubtitleTranslationSourceEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFallbackCaptions(captions: List<SubtitleFallbackCaptionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCommittedCaptions(captions: List<SubtitleCommittedCaptionEntity>)

    @Update
    suspend fun updateTask(task: SubtitleTaskEntity)

    @Update
    suspend fun updateItem(item: SubtitleTaskItemEntity)

    @Query("SELECT * FROM subtitle_task_snapshots WHERE itemId = :itemId ORDER BY captionIndex")
    suspend fun getSnapshots(itemId: String): List<SubtitleTaskSnapshotEntity>

    @Query("SELECT * FROM subtitle_transcription_chunks WHERE itemId = :itemId ORDER BY chunkIndex")
    suspend fun getChunks(itemId: String): List<SubtitleTranscriptionChunkEntity>

    @Query("SELECT * FROM subtitle_translation_sources WHERE itemId = :itemId ORDER BY sourceIndex")
    suspend fun getSources(itemId: String): List<SubtitleTranslationSourceEntity>

    @Query("SELECT * FROM subtitle_fallback_captions WHERE itemId = :itemId ORDER BY captionIndex")
    suspend fun getFallbackCaptions(itemId: String): List<SubtitleFallbackCaptionEntity>

    @Query("SELECT * FROM subtitle_committed_captions WHERE itemId = :itemId ORDER BY captionIndex")
    suspend fun getCommittedCaptions(itemId: String): List<SubtitleCommittedCaptionEntity>

    @Query("DELETE FROM subtitle_translation_sources WHERE itemId = :itemId")
    suspend fun deleteSources(itemId: String)

    @Query("DELETE FROM subtitle_fallback_captions WHERE itemId = :itemId")
    suspend fun deleteFallbackCaptions(itemId: String)

    @Query("DELETE FROM subtitle_task_items WHERE id = :itemId")
    suspend fun deleteItem(itemId: String)

    @Query("DELETE FROM subtitle_tasks WHERE id = :taskId")
    suspend fun deleteTask(taskId: String)

    @Query("SELECT COUNT(*) FROM subtitle_task_items WHERE taskId = :taskId")
    suspend fun countItems(taskId: String): Int

    @Query(
        "UPDATE subtitle_tasks SET state = 'INTERRUPTED', updatedAt = :now WHERE id IN " +
            "(SELECT DISTINCT taskId FROM subtitle_task_items WHERE state = 'INTERRUPTED')"
    )
    suspend fun markInterruptedTasks(now: Long)

    @Query(
        "UPDATE subtitle_task_items SET state = 'PAUSED', updatedAt = :now " +
            "WHERE state = 'PAUSE_REQUESTED'"
    )
    suspend fun settlePauseRequestsAfterInterruption(now: Long): Int

    @Query(
        "UPDATE subtitle_task_items SET suspendedFromState = CASE WHEN suspendedFromState = '' THEN state ELSE suspendedFromState END, " +
            "state = 'INTERRUPTED', " +
            "errorMessage = :message, updatedAt = :now WHERE state NOT IN " +
            "('SUCCEEDED','CANCELED','FAILED','PAUSED','INTERRUPTED','CANCEL_REQUESTED')"
    )
    suspend fun interruptAllIncompleteItems(now: Long, message: String): Int
}
