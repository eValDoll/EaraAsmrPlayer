package com.asmr.player.data.lyrics

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.media3.common.MediaItem
import androidx.room.Room
import androidx.room.withTransaction
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.AppDatabaseMigrations
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.data.local.db.entities.SubtitleEntity
import com.asmr.player.data.local.db.entities.TrackEntity
import com.asmr.player.subtitle.GeneratedSubtitleFileExporter
import com.asmr.player.util.SubtitleParser
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class LyricsPersistenceTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private val context get() = RuntimeEnvironment.getApplication()
    private val databaseName = "lyrics-persistence-${System.nanoTime()}.db"
    private lateinit var database: AppDatabase
    private lateinit var loader: LyricsLoader
    private lateinit var audio: File
    private var albumId = 0L
    private var trackId = 0L
    private val item get() = mediaItem(audio)

    @Before
    fun setUp() = runBlocking {
        audio = temporaryFolder.newFile("audio.mp3")
        openDatabase()
        albumId = database.albumDao().insertAlbum(AlbumEntity(title = "作品", path = audio.parent!!))
        trackId = database.trackDao().insertTrack(TrackEntity(albumId = albumId, title = "音轨", path = audio.absolutePath))
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun observingCurrentAudio_updatesAfterPublishAndClearsAfterDeletion() = runBlocking {
        val updates = Channel<LyricsResult>(Channel.UNLIMITED)
        val job = launch(Dispatchers.Default) { loader.observe(item).collect { updates.send(it) } }
        try {
            assertTrue(updates.next().lyrics.isEmpty())
            database.trackDao().insertSubtitles(listOf(caption("转录原文")))
            assertEquals(listOf("转录原文"), updates.next().lyrics.map { it.text })
            database.withTransaction {
                database.trackDao().deleteSubtitlesForTrack(trackId)
                database.trackDao().insertSubtitles(listOf(caption("翻译后的中文")))
            }
            assertEquals(listOf("翻译后的中文"), updates.next().lyrics.map { it.text })
            database.trackDao().deleteSubtitlesByUser(listOf(trackId))
            assertTrue(updates.next().lyrics.isEmpty())
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test
    fun deletingCompletedSubtitles_preventsSidecarReimportAfterDatabaseReopen() = runBlocking {
        val sidecar = temporaryFolder.newFile("audio.lrc").apply { writeText("[00:00.00]旧的翻译字幕", Charsets.UTF_8) }
        assertEquals(listOf("旧的翻译字幕"), loader.load(item).lyrics.map { it.text })
        database.trackDao().deleteSubtitlesByUser(listOf(trackId))

        assertTrue(loader.load(item).lyrics.isEmpty())
        database.close()
        openDatabase()

        assertTrue(loader.load(item).lyrics.isEmpty())
        assertTrue(database.trackDao().getSubtitlesForTrack(trackId).isEmpty())
        assertTrue(database.trackDao().observeSubtitleTrackSummaries().first().isEmpty())
        assertEquals("[00:00.00]旧的翻译字幕", sidecar.readText(Charsets.UTF_8))
    }

    @Test
    fun automaticImportAlreadyInFlight_cannotRestoreDeletedRows() = runBlocking {
        val alreadyReadFile = listOf(caption("旧字幕"))
        database.trackDao().insertSubtitles(alreadyReadFile)
        database.trackDao().deleteSubtitlesByUser(listOf(trackId))

        assertFalse(database.trackDao().replaceAutoSubtitles(trackId, alreadyReadFile))
        assertFalse(database.trackDao().importScannedSubtitles(alreadyReadFile, restoreDeleted = false))
        database.trackDao().insertAutoSubtitles(alreadyReadFile)

        assertTrue(database.trackDao().getSubtitlesForTrack(trackId).isEmpty())
    }

    @Test
    fun automaticImportWithNewTrackId_staysBlockedUntilUserScan() = runBlocking {
        database.trackDao().deleteSubtitlesByUser(listOf(trackId))
        database.trackDao().deleteTrackById(trackId)
        val replacementId = database.trackDao().insertTrack(TrackEntity(albumId = albumId, title = "重新扫描", path = audio.absolutePath))
        assertTrue(replacementId != trackId)
        database.trackDao().insertAutoSubtitles(listOf(caption("磁盘字幕").copy(trackId = replacementId)))

        assertTrue(database.trackDao().getSubtitlesForTrack(replacementId).isEmpty())
        assertTrue(loader.load(item).lyrics.isEmpty())

        assertTrue(database.trackDao().importScannedSubtitles(
            listOf(caption("磁盘字幕").copy(trackId = replacementId)), restoreDeleted = true))
        assertFalse(database.trackDao().isSubtitleAutoImportBlocked(audio.absolutePath))
        assertEquals(listOf("磁盘字幕"), loader.load(item).lyrics.map { it.text })
    }

    @Test
    fun userScan_restoresExistingFileImmediatelyAndAfterReopen() = runBlocking {
        val sidecar = temporaryFolder.newFile("audio.lrc").apply { writeText("[00:00.00]磁盘上的字幕", Charsets.UTF_8) }
        assertEquals(listOf("磁盘上的字幕"), loader.load(item).lyrics.map { it.text })
        database.trackDao().deleteSubtitlesByUser(listOf(trackId))
        val updates = Channel<LyricsResult>(Channel.UNLIMITED)
        val job = launch(Dispatchers.Default) { loader.observe(item).collect { updates.send(it) } }
        try {
            assertTrue(updates.next().lyrics.isEmpty())
            val scanned = SubtitleParser.parse(sidecar.absolutePath).map {
                SubtitleEntity(trackId = trackId, startMs = it.startMs, endMs = it.endMs, text = it.text)
            }
            assertTrue(database.trackDao().importScannedSubtitles(scanned, restoreDeleted = true))
            assertEquals(listOf("磁盘上的字幕"), updates.next().lyrics.map { it.text })
            assertFalse(database.trackDao().isSubtitleAutoImportBlocked(audio.absolutePath))
        } finally {
            job.cancelAndJoin()
        }
        database.close()
        openDatabase()
        assertEquals(listOf("磁盘上的字幕"), loader.load(item).lyrics.map { it.text })
    }

    @Test
    fun userScanWithoutSubtitleFile_leavesDeletedSubtitlesEmpty() = runBlocking {
        database.trackDao().insertSubtitles(listOf(caption("已删除的字幕")))
        database.trackDao().deleteSubtitlesByUser(listOf(trackId))

        assertFalse(database.trackDao().importScannedSubtitles(emptyList(), restoreDeleted = true))
        assertTrue(database.trackDao().isSubtitleAutoImportBlocked(audio.absolutePath))
        assertTrue(database.trackDao().getSubtitlesForTrack(trackId).isEmpty())
        assertTrue(loader.load(item).lyrics.isEmpty())
    }

    @Test
    fun userScan_restoresOnlyTracksWithReadSubtitles() = runBlocking {
        val otherAudio = temporaryFolder.newFile("other.mp3")
        val otherId = database.trackDao().insertTrack(TrackEntity(albumId = albumId, title = "其他音轨", path = otherAudio.absolutePath))
        database.trackDao().deleteSubtitlesByUser(listOf(trackId, otherId))

        assertTrue(database.trackDao().importScannedSubtitles(listOf(caption("扫描读到的字幕")), restoreDeleted = true))
        assertEquals(listOf("扫描读到的字幕"), loader.load(item).lyrics.map { it.text })
        assertFalse(database.trackDao().isSubtitleAutoImportBlocked(audio.absolutePath))
        assertTrue(database.trackDao().isSubtitleAutoImportBlocked(otherAudio.absolutePath))
        assertTrue(database.trackDao().getSubtitlesForTrack(otherId).isEmpty())
    }

    @Test
    fun regeneratedSubtitles_overwriteOldFileAndUserScanLoadsNewText() = runBlocking {
        val sidecar = temporaryFolder.newFile("audio.lrc").apply { writeText("[00:00.00]旧字幕", Charsets.UTF_8) }
        database.trackDao().deleteSubtitlesByUser(listOf(trackId))
        database.trackDao().insertSubtitles(listOf(caption("重新翻译的字幕")))
        GeneratedSubtitleFileExporter(context, database).export(trackId)

        val scanned = SubtitleParser.parse(sidecar.absolutePath).map {
            SubtitleEntity(trackId = trackId, startMs = it.startMs, endMs = it.endMs, text = it.text)
        }
        assertEquals(listOf("重新翻译的字幕"), scanned.map { it.text })
        assertTrue(database.trackDao().importScannedSubtitles(scanned, restoreDeleted = true))
        assertEquals(listOf("重新翻译的字幕"), loader.load(item).lyrics.map { it.text })
    }

    @Test
    fun explicitlyGeneratedSubtitles_canBePublishedAfterDeletion() = runBlocking {
        database.trackDao().deleteSubtitlesByUser(listOf(trackId))
        database.trackDao().insertSubtitles(listOf(caption("重新生成的中文")))

        assertEquals(listOf("重新生成的中文"), loader.load(item).lyrics.map { it.text })
        assertEquals(1, database.trackDao().observeSubtitleTrackSummaries().first().size)
        database.trackDao().replaceAutoSubtitles(trackId, listOf(caption("旧文件中的中文")))
        assertEquals(listOf("重新生成的中文"), loader.load(item).lyrics.map { it.text })
    }

    @Test
    fun choosingManualSubtitleAfterDeletion_restoresUserSelectedSource() = runBlocking {
        database.trackDao().deleteSubtitlesByUser(listOf(trackId))
        val chosen = temporaryFolder.newFile("chosen.lrc").apply { writeText("[00:00.00]我选择的字幕", Charsets.UTF_8) }
        loader.saveManualLyrics(lyricsTargetContextFromMediaItem(item)!!, chosen.absolutePath)

        assertFalse(database.trackDao().isSubtitleAutoImportBlocked(audio.absolutePath))
        assertEquals(listOf("我选择的字幕"), loader.load(item).lyrics.map { it.text })
    }

    @Test
    fun deletingOneTrack_doesNotBlockOtherTracksInBatchImports() = runBlocking {
        val secondAudio = temporaryFolder.newFile("second.mp3")
        val secondId = database.trackDao().insertTrack(TrackEntity(albumId = albumId, title = "另一个音轨", path = secondAudio.absolutePath))
        database.trackDao().deleteSubtitlesByUser(listOf(trackId))
        database.trackDao().insertAutoSubtitles(listOf(caption("应被阻止"), caption("保留字幕").copy(trackId = secondId)))

        assertTrue(database.trackDao().getSubtitlesForTrack(trackId).isEmpty())
        assertEquals("保留字幕", database.trackDao().getSubtitlesForTrack(secondId).single().text)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun switchingAudio_stopsObservingPreviousTrack() = runBlocking {
        val secondAudio = temporaryFolder.newFile("second.mp3")
        val secondId = database.trackDao().insertTrack(TrackEntity(albumId = albumId, title = "另一个音轨", path = secondAudio.absolutePath))
        database.trackDao().insertSubtitles(listOf(caption("第一轨"), caption("第二轨").copy(trackId = secondId)))
        val current = MutableStateFlow(item)
        val updates = Channel<LyricsResult>(Channel.UNLIMITED)
        val job = launch(Dispatchers.Default) {
            current.flatMapLatest(loader::observe).collect { updates.send(it) }
        }
        try {
            assertEquals("第一轨", updates.next().lyrics.single().text)
            current.value = mediaItem(secondAudio)
            assertEquals("第二轨", updates.next().lyrics.single().text)
            database.trackDao().deleteSubtitlesByUser(listOf(trackId))
            assertNull(withTimeoutOrNull(250) { updates.receive() })
            database.trackDao().deleteSubtitlesByUser(listOf(secondId))
            assertTrue(updates.next().lyrics.isEmpty())
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test
    fun normalizingDisplay_doesNotRewriteGeneratedJapaneseOrDatabaseRows() = runBlocking {
        val generated = caption("相同的中文").copy(japaneseText = "日本語")
        database.trackDao().insertSubtitles(listOf(generated, generated))
        assertEquals(1, loader.load(item).lyrics.size)
        assertEquals(listOf("日本語", "日本語"), database.trackDao().getSubtitlesForTrack(trackId).map { it.japaneseText })
    }

    @Test
    fun upgradingVersion34_preservesSubtitlesAndValidatesNewRoomSchema() = runBlocking {
        database.trackDao().insertSubtitles(listOf(caption("升级前的字幕").copy(japaneseText = "日本語")))
        database.close()
        // 还原 34 版结构，再让 Room 执行真实升级并校验所有表和索引。
        SQLiteDatabase.openDatabase(context.getDatabasePath(databaseName).absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.execSQL("DROP TABLE subtitle_import_blocks")
            it.execSQL("DROP INDEX index_tracks_path")
            it.version = 34
        }
        openDatabase()

        assertEquals("日本語", database.trackDao().getSubtitlesForTrack(trackId).single().japaneseText)
        assertEquals(listOf("升级前的字幕"), loader.load(item).lyrics.map { it.text })
        assertFalse(database.trackDao().isSubtitleAutoImportBlocked(audio.absolutePath))
        database.trackDao().deleteSubtitlesByUser(listOf(trackId))
        assertTrue(loader.load(item).lyrics.isEmpty())
    }

    private fun openDatabase() {
        database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .allowMainThreadQueries().addMigrations(AppDatabaseMigrations.MIGRATION_34_35).build()
        loader = LyricsLoader(database, database.trackDao(), database.albumDao(), database.remoteSubtitleSourceDao(),
            ManualLyricsSourceRepository(database.manualLyricsSourceDao(), context),
            OkHttpClient.Builder().addInterceptor { error("本地字幕测试不应访问网络") }.build(), context)
    }

    private fun caption(text: String) = SubtitleEntity(trackId = trackId, startMs = 0, endMs = 1000, text = text)
    private fun mediaItem(file: File) = MediaItem.Builder().setMediaId(file.absolutePath).setUri(file.absolutePath).build()
    private suspend fun Channel<LyricsResult>.next(): LyricsResult = withTimeout(5000) { receive() }
}
