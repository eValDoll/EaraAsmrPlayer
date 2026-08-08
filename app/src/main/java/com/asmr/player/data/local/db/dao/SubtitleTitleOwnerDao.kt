package com.asmr.player.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.asmr.player.data.local.db.entities.SubtitleTitleOwnerEntity

@Dao
interface SubtitleTitleOwnerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(owners: List<SubtitleTitleOwnerEntity>)

    @Query("SELECT * FROM subtitle_title_owners WHERE taskId = :taskId")
    suspend fun getByTask(taskId: String): List<SubtitleTitleOwnerEntity>

    @Query("SELECT * FROM subtitle_title_owners WHERE displayTitle = '' ORDER BY createdAt ASC")
    suspend fun getPendingOwners(): List<SubtitleTitleOwnerEntity>

    @Query("SELECT DISTINCT taskId FROM subtitle_title_owners WHERE displayTitle = ''")
    suspend fun getPendingTaskIds(): List<String>

    @Query(
        "UPDATE subtitle_title_owners SET displayTitle = :displayTitle " +
            "WHERE taskId = :taskId AND kind = :kind AND targetId = :targetId"
    )
    suspend fun updateDisplayTitle(taskId: String, kind: String, targetId: Long, displayTitle: String)

    @Query("DELETE FROM subtitle_title_owners WHERE taskId = :taskId AND displayTitle = ''")
    suspend fun deletePendingForTask(taskId: String)
}
