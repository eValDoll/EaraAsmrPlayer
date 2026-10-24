package com.asmr.player.subtitle

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.AppDatabaseProvider
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.data.local.db.entities.SubtitleCommittedCaptionEntity
import com.asmr.player.data.local.db.entities.SubtitleEntity
import com.asmr.player.data.local.db.entities.SubtitleTaskEntity
import com.asmr.player.data.local.db.entities.SubtitleTaskItemEntity
import com.asmr.player.data.local.db.entities.SubtitleTitleOwnerEntity
import com.asmr.player.data.local.db.entities.SubtitleTitleOwnerKind
import com.asmr.player.data.local.db.entities.SubtitleTranslationSourceEntity
import com.asmr.player.data.local.db.entities.TrackEntity
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34], manifest = Config.NONE)
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
        SubtitleTaskRepository::class.java.getDeclaredField("startupReconciled")
            .apply { isAccessible = true }.setBoolean(repository, true)
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
        database.subtitleTitleOwnerDao().insertMissing(listOf(
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
        finishCurrentTitleAttempt()

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

        finishCurrentTitleAttempt()

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

        finishCurrentTitleAttempt()

        assertNull(dao.getItem("item"))
        assertNull(dao.getTask("task"))
        assertEquals(emptyList<String>(), database.subtitleTitleOwnerDao().getPendingTaskIds())
        assertNotNull(database.trackDao().getTrackByIdOnce(trackId))
    }

    @Test
    fun explicitResumeAndRetry_restoreExhaustedTitlesWithoutResettingSubtitleProgress() = runBlocking {
        val dao = database.subtitleTaskDao()
        val sources = listOf(
            SubtitleTranslationSourceEntity("item", 0, 0, 1_000, "おやすみ"),
            SubtitleTranslationSourceEntity("item", 1, 1_000, 2_000, "また明日")
        )
        val committed = listOf(SubtitleCommittedCaptionEntity("item", 0, 0, 0, 0, 1_000, "おやすみ", "晚安"))
        dao.insertSources(sources)
        dao.insertCommittedCaptions(committed)
        database.trackDao().insertSubtitles(listOf(SubtitleEntity(trackId = trackId, startMs = 0, endMs = 1_000, text = "晚安")))
        val published = database.trackDao().getSubtitlesForTrack(trackId)

        listOf(SubtitleItemState.INTERRUPTED, SubtitleItemState.FAILED).forEach { state ->
            finishCurrentTitleAttempt()
            assertEquals(emptyList<String>(), database.subtitleTitleOwnerDao().getPendingTaskIds())
            dao.updateItem(requireNotNull(dao.getItem("item")).copy(
                state = state, suspendedFromState = SubtitleItemState.TRANSLATING,
                translationCursor = 1, translationTotal = 2, transcriptionChunkCursor = 7,
                transcriptionProgress = 83, lastPublishedHash = "published-subtitles", attempt = 3
            ))

            if (state == SubtitleItemState.FAILED) repository.retryItem("item") else repository.resumeItem("item")

            assertEquals(listOf("task"), database.subtitleTitleOwnerDao().getPendingTaskIds())
            assertEquals(2, database.subtitleTitleOwnerDao().getByTask("task").size)
            val resumed = requireNotNull(dao.getItem("item"))
            assertEquals(SubtitleItemState.QUEUED_TRANSLATION, resumed.state)
            assertEquals(1, resumed.translationCursor)
            assertEquals(2, resumed.translationTotal)
            assertEquals(7, resumed.transcriptionChunkCursor)
            assertEquals(83, resumed.transcriptionProgress)
            assertEquals("published-subtitles", resumed.lastPublishedHash)
            assertEquals(sources, dao.getSources("item"))
            assertEquals(committed, dao.getCommittedCaptions("item"))
            assertEquals(published, database.trackDao().getSubtitlesForTrack(trackId))
        }
    }

    @Test
    fun resumeTask_keepsSuccessfulOwnersAndRegistersOnlyMissingUntranslatedTracks() = runBlocking {
        val ownerDao = database.subtitleTitleOwnerDao()
        database.albumDao().updateAlbumDisplayTitle(albumId, "已翻译作品名")
        database.trackDao().updateTrackDisplayTitle(trackId, "已翻译音轨名")
        ownerDao.updateDisplayTitle("task", SubtitleTitleOwnerKind.ALBUM, albumId, "已翻译作品名")
        ownerDao.updateDisplayTitle("task", SubtitleTitleOwnerKind.TRACK, trackId, "已翻译音轨名")
        val successfulOwners = ownerDao.getByTask("task")
        val missingTrackId = insertTrackItem("missing", SubtitleItemState.INTERRUPTED)

        repository.resumeTask("task")

        val owners = ownerDao.getByTask("task")
        assertEquals(3, owners.size)
        successfulOwners.forEach { expected ->
            assertEquals(expected, owners.single { it.kind == expected.kind && it.targetId == expected.targetId })
        }
        assertEquals("", owners.single { it.kind == SubtitleTitleOwnerKind.TRACK && it.targetId == missingTrackId }.displayTitle)
        assertEquals("已翻译作品名", database.albumDao().getAlbumById(albumId)?.displayTitle)
        assertEquals("已翻译音轨名", database.trackDao().getTrackByIdOnce(trackId)?.displayTitle)
        assertEquals(listOf("task"), ownerDao.getPendingTaskIds())
    }

    @Test
    fun resumeTask_restoresAllResumableItemsBeforeWakingServiceOnce() = runBlocking {
        finishCurrentTitleAttempt()
        val dao = database.subtitleTaskDao()
        val original = requireNotNull(dao.getItem("item"))
        dao.updateItem(original.copy(
            state = SubtitleItemState.PAUSED, suspendedFromState = SubtitleItemState.TRANSCRIBING,
            transcriptionChunkCursor = 4, transcriptionProgress = 37, lastPublishedHash = "published-subtitles"
        ))
        insertTrackItem("interrupted", SubtitleItemState.INTERRUPTED)
        insertTrackItem("failed", SubtitleItemState.FAILED)
        val before = dao.getItemsForTask("task").associateBy { it.id }
        val application = ApplicationProvider.getApplicationContext<Application>()
        val shadowApplication = shadowOf(application)
        assertNull(shadowApplication.nextStartedService)

        repository.resumeTask("task")

        assertEquals(SubtitleItemState.QUEUED_TRANSCRIPTION, dao.getItem("item")?.state)
        assertEquals(SubtitleItemState.QUEUED_TRANSLATION, dao.getItem("interrupted")?.state)
        assertEquals(SubtitleItemState.QUEUED_TRANSLATION, dao.getItem("failed")?.state)
        dao.getItemsForTask("task").forEach { resumed ->
            val previous = requireNotNull(before[resumed.id])
            assertEquals(previous.transcriptionChunkCursor, resumed.transcriptionChunkCursor)
            assertEquals(previous.transcriptionProgress, resumed.transcriptionProgress)
            assertEquals(previous.translationCursor, resumed.translationCursor)
            assertEquals(previous.lastPublishedHash, resumed.lastPublishedHash)
        }
        assertEquals(4, database.subtitleTitleOwnerDao().getByTask("task").size)
        val wake = requireNotNull(shadowApplication.nextStartedService)
        assertEquals(SubtitleTaskService::class.java.name, wake.component?.className)
        assertNull(shadowApplication.nextStartedService)
    }

    @Test
    fun resumeTask_doesNotTakeOwnershipOfAlreadyTranslatedTrackWithoutOwner() = runBlocking {
        finishCurrentTitleAttempt()
        database.trackDao().updateTrackDisplayTitle(trackId, "独立已有译名")
        val dao = database.subtitleTaskDao()
        dao.updateItem(requireNotNull(dao.getItem("item")).copy(state = SubtitleItemState.INTERRUPTED))

        repository.resumeTask("task")

        assertEquals(emptyList<SubtitleTitleOwnerEntity>(), database.subtitleTitleOwnerDao().getByTask("task"))
        assertEquals("独立已有译名", database.trackDao().getTrackByIdOnce(trackId)?.displayTitle)
    }

    @Test
    fun resumeTask_registersOnceAndDoesNotRebuildExhaustedWorkWithoutAnotherResume() = runBlocking {
        finishCurrentTitleAttempt()
        val dao = database.subtitleTaskDao()
        dao.updateItem(requireNotNull(dao.getItem("item")).copy(state = SubtitleItemState.INTERRUPTED))
        repository.resumeTask("task")
        val restoredOwners = database.subtitleTitleOwnerDao().getByTask("task")
        assertEquals(2, restoredOwners.size)

        repository.resumeTask("task")

        assertEquals(restoredOwners, database.subtitleTitleOwnerDao().getByTask("task"))
        finishCurrentTitleAttempt()
        repository.refreshTaskState("task")
        repository.reconcileOnAppLaunch()
        assertEquals(emptyList<String>(), database.subtitleTitleOwnerDao().getPendingTaskIds())
        assertEquals(emptyList<SubtitleTitleOwnerEntity>(), database.subtitleTitleOwnerDao().getByTask("task"))
    }

    @Test
    fun resumeTask_skipsCanceledItemsWhenRestoringOwners() = runBlocking {
        finishCurrentTitleAttempt()
        insertTrackItem("canceled", SubtitleItemState.CANCELED)
        insertTrackItem("cancel-requested", SubtitleItemState.CANCEL_REQUESTED)
        val dao = database.subtitleTaskDao()
        dao.updateItem(requireNotNull(dao.getItem("item")).copy(state = SubtitleItemState.INTERRUPTED))

        repository.resumeTask("task")

        val owners = database.subtitleTitleOwnerDao().getByTask("task")
        assertEquals(2, owners.size)
        assertEquals(setOf(trackId), owners.filter { it.kind == SubtitleTitleOwnerKind.TRACK }.map { it.targetId }.toSet())
        assertEquals(SubtitleItemState.CANCELED, dao.getItem("canceled")?.state)
        assertEquals(SubtitleItemState.CANCEL_REQUESTED, dao.getItem("cancel-requested")?.state)
    }

    @Test
    fun exhaustedEarlierAttempt_keepsNewPendingOwnerAndCompletedSubtitleItems() = runBlocking {
        val ownerDao = database.subtitleTitleOwnerDao()
        val attemptedOwners = ownerDao.getByTask("task")
        assertEquals(2, attemptedOwners.size)
        val addedTrackId = insertTrackItem("added", SubtitleItemState.INTERRUPTED)
        repository.resumeItem("added")
        assertEquals(3, ownerDao.getByTask("task").size)
        val dao = database.subtitleTaskDao()
        dao.getItemsForTask("task").forEach { item ->
            dao.updateItem(item.copy(state = SubtitleItemState.SUCCEEDED))
        }

        repository.finishTitleTranslation("task", attemptedOwners)

        val remaining = ownerDao.getByTask("task").single()
        assertEquals(SubtitleTitleOwnerKind.TRACK, remaining.kind)
        assertEquals(addedTrackId, remaining.targetId)
        assertEquals("", remaining.displayTitle)
        assertEquals(listOf("task"), ownerDao.getPendingTaskIds())
        assertNotNull(dao.getTask("task"))
        assertEquals(2, dao.getItemsForTask("task").size)
    }

    @Test
    fun taskFlow_observesTranslatedTitlesAcrossStaleProgressUpdatesAndTitleRemoval() = runBlocking {
        val dao = database.subtitleTaskDao()
        val originalItem = requireNotNull(dao.getItem("item"))
        val observed = MutableStateFlow<SubtitleTaskItemUi?>(null)
        val collector = launch {
            repository.tasks.collect { tasks ->
                observed.value = tasks.single().items.single()
            }
        }
        try {
            withTimeout(10_000) {
                assertEquals("音轨原名", observed.filterNotNull().first().title)

                database.trackDao().updateTrackDisplayTitle(trackId, "已翻译音轨名")

                val translated = observed.filterNotNull().first { it.title == "已翻译音轨名" }
                assertEquals(originalItem.translationCursor, translated.translationCursor)
                assertEquals(originalItem, dao.getItem("item"))

                dao.updateItem(originalItem.copy(translationCursor = 3, translationTotal = 6, translationBatchIndex = 1))

                val progressed = observed.filterNotNull().first { it.translationCursor == 3 }
                assertEquals("已翻译音轨名", progressed.title)
                assertEquals(6, progressed.translationTotal)
                assertEquals(1, progressed.translationBatchIndex)

                database.trackDao().updateTrackDisplayTitle(trackId, "")

                val original = observed.filterNotNull().first { it.title == originalItem.trackTitle }
                assertEquals(3, original.translationCursor)
                assertEquals(6, original.translationTotal)
            }
        } finally {
            collector.cancelAndJoin()
        }
    }

    private suspend fun finishCurrentTitleAttempt() {
        repository.finishTitleTranslation("task", database.subtitleTitleOwnerDao().getByTask("task"))
    }

    private suspend fun insertTrackItem(itemId: String, state: String): Long {
        val path = "/album/$itemId.mp3"
        val addedTrackId = database.trackDao().insertTrack(TrackEntity(albumId = albumId, title = itemId, path = path))
        val template = requireNotNull(database.subtitleTaskDao().getItem("item"))
        database.subtitleTaskDao().insertItems(listOf(template.copy(
            id = itemId, trackId = addedTrackId, trackTitle = itemId, trackPath = path,
            state = state, suspendedFromState = SubtitleItemState.TRANSLATING
        )))
        return addedTrackId
    }
}
