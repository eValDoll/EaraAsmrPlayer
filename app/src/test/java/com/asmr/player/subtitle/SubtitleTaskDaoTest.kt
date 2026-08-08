package com.asmr.player.subtitle

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.data.local.db.entities.SubtitleTaskEntity
import com.asmr.player.data.local.db.entities.SubtitleTaskItemEntity
import com.asmr.player.data.local.db.entities.SubtitleTaskSnapshotEntity
import com.asmr.player.data.local.db.entities.TrackEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class SubtitleTaskDaoTest {
    private lateinit var database: AppDatabase
    private var trackId = 0L

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        val albumId = database.albumDao().insertAlbum(
            AlbumEntity(title = "测试专辑", path = "/album", rjCode = "RJ123456")
        )
        trackId = database.trackDao().insertTrack(
            TrackEntity(albumId = albumId, title = "测试音频", path = "/album/track.mp3")
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun fifoQuery_usesGlobalQueueSequence() = runBlocking {
        val dao = database.subtitleTaskDao()
        dao.insertTask(task("batch"))
        dao.insertItems(
            listOf(
                item("second", "batch", trackId, 2L),
                item("first", "batch", createTrack("first"), 1L)
            )
        )

        assertEquals("first", dao.getNextTranscription()?.id)
    }

    @Test
    fun sameTrack_cannotHaveTwoPersistentItems() = runBlocking {
        val dao = database.subtitleTaskDao()
        dao.insertTask(task("batch-a"))
        dao.insertTask(task("batch-b"))
        dao.insertItems(listOf(item("one", "batch-a", trackId, 1L)))

        assertThrows(Exception::class.java) {
            runBlocking { dao.insertItems(listOf(item("two", "batch-b", trackId, 2L))) }
        }
        Unit
    }

    @Test
    fun deletingItem_cascadesItsCheckpointRows() = runBlocking {
        val dao = database.subtitleTaskDao()
        dao.insertTask(task("batch"))
        dao.insertItems(listOf(item("item", "batch", trackId, 1L)))
        dao.insertSnapshots(listOf(SubtitleTaskSnapshotEntity("item", 0, 0L, 1_000L, "原文")))

        dao.deleteItem("item")

        assertEquals(0, dao.getSnapshots("item").size)
        assertEquals(null, dao.getItem("item"))
    }

    private suspend fun createTrack(name: String): Long {
        val albumId = database.albumDao().insertAlbum(
            AlbumEntity(title = name, path = "/$name", rjCode = "RJ-$name")
        )
        return database.trackDao().insertTrack(
            TrackEntity(albumId = albumId, title = name, path = "/$name/audio.mp3")
        )
    }

    private fun task(id: String) = SubtitleTaskEntity(
        id = id,
        origin = SubtitleTaskOrigin.GENERATED,
        title = id,
        rjCode = "RJ123456",
        state = SubtitleTaskState.ACTIVE,
        warning = "",
        createdAt = 1L,
        updatedAt = 1L
    )

    private fun item(id: String, taskId: String, targetTrackId: Long, sequence: Long) =
        SubtitleTaskItemEntity(
            id = id,
            taskId = taskId,
            trackId = targetTrackId,
            trackTitle = id,
            trackPath = "/$id.mp3",
            mode = SubtitleTaskMode.GENERATED,
            queueSequence = sequence,
            state = SubtitleItemState.QUEUED_TRANSCRIPTION,
            suspendedFromState = "",
            transcriptionChunkCursor = 0,
            transcriptionProgress = 0,
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
            createdAt = sequence,
            updatedAt = sequence
        )
}
