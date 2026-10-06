package com.asmr.player.ui.downloads

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.entities.DownloadItemEntity
import com.asmr.player.data.local.db.entities.DownloadTaskEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class DownloadTaskPauseTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun pauseTask_stopsEveryRunnableFileAndPreservesOtherStatesAndTasks() = runBlocking {
        val dao = database.downloadDao()
        val taskId = insertTask("selected")
        val otherTaskId = insertTask("other")
        val states = listOf("QUEUED", "ENQUEUED", "RUNNING", "BLOCKED", "FINALIZING", "SUCCEEDED", "FAILED", "CANCELLED", "PAUSED")
        states.forEach { dao.upsertItem(item(taskId, it, it)) }
        dao.upsertItem(item(otherTaskId, "other", "RUNNING"))

        assertEquals(states.take(4).toSet(), dao.pauseTaskItems(taskId, 20L).toSet())
        val files = dao.getItemsForTask(taskId).associateBy { it.workId }
        states.take(4).forEach { workId ->
            val paused = checkNotNull(files[workId])
            assertEquals("PAUSED", paused.state)
            assertEquals(0L, paused.speed)
            assertEquals(42L, paused.downloaded)
            assertEquals(100L, paused.total)
            assertEquals(20L, paused.updatedAt)
        }
        states.drop(4).forEach { assertEquals(it, files[it]?.state) }
        assertEquals("RUNNING", dao.getItemByWorkId("other")?.state)
        assertEquals(emptyList<String>(), dao.pauseTaskItems(taskId, 21L))
    }

    @Test
    fun pauseTask_usesCurrentWorkerIdAndPreventsQueueDispatchAndStaleProgress() = runBlocking {
        val dao = database.downloadDao()
        val taskId = insertTask("selected")
        dao.upsertItem(item(taskId, "queued", "QUEUED"))
        assertEquals(1, dao.replaceWorkIdForResume("queued", "scheduled", "ENQUEUED", 42L, 10L))

        assertEquals(listOf("scheduled"), dao.pauseTaskItems(taskId, 20L))
        assertEquals(0, dao.updateRunningItemProgress("scheduled", "RUNNING", 60L, 100L, 30L, 21L))
        assertEquals(0, dao.replaceWorkIdForResume("scheduled", "unexpected", "ENQUEUED", 42L, 22L))
        assertEquals("PAUSED", dao.getItemByWorkId("scheduled")?.state)
        assertEquals(emptyList<DownloadItemEntity>(), dao.getQueuedItems(10))
    }

    private suspend fun insertTask(key: String): Long = database.downloadDao().insertTask(
        DownloadTaskEntity(taskKey = key, title = key, rootDir = "/$key", createdAt = 1L, updatedAt = 1L)
    )

    private fun item(taskId: Long, workId: String, state: String) = DownloadItemEntity(
        taskId = taskId, workId = workId, url = "https://example.com/audio.mp3",
        relativePath = "$workId.mp3", fileName = "$workId.mp3", targetDir = "/album",
        filePath = "/album/$workId.mp3", state = state, downloaded = 42L, total = 100L,
        speed = 30L, createdAt = 1L, updatedAt = 1L
    )
}
