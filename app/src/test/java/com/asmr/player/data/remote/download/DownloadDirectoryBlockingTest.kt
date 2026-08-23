package com.asmr.player.data.remote.download

import android.app.Application
import androidx.room.Room
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.data.local.db.entities.DownloadItemEntity
import com.asmr.player.data.local.db.entities.DownloadTaskEntity
import com.asmr.player.data.local.db.entities.SubtitleTaskEntity
import com.asmr.player.data.local.db.entities.SubtitleTaskItemEntity
import com.asmr.player.data.local.db.entities.TrackEntity
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class DownloadDirectoryBlockingTest {
    @Test
    fun everyStateExceptSucceeded_blocksDirectoryChange() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = database.downloadDao()
            val taskId = dao.insertTask(
                DownloadTaskEntity(
                    taskKey = "album:RJ12345678@default",
                    logicalTaskKey = "album:RJ12345678",
                    title = "RJ12345678",
                    rootDir = "/albums/RJ12345678",
                    destinationRoot = "/albums",
                    albumRootDir = "/albums/RJ12345678",
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            )
            val states = listOf("QUEUED", "ENQUEUED", "RUNNING", "BLOCKED", "PAUSED", "FAILED", "CANCELLED")
            states.forEachIndexed { index, state ->
                dao.upsertItem(item(taskId, "work-$index", state))
                assertEquals("状态 $state 必须阻止切换", 1, dao.countUnfinishedItems())
                dao.deleteItemsForTask(taskId)
            }

            dao.upsertItem(item(taskId, "work-success", "SUCCEEDED"))
            assertEquals(0, dao.countUnfinishedItems())
        } finally {
            database.close()
        }
    }

    @Test
    fun clearingOldDestination_removesOnlyDownloadedAlbumAndItsTranslationTask() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val oldRoot = File(context.cacheDir, "old-download-root").absolutePath
            val importedRoot = File(oldRoot, "RJ10000001").absolutePath
            val downloadedRoot = File(oldRoot, "RJ10000002").absolutePath
            val importedAlbumId = database.albumDao().insertAlbum(
                AlbumEntity(
                    title = "手动导入作品",
                    path = importedRoot,
                    localPath = importedRoot,
                    rjCode = "RJ10000001",
                    workId = "RJ10000001",
                ),
            )
            val downloadedAlbumId = database.albumDao().insertAlbum(
                AlbumEntity(
                    title = "App 下载作品",
                    path = "web://rj/RJ10000002",
                    downloadPath = downloadedRoot,
                    rjCode = "RJ10000002",
                    workId = "RJ10000002",
                ),
            )
            val importedTrackId = database.trackDao().insertTrack(
                TrackEntity(
                    albumId = importedAlbumId,
                    title = "导入音轨",
                    path = File(importedRoot, "voice.mp3").absolutePath,
                ),
            )
            val downloadedTrackId = database.trackDao().insertTrack(
                TrackEntity(
                    albumId = downloadedAlbumId,
                    title = "下载音轨",
                    path = File(downloadedRoot, "voice.mp3").absolutePath,
                ),
            )
            val downloadTaskId = database.downloadDao().insertTask(
                DownloadTaskEntity(
                    taskKey = "album:RJ10000002@old",
                    logicalTaskKey = "album:RJ10000002",
                    title = "RJ10000002",
                    rootDir = downloadedRoot,
                    destinationRoot = oldRoot,
                    albumRootDir = downloadedRoot,
                    albumRjCode = "RJ10000002",
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            )
            database.downloadDao().upsertItem(item(downloadTaskId, "downloaded-work", "SUCCEEDED"))

            insertSubtitleTask(database, "import-task", importedTrackId, File(importedRoot, "voice.mp3").absolutePath)
            insertSubtitleTask(database, "download-task", downloadedTrackId, File(downloadedRoot, "voice.mp3").absolutePath)

            DownloadDirectoryCoordinator(
                database = database,
                destinationStore = DownloadDestinationStore(context),
                storage = DownloadStorageGateway(context),
            ).removeDatabaseRecordsForRoot(oldRoot)

            assertNotNull(database.albumDao().getAlbumById(importedAlbumId))
            assertNotNull(database.trackDao().getTrackByIdOnce(importedTrackId))
            assertNull(database.albumDao().getAlbumById(downloadedAlbumId))
            assertNull(database.trackDao().getTrackByIdOnce(downloadedTrackId))
            assertEquals(emptyList<DownloadTaskEntity>(), database.downloadDao().getAllTasksOnce())
            assertNotNull(database.subtitleTaskDao().getTask("import-task"))
            assertNull(database.subtitleTaskDao().getTask("download-task"))
        } finally {
            database.close()
        }
    }

    private suspend fun insertSubtitleTask(
        database: AppDatabase,
        taskId: String,
        trackId: Long,
        trackPath: String,
    ) {
        database.subtitleTaskDao().insertTask(
            SubtitleTaskEntity(
                id = taskId,
                origin = "MANUAL",
                title = taskId,
                rjCode = "",
                state = "SUCCEEDED",
                warning = "",
                createdAt = 1L,
                updatedAt = 1L,
            ),
        )
        database.subtitleTaskDao().insertItems(
            listOf(
                SubtitleTaskItemEntity(
                    id = "$taskId-item",
                    taskId = taskId,
                    trackId = trackId,
                    trackTitle = taskId,
                    trackPath = trackPath,
                    mode = "MANUAL",
                    queueSequence = 1L,
                    state = "SUCCEEDED",
                    suspendedFromState = "",
                    transcriptionChunkCursor = 0,
                    transcriptionProgress = 100,
                    transcriptionModelId = "",
                    transcribedMs = 0L,
                    totalDurationMs = 0L,
                    translationCursor = 0,
                    translationTotal = 0,
                    translationBatchIndex = 0,
                    translationBatchTotal = 0,
                    attempt = 0,
                    nextAttemptAt = 0L,
                    errorMessage = "",
                    originalHash = "",
                    lastPublishedHash = "",
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            ),
        )
    }

    private fun item(taskId: Long, workId: String, state: String) = DownloadItemEntity(
        taskId = taskId,
        workId = workId,
        url = "https://example.test/audio.mp3",
        relativePath = "audio.mp3",
        fileName = "audio.mp3",
        targetDir = "/albums/RJ12345678",
        filePath = "/albums/RJ12345678/audio.mp3",
        state = state,
        downloaded = 0L,
        total = 1L,
        speed = 0L,
        createdAt = 1L,
        updatedAt = 1L,
    )
}
