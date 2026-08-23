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

    @Query("SELECT * FROM download_tasks ORDER BY createdAt DESC")
    suspend fun getAllTasksOnce(): List<DownloadTaskEntity>

    @Query(
        """
        SELECT d.id AS taskId,
               a.coverUrl AS coverUrl,
               a.coverPath AS coverPath,
               a.coverThumbPath AS coverThumbPath
        FROM download_tasks d
        INNER JOIN albums a ON
            (
                TRIM(d.albumRjCode) != '' AND
                (
                    a.rjCode = TRIM(d.albumRjCode) COLLATE NOCASE OR
                    a.workId = TRIM(d.albumRjCode) COLLATE NOCASE
                )
            ) OR (
                TRIM(d.albumWorkId) != '' AND
                (
                    a.rjCode = TRIM(d.albumWorkId) COLLATE NOCASE OR
                    a.workId = TRIM(d.albumWorkId) COLLATE NOCASE
                )
            ) OR (
                TRIM(d.albumRjCode) = '' AND
                TRIM(d.albumWorkId) = '' AND
                (
                    a.rjCode = TRIM(d.title) COLLATE NOCASE OR
                    a.workId = TRIM(d.title) COLLATE NOCASE
                )
            )
        ORDER BY a.id DESC
        """
    )
    fun observeTaskAlbumCovers(): Flow<List<DownloadTaskAlbumCoverRow>>

    @Query("SELECT * FROM download_tasks WHERE id = :taskId LIMIT 1")
    suspend fun getTaskById(taskId: Long): DownloadTaskEntity?

    @Query("SELECT * FROM download_tasks WHERE taskKey = :taskKey LIMIT 1")
    suspend fun getTaskByKey(taskKey: String): DownloadTaskEntity?

    @Query("SELECT * FROM download_tasks WHERE rootDir = :rootDir LIMIT 1")
    suspend fun getTaskByRootDir(rootDir: String): DownloadTaskEntity?

    @Query("UPDATE download_tasks SET subtitle = :subtitle, updatedAt = :updatedAt WHERE id = :taskId")
    suspend fun updateTaskSubtitle(taskId: Long, subtitle: String, updatedAt: Long)

    @Query(
        "UPDATE download_tasks SET " +
            "subtitle = :subtitle, " +
            "albumTitle = :albumTitle, " +
            "albumCircle = :albumCircle, " +
            "albumCv = :albumCv, " +
            "albumTagsCsv = :albumTagsCsv, " +
            "albumCoverUrl = :albumCoverUrl, " +
            "albumDescription = :albumDescription, " +
            "albumWorkId = :albumWorkId, " +
            "albumRjCode = :albumRjCode, " +
            "updatedAt = :updatedAt " +
            "WHERE id = :taskId"
    )
    suspend fun updateTaskMetadata(
        taskId: Long,
        subtitle: String,
        albumTitle: String,
        albumCircle: String,
        albumCv: String,
        albumTagsCsv: String,
        albumCoverUrl: String,
        albumDescription: String,
        albumWorkId: String,
        albumRjCode: String,
        updatedAt: Long
    )

    @Query("SELECT * FROM download_items WHERE taskId = :taskId ORDER BY relativePath ASC")
    suspend fun getItemsForTask(taskId: Long): List<DownloadItemEntity>

    @Query("SELECT * FROM download_items WHERE workId = :workId LIMIT 1")
    suspend fun getItemByWorkId(workId: String): DownloadItemEntity?

    @Query("SELECT * FROM download_items WHERE filePath = :filePath LIMIT 1")
    suspend fun getItemByFilePath(filePath: String): DownloadItemEntity?

    @Query("SELECT * FROM download_items WHERE taskId = :taskId AND relativePath = :relativePath LIMIT 1")
    suspend fun getItemByTaskAndRelativePath(taskId: Long, relativePath: String): DownloadItemEntity?

    @Query("UPDATE download_items SET filePath = :filePath, targetDir = :targetDir, updatedAt = :updatedAt WHERE workId = :workId")
    suspend fun updateItemDestination(workId: String, filePath: String, targetDir: String, updatedAt: Long)

    @Query(
        "SELECT * FROM download_items " +
            "WHERE state = 'QUEUED' " +
            "ORDER BY " +
            "CASE WHEN downloaded > 0 THEN 0 ELSE 1 END ASC, " +
            "downloaded DESC, " +
            "updatedAt DESC, " +
            "createdAt ASC " +
            "LIMIT :limit"
    )
    suspend fun getQueuedItems(limit: Int): List<DownloadItemEntity>

    @Query("SELECT * FROM download_items WHERE state IN ('RUNNING', 'ENQUEUED', 'BLOCKED')")
    suspend fun getActiveItems(): List<DownloadItemEntity>

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

    @Query("UPDATE download_items SET state = :state, updatedAt = :updatedAt WHERE taskId = :taskId AND state NOT IN ('SUCCEEDED', 'PAUSED')")
    suspend fun pauseAllItemsInTask(taskId: Long, state: String, updatedAt: Long)

    @Query("SELECT * FROM download_items WHERE state = 'PAUSED' OR state IN ('RUNNING', 'ENQUEUED', 'BLOCKED', 'QUEUED')")
    suspend fun getAllActiveOrPausedItems(): List<DownloadItemEntity>

    @Query("SELECT COUNT(*) FROM download_items WHERE state = 'PAUSED' OR state IN ('RUNNING', 'ENQUEUED', 'BLOCKED', 'QUEUED')")
    suspend fun countRecoverableItems(): Int

    @Query("SELECT COUNT(*) FROM download_items WHERE state IN ('RUNNING', 'ENQUEUED', 'BLOCKED')")
    suspend fun countActiveItems(): Int

    @Query("SELECT COUNT(*) FROM download_items WHERE state = 'PAUSED'")
    suspend fun countPausedItems(): Int

    @Query("SELECT COUNT(*) FROM download_items WHERE state != 'SUCCEEDED'")
    suspend fun countUnfinishedItems(): Int
}

data class DownloadTaskAlbumCoverRow(
    val taskId: Long,
    val coverUrl: String,
    val coverPath: String,
    val coverThumbPath: String
)
