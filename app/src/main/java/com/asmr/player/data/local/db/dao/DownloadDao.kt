package com.asmr.player.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.asmr.player.data.local.db.entities.DownloadItemEntity
import com.asmr.player.data.local.db.entities.DownloadTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Transaction
    @Query("SELECT * FROM download_tasks ORDER BY createdAt DESC")
    fun observeTasksWithItems(): Flow<List<DownloadTaskWithItems>>

    @Query("SELECT * FROM download_tasks WHERE id = :taskId LIMIT 1")
    suspend fun getTaskById(taskId: Long): DownloadTaskEntity?

    @Query("SELECT * FROM download_tasks WHERE taskKey = :taskKey LIMIT 1")
    suspend fun getTaskByKey(taskKey: String): DownloadTaskEntity?

    @Query("SELECT * FROM download_tasks WHERE rootDir = :rootDir LIMIT 1")
    suspend fun getTaskByRootDir(rootDir: String): DownloadTaskEntity?

    @Query("UPDATE download_tasks SET subtitle = :subtitle, updatedAt = :updatedAt WHERE id = :taskId")
    suspend fun updateTaskSubtitle(taskId: Long, subtitle: String, updatedAt: Long)

    @Query("SELECT * FROM download_items WHERE taskId = :taskId ORDER BY relativePath ASC")
    suspend fun getItemsForTask(taskId: Long): List<DownloadItemEntity>

    @Query("SELECT * FROM download_items WHERE workId = :workId LIMIT 1")
    suspend fun getItemByWorkId(workId: String): DownloadItemEntity?

    @Query("SELECT * FROM download_items WHERE filePath = :filePath LIMIT 1")
    suspend fun getItemByFilePath(filePath: String): DownloadItemEntity?

    @Query("SELECT COUNT(*) FROM download_items WHERE taskId = :taskId")
    suspend fun countItemsForTask(taskId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTask(task: DownloadTaskEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItem(item: DownloadItemEntity): Long

    @Query(
        "UPDATE download_items " +
            "SET state = :state, downloaded = :downloaded, total = :total, speed = :speed, updatedAt = :updatedAt " +
            "WHERE workId = :workId"
    )
    suspend fun updateItemProgress(
        workId: String,
        state: String,
        downloaded: Long,
        total: Long,
        speed: Long,
        updatedAt: Long
    )

    @Query("UPDATE download_items SET state = :state, updatedAt = :updatedAt WHERE workId = :workId")
    suspend fun updateItemState(workId: String, state: String, updatedAt: Long)

    @Query(
        "UPDATE download_items " +
            "SET workId = :newWorkId, state = :state, downloaded = :downloaded, speed = 0, updatedAt = :updatedAt " +
            "WHERE workId = :oldWorkId"
    )
    suspend fun replaceWorkIdForResume(
        oldWorkId: String,
        newWorkId: String,
        state: String,
        downloaded: Long,
        updatedAt: Long
    )

    @Query("DELETE FROM download_items WHERE workId = :workId")
    suspend fun deleteItemByWorkId(workId: String)

    @Query("DELETE FROM download_items WHERE filePath = :filePath")
    suspend fun deleteItemsByFilePath(filePath: String)

    @Query("DELETE FROM download_items WHERE taskId = :taskId")
    suspend fun deleteItemsForTask(taskId: Long)

    @Query("DELETE FROM download_tasks WHERE id = :taskId")
    suspend fun deleteTaskById(taskId: Long)
}
