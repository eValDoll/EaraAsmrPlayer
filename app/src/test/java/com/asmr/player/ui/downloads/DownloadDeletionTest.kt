package com.asmr.player.ui.downloads

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.entities.DownloadItemEntity
import com.asmr.player.data.local.db.entities.DownloadTaskEntity
import com.asmr.player.data.remote.download.DownloadStorageGateway
import com.asmr.player.util.LocalFileOperation
import com.asmr.player.util.LocalFileOperationCoordinator
import com.asmr.player.util.LocalFileScopes
import com.asmr.player.util.withDownloadDeletion
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class DownloadDeletionTest {
    private lateinit var database: AppDatabase
    private lateinit var scopes: LocalFileScopes

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        scopes = LocalFileScopes(database, DownloadStorageGateway(context))
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun pausedFileCanBeDeletedWhileSiblingDownloadsAndCoverAwaitsFinalization() = runBlocking {
        val dao = database.downloadDao()
        val task = task()
        val paused = item(task, "paused", "PAUSED")
        val running = item(task, "running", "RUNNING")
        val cover = item(task, "cover", "FINALIZING")
        listOf(paused, running, cover).forEach { dao.upsertItem(it) }
        val downloading = checkNotNull(LocalFileOperationCoordinator.shared.tryAcquire(
            LocalFileOperation.DOWNLOAD, running.filePath, scopes.downloadFile(running)
        ))
        try {
            assertTrue(database.withDownloadDeletion(scopes, task, paused, stopDownload = {}) {
                dao.deleteItemByWorkId(paused.workId)
            })
            assertNull(dao.getItemByWorkId(paused.workId))
            assertEquals("RUNNING", dao.getItemByWorkId(running.workId)?.state)
            assertEquals("FINALIZING", dao.getItemByWorkId(cover.workId)?.state)
            assertNull(LocalFileOperationCoordinator.shared.tryAcquire(LocalFileOperation.DELETE, scope = scopes.download(task)))
        } finally {
            downloading.close()
        }
    }

    @Test
    fun deletionWaitsForCancelledWriterToReleaseItsFile() = runBlocking {
        withTimeout(5_000) {
            val dao = database.downloadDao()
            val task = task()
            val selected = item(task, "selected", "RUNNING")
            dao.upsertItem(selected)
            val writer = checkNotNull(LocalFileOperationCoordinator.shared.tryAcquire(
                LocalFileOperation.DOWNLOAD, selected.filePath, scopes.downloadFile(selected)
            ))
            val stopped = CompletableDeferred<Unit>()
            val deletion = async(start = CoroutineStart.UNDISPATCHED) {
                database.withDownloadDeletion(scopes, task, selected, stopDownload = {
                    dao.stopInterruptibleItem(selected.workId, "PAUSED", 2L)
                    stopped.complete(Unit)
                }) { dao.deleteItemByWorkId(selected.workId) }
            }
            try {
                stopped.await()
                assertFalse(deletion.isCompleted)
                assertNotNull(dao.getItemByWorkId(selected.workId))
                assertEquals(0, dao.updateRunningItemProgress(selected.workId, "RUNNING", 50L, 100L, 10L, 3L))
            } finally {
                writer.close()
            }
            assertTrue(deletion.await())
            assertNull(dao.getItemByWorkId(selected.workId))
        }
    }

    @Test
    fun deletingTaskStopsItsOwnTransfersAndIncludesPendingFinalizationFiles() = runBlocking {
        val dao = database.downloadDao()
        val task = task()
        listOf(item(task, "paused", "PAUSED"), item(task, "running", "RUNNING"), item(task, "cover", "FINALIZING"))
            .forEach { dao.upsertItem(it) }
        var stopped = false
        assertTrue(database.withDownloadDeletion(scopes, task, stopDownload = {
            dao.pauseTaskItems(task.id, 2L)
            stopped = true
        }) {
            assertTrue(stopped)
            assertEquals("PAUSED", dao.getItemByWorkId("running")?.state)
            dao.deleteTaskById(task.id)
        })
        assertNull(dao.getTaskById(task.id))
        assertTrue(dao.getItemsForTask(task.id).isEmpty())
    }

    @Test
    fun deletionStillProtectsOtherTaskWritingTheSameFileOrDirectory() = runBlocking {
        val dao = database.downloadDao()
        val selected = task()
        val other = task("other")
        val paused = item(selected, "paused", "PAUSED")
        dao.upsertItem(paused)
        dao.upsertItem(item(other, "duplicate", "RUNNING").copy(filePath = paused.filePath))
        assertFalse(database.withDownloadDeletion(scopes, selected, paused, stopDownload = { fail("不能取消其他下载") }) {
            fail("不能删除仍被其他任务写入的文件")
        })
        assertFalse(database.withDownloadDeletion(scopes, selected, stopDownload = { fail("不能取消其他下载") }) {
            fail("不能删除仍被其他任务使用的目录")
        })
        assertEquals("PAUSED", dao.getItemByWorkId(paused.workId)?.state)
    }

    @Test
    fun finalizationStillExcludesFileDeletionUntilItReleasesTheAlbum() = runBlocking {
        withTimeout(5_000) {
            val task = task()
            val selected = item(task, "selected", "FINALIZING")
            database.downloadDao().upsertItem(selected)
            val finalizing = checkNotNull(LocalFileOperationCoordinator.shared.tryAcquire(
                LocalFileOperation.FINALIZE_DOWNLOAD, scope = scopes.download(task)
            ))
            val stopped = CompletableDeferred<Unit>()
            val deletion = async(start = CoroutineStart.UNDISPATCHED) {
                database.withDownloadDeletion(scopes, task, selected, stopDownload = { stopped.complete(Unit) }) {
                    database.downloadDao().deleteItemByWorkId(selected.workId)
                }
            }
            try {
                stopped.await()
                assertFalse(deletion.isCompleted)
            } finally {
                finalizing.close()
            }
            assertTrue(deletion.await())
        }
    }

    private suspend fun task(key: String = "selected"): DownloadTaskEntity {
        val task = DownloadTaskEntity(taskKey = key, title = "RJ111111", rootDir = "/albums/RJ111111",
            albumRjCode = "RJ111111", createdAt = 1L, updatedAt = 1L)
        return task.copy(id = database.downloadDao().insertTask(task))
    }

    private fun item(task: DownloadTaskEntity, workId: String, state: String) = DownloadItemEntity(
        taskId = task.id, workId = workId, url = "https://example.test/audio.mp3", relativePath = "$workId.mp3",
        fileName = "$workId.mp3", targetDir = task.rootDir, filePath = "${task.rootDir}/$workId.mp3",
        state = state, downloaded = 42L, total = 100L, speed = 0L, createdAt = 1L, updatedAt = 1L
    )
}
