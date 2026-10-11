package com.asmr.player.subtitle

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.AppDatabaseProvider
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.data.local.db.entities.SubtitleTaskEntity
import com.asmr.player.data.local.db.entities.SubtitleTaskItemEntity
import com.asmr.player.data.local.db.entities.SubtitleTitleOwnerEntity
import com.asmr.player.data.local.db.entities.SubtitleTitleOwnerKind
import com.asmr.player.data.local.db.entities.TrackEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class SubtitleTitleTranslationCompletionTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: SubtitleTaskRepository
    private var albumId = 0L
    private var trackId = 0L

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        AppDatabaseProvider::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, database)
        repository = SubtitleTaskRepository.get(context)
        albumId = database.albumDao().insertAlbum(AlbumEntity(title = "作品原名", path = "/album"))
        trackId = database.trackDao().insertTrack(TrackEntity(albumId = albumId, title = "音轨原名", path = "/album/audio.mp3"))
        database.subtitleTaskDao().insertTask(SubtitleTaskEntity(
            id = "task", origin = SubtitleTaskOrigin.GENERATED, title = "作品原名", rjCode = "",
            state = SubtitleTaskState.ACTIVE, warning = "", createdAt = 1, updatedAt = 1
        ))
        database.subtitleTaskDao().insertItems(listOf(SubtitleTaskItemEntity(
            id = "item", taskId = "task", trackId = trackId, trackTitle = "音轨原名", trackPath = "/album/audio.mp3",
            mode = SubtitleTaskMode.GENERATED, queueSequence = 1, state = SubtitleItemState.TRANSLATING,
            suspendedFromState = "", transcriptionChunkCursor = 0, transcriptionProgress = 0,
            transcriptionModelId = "", transcribedMs = 0, totalDurationMs = 0, translationCursor = 0,
            translationTotal = 0, translationBatchIndex = 0, translationBatchTotal = 0, attempt = 0,
            nextAttemptAt = 0, errorMessage = "", originalHash = "", lastPublishedHash = "", createdAt = 1, updatedAt = 1
        )))
        database.subtitleTitleOwnerDao().upsertAll(listOf(
            SubtitleTitleOwnerEntity("task", SubtitleTitleOwnerKind.ALBUM, albumId, "", 1),
            SubtitleTitleOwnerEntity("task", SubtitleTitleOwnerKind.TRACK, trackId, "", 1)
        ))
    }

    @After
    fun tearDown() {
        database.close()
        // 归还进程级单例，避免下一个测试持有已经关闭的数据库。
        AppDatabaseProvider::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, null)
        SubtitleTaskRepository::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, null)
    }

    @Test
    fun exhaustedTitleTranslation_clearsPendingWorkAndKeepsOriginalTitles() = runBlocking {
        repository.finishTitleTranslation("task")

        assertEquals(emptyList<String>(), database.subtitleTitleOwnerDao().getPendingTaskIds())
        assertNotNull(database.subtitleTaskDao().getItem("item"))
        assertEquals("作品原名", database.albumDao().getAlbumById(albumId)?.title)
        assertEquals("", database.albumDao().getAlbumById(albumId)?.displayTitle)
        assertEquals("音轨原名", database.trackDao().getTrackByIdOnce(trackId)?.title)
        assertEquals("", database.trackDao().getTrackByIdOnce(trackId)?.displayTitle)
    }

    @Test
    fun exhaustedTitleTranslation_retainsSuccessfulOwnersForCancellation() = runBlocking {
        database.albumDao().updateAlbumDisplayTitle(albumId, "已翻译作品名")
        database.subtitleTitleOwnerDao().updateDisplayTitle("task", SubtitleTitleOwnerKind.ALBUM, albumId, "已翻译作品名")

        repository.finishTitleTranslation("task")

        val owners = database.subtitleTitleOwnerDao().getByTask("task")
        assertEquals(1, owners.size)
        assertEquals("已翻译作品名", owners.single().displayTitle)
        assertEquals(emptyList<String>(), database.subtitleTitleOwnerDao().getPendingTaskIds())
        assertEquals("已翻译作品名", database.albumDao().getAlbumById(albumId)?.displayTitle)
    }

    @Test
    fun exhaustedTitleTranslation_releasesAlreadyCompletedSubtitleTask() = runBlocking {
        val dao = database.subtitleTaskDao()
        dao.updateItem(dao.getItem("item")!!.copy(state = SubtitleItemState.SUCCEEDED))
        repository.finishSucceeded("item")
        assertNotNull(dao.getItem("item"))

        repository.finishTitleTranslation("task")

        assertNull(dao.getItem("item"))
        assertNull(dao.getTask("task"))
        assertEquals(emptyList<String>(), database.subtitleTitleOwnerDao().getPendingTaskIds())
        assertNotNull(database.trackDao().getTrackByIdOnce(trackId))
    }
}
