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
import com.asmr.player.util.SyncCoordinator
import com.asmr.player.util.LocalFileScopes
import com.asmr.player.util.withSubtitleFileAccess
import com.asmr.player.util.tryBeginFileMutation
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
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
    fun runningAlbumAcceptsNewFilesWhileSkippingDuplicateSelections() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            withTimeout(5_000) {
                val storage = DownloadStorageGateway(context)
                val store = DownloadDestinationStore(context)
                val coordinator = DownloadDirectoryCoordinator(database, store, storage)
                val manager = DownloadManager(context, database.downloadDao(), coordinator, storage)
                val request = DownloadBatchRequest("RJ111111", "album:RJ111111", listOf(
                    RelativeDownloadItem("https://example.test/a.mp3", "a.mp3")
                ), albumRjCode = "RJ111111")
                assertEquals(EnqueueDownloadBatchResult.Accepted(1), manager.enqueueBatch(request))
                val dao = database.downloadDao()
                val task = dao.getAllTasksOnce().single()
                val first = dao.getItemsForTask(task.id).single()
                dao.updateItemState(first.workId, "RUNNING", 2L)
                val scope = LocalFileScopes(database, storage).download(task)
                val downloading = checkNotNull(com.asmr.player.util.LocalFileOperationCoordinator.shared.tryAcquire(
                    com.asmr.player.util.LocalFileOperation.DOWNLOAD, "a.mp3", scope
                ))
                try {
                    val added = RelativeDownloadItem("https://example.test/b.mp3", "b.mp3")
                    assertEquals(EnqueueDownloadBatchResult.Accepted(1), manager.enqueueBatch(request.copy(items = request.items + added + added)))
                    assertEquals(listOf("a.mp3", "b.mp3"), dao.getItemsForTask(task.id).map { it.relativePath })
                    assertEquals("RUNNING", dao.getItemByWorkId(first.workId)?.state)
                } finally {
                    downloading.close()
                }
                assertEquals(EnqueueDownloadBatchResult.TaskBlocked, manager.enqueueBatch(request))
                dao.updateItemState(first.workId, DOWNLOAD_STATE_FINALIZING, 3L)
                org.junit.Assert.assertFalse(dao.isReadyForFinalization(task.id))
                val second = dao.getItemsForTask(task.id).single { it.relativePath == "b.mp3" }
                dao.updateItemState(second.workId, DOWNLOAD_STATE_FINALIZING, 4L)
                org.junit.Assert.assertTrue(dao.isReadyForFinalization(task.id))
                dao.finishFinalizingItems(task.id, 5L)
                org.junit.Assert.assertFalse(dao.isReadyForFinalization(task.id))
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun supplementaryDownloadBlocksOnlyItsAlbumUntilLibraryCommit() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            withTimeout(5_000) {
                val scopes = LocalFileScopes(database, DownloadStorageGateway(context))
                val first = scopes.create(listOf("RJ111111"), listOf("/albums/RJ111111"))
                val second = scopes.create(listOf("RJ222222"), listOf("/albums/RJ222222"))
                val dao = database.downloadDao()
                val taskId = dao.insertTask(DownloadTaskEntity(
                    taskKey = "supplement", title = "RJ111111", rootDir = "/albums/RJ111111", albumRjCode = "RJ111111",
                    createdAt = 1L, updatedAt = 1L,
                ))
                dao.upsertItem(item(taskId, "supplement", DOWNLOAD_STATE_FINALIZING))
                val firstTranslation = async(start = CoroutineStart.UNDISPATCHED) {
                    database.withSubtitleFileAccess(scopes, first) { "A 开始翻译" }
                }
                assertEquals("B 开始翻译", database.withSubtitleFileAccess(scopes, second) { "B 开始翻译" })
                yield()
                org.junit.Assert.assertFalse(firstTranslation.isCompleted)
                assertNull(database.tryBeginFileMutation(scopes, first))
                checkNotNull(database.tryBeginFileMutation(scopes, second)).close()
                assertEquals(1, dao.countUnfinishedItems())
                dao.updateItemState("supplement", "PAUSED", 2L)
                assertNull(database.tryBeginFileMutation(scopes, first))
                checkNotNull(database.tryBeginFileMutation(scopes, first, allowStoppedDownloads = true)).close()
                dao.updateItemState("supplement", DOWNLOAD_STATE_FINALIZING, 2L)
                dao.finishFinalizingItems(taskId, 2L)
                assertEquals("A 开始翻译", firstTranslation.await())
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun pendingTranslationBlocksNewDownloadOnlyForItsAlbum() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            withTimeout(5_000) {
                val storage = DownloadStorageGateway(context)
                val scopes = LocalFileScopes(database, storage)
                val coordinator = DownloadDirectoryCoordinator(database, DownloadDestinationStore(context), storage)
                val first = scopes.create(listOf("RJ111111"), listOf("/audio/first"))
                val second = scopes.create(listOf("RJ222222"), listOf("/audio/second"))
                val album = database.albumDao().insertAlbum(AlbumEntity(title = "A", path = "/audio/first", rjCode = "RJ111111"))
                val track = database.trackDao().insertTrack(TrackEntity(albumId = album, title = "音轨", path = "/audio/first/a.wav"))
                insertSubtitleTask(database, "translating-A", track, "/audio/first/a.wav")
                assertNull(database.tryBeginFileMutation(scopes, first))
                checkNotNull(database.tryBeginFileMutation(scopes, second)).close()
                val blocked = async(start = CoroutineStart.UNDISPATCHED) {
                    coordinator.withDirectoryLock({ first }) { "A 开始下载" }
                }
                assertEquals("B 开始下载", coordinator.withDirectoryLock({ second }) { "B 开始下载" })
                org.junit.Assert.assertFalse(blocked.isCompleted)
                database.subtitleTaskDao().deleteTask("translating-A")
                assertEquals("A 开始下载", blocked.await())
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun externalStorageUriAndPhysicalPathShareTheSameScope() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val scopes = LocalFileScopes(database, DownloadStorageGateway(context))
            val authority = "com.android.externalstorage.documents"
            val tree = android.provider.DocumentsContract.buildTreeDocumentUri(authority, "primary:Music")
            val uri = android.provider.DocumentsContract.buildDocumentUriUsingTree(tree, "primary:Music/RJ111111")
            val physical = File(android.os.Environment.getExternalStorageDirectory(), "Music/RJ111111")
            val document = scopes.create(roots = listOf(uri.toString()))
            org.junit.Assert.assertTrue(document.overlaps(scopes.create(roots = listOf(physical.path))))
            org.junit.Assert.assertTrue(scopes.create(roots = listOf("file:///storage/music/RJ111111"))
                .overlaps(scopes.create(roots = listOf("/storage/music/RJ111111"))))
            org.junit.Assert.assertFalse(document.overlaps(scopes.create(roots = listOf(physical.path + "2"))))
        } finally {
            database.close()
        }
    }

    @Test
    fun scanAndSubtitleReservationsBlockDirectoryChange() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val coordinator = DownloadDirectoryCoordinator(database, DownloadDestinationStore(context), DownloadStorageGateway(context))
            val destination = DownloadDestinationStore(context).defaultDestination()
            val sync = SyncCoordinator(database, DownloadStorageGateway(context))
            val scan = checkNotNull(sync.tryBegin())
            try {
                assertEquals(DownloadDirectoryChangeResult.BlockedByLocalFileOperation, coordinator.changeDestination(destination))
            } finally {
                sync.end(scan)
            }
            val album = database.albumDao().insertAlbum(AlbumEntity(title = "专辑", path = "/audio"))
            val track = database.trackDao().insertTrack(TrackEntity(albumId = album, title = "音轨", path = "/audio/a.wav"))
            insertSubtitleTask(database, "pending-subtitle", track, "/audio/a.wav")
            assertEquals(DownloadDirectoryChangeResult.BlockedByLocalFileOperation, coordinator.changeDestination(destination))
            assertNull(SyncCoordinator(database, DownloadStorageGateway(context)).tryBegin())
            database.subtitleTaskDao().deleteTask("pending-subtitle")
            assertEquals(DownloadDirectoryChangeResult.Unchanged, coordinator.changeDestination(destination))
        } finally {
            database.close()
        }
    }

    @Test
    fun delayedDownloadUpdatesCannotOverridePauseCancelOrDeletion() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val dao = database.downloadDao()
            val task = dao.insertTask(DownloadTaskEntity(taskKey = "task", title = "专辑", rootDir = "/audio", createdAt = 1L, updatedAt = 1L))
            listOf("PAUSED", "CANCELLED", "FAILED", "SUCCEEDED", DOWNLOAD_STATE_FINALIZING).forEach { state ->
                dao.upsertItem(item(task, "work", state))
                assertEquals(0, dao.updateRunningItemProgress("work", "SUCCEEDED", 10, 10, 0, 2))
                dao.failRunningItem("work", 3)
                assertEquals(0, dao.replaceWorkIdForResume("work", "next", "ENQUEUED", 0, 4))
                assertEquals(state, dao.getItemByWorkId("work")?.state)
            }
            listOf("SUCCEEDED", DOWNLOAD_STATE_FINALIZING).forEach { state ->
                dao.upsertItem(item(task, "committed", state))
                assertEquals(0, dao.stopInterruptibleItem("committed", "PAUSED", 4))
                assertEquals(0, dao.stopInterruptibleItem("committed", "CANCELLED", 4))
                assertEquals(state, dao.getItemByWorkId("committed")?.state)
            }
            dao.deleteItemsForTask(task)
            assertEquals(0, dao.updateRunningItemProgress("work", "RUNNING", 10, 10, 0, 5))
            assertNull(dao.getItemByWorkId("work"))
            dao.upsertItem(item(task, "queued", "QUEUED"))
            assertEquals(1, dao.replaceWorkIdForResume("queued", "scheduled", "ENQUEUED", 0, 6))
            assertEquals(1, dao.updateRunningItemProgress("scheduled", "RUNNING", 5, 10, 1, 7))
        } finally {
            database.close()
        }
    }

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
            val states = listOf("QUEUED", "ENQUEUED", "RUNNING", "BLOCKED", "PAUSED", "FAILED", "CANCELLED", DOWNLOAD_STATE_FINALIZING)
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
