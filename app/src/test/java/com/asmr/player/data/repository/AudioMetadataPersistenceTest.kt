package com.asmr.player.data.repository

import android.app.Application
import androidx.room.Room
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.AppDatabaseMigrations
import com.asmr.player.data.local.db.dao.AudioMetadataDao
import com.asmr.player.data.local.db.entities.AudioMetadataEntity
import com.asmr.player.data.local.db.entities.PlaylistEntity
import com.asmr.player.data.local.db.entities.PlaylistItemEntity
import com.asmr.player.util.AudioQuality
import com.asmr.player.util.AudioTechnicalMetadata
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class AudioMetadataPersistenceTest {
    private val hq = AudioTechnicalMetadata(44100, 320000, 2, 0, "audio/mpeg", 123.0)
    private val sq = AudioTechnicalMetadata(48000, 0, 2, 24, "audio/flac", 234.0)
    private val path = "https://example.com/audio?id=1"

    @Test
    fun qualitiesSurviveDatabaseReopenAndKeepDistinctUrls() = runBlocking {
        withDatabase { open ->
            open().useDatabase { db ->
                for ((url, metadata) in listOf(path to hq, "$path&version=2" to sq)) {
                    AudioMetadataCache.Entry().apply { record(metadata) }.persist(url, db.audioMetadataDao())
                }
            }
            open().useDatabase { db ->
                for ((url, metadata) in listOf(path to hq, "$path&version=2" to sq)) {
                    val restored = AudioMetadataCache.Entry()
                    restored.restore(url, db.audioMetadataDao())
                    assertEquals(metadata, restored.metadata.value)
                    assertEquals(metadata.quality, restored.metadata.value?.quality)
                }
                assertNull(db.audioMetadataDao().get("https://example.com/unknown"))
            }
        }
    }

    @Test
    fun migrationPreservesFavoritesAndValidatesTheNewSchema() = runBlocking {
        withDatabase { open ->
            val favorite = PlaylistItemEntity(playlistId = 7, mediaId = path, title = "晚安音声", uri = path)
            open().useDatabase { db ->
                db.playlistDao().insertPlaylist(PlaylistEntity(id = 7, name = "我的收藏"))
                db.playlistItemDao().upsertItems(listOf(favorite))
                // Recreate the previous schema; reopening exercises Room's actual upgrade and validation.
                db.openHelper.writableDatabase.execSQL("DROP TABLE audio_metadata")
                db.openHelper.writableDatabase.version = 33
            }
            open().useDatabase { db ->
                assertEquals(listOf(favorite), db.playlistItemDao().getItemsOnce(7))
                assertEquals("我的收藏", db.playlistDao().getPlaylistByIdOnce(7)?.name)
                val entity = AudioMetadataEntity.from(path, sq)
                db.audioMetadataDao().upsert(entity)
                assertEquals(entity, db.audioMetadataDao().get(path))
            }
        }
    }

    @Test
    fun delayedRestoreDoesNotOverwriteNewPlayback() = runBlocking {
        val readStarted = CompletableDeferred<Unit>()
        val finishRead = CompletableDeferred<Unit>()
        val dao = object : MemoryDao(AudioMetadataEntity.from(path, hq)) {
            override suspend fun get(path: String): AudioMetadataEntity? {
                readStarted.complete(Unit)
                finishRead.await()
                return super.get(path)
            }
        }
        val entry = AudioMetadataCache.Entry()
        val restore = launch { entry.restore(path, dao) }
        readStarted.await()
        entry.record(sq)
        finishRead.complete(Unit)
        restore.join()
        assertEquals(sq, entry.metadata.value)
        entry.persist(path, dao)
        assertEquals(sq, dao.saved?.toMetadata())
    }

    @Test
    fun concurrentSavesKeepLatestQualityAndAvoidDuplicateWrites() = runBlocking {
        val writeStarted = CompletableDeferred<Unit>()
        val finishWrite = CompletableDeferred<Unit>()
        val dao = object : MemoryDao() {
            override suspend fun upsert(metadata: AudioMetadataEntity) {
                if (writes == 0) {
                    writeStarted.complete(Unit)
                    finishWrite.await()
                }
                super.upsert(metadata)
            }
        }
        val entry = AudioMetadataCache.Entry().apply { record(hq) }
        val first = launch { entry.persist(path, dao) }
        writeStarted.await()
        entry.record(sq)
        val second = launch { entry.persist(path, dao) }
        val duplicate = launch { entry.persist(path, dao) }
        finishWrite.complete(Unit)
        first.join()
        second.join()
        duplicate.join()
        assertEquals(sq, dao.saved?.toMetadata())
        assertEquals(2, dao.writes)
    }

    @Test
    fun incompleteFormatKeepsBadgeButKnownLowerQualityRemovesIt() = runBlocking {
        val dao = MemoryDao()
        val entry = AudioMetadataCache.Entry().apply { record(hq) }
        entry.persist(path, dao)
        entry.record(AudioTechnicalMetadata())
        entry.persist(path, dao)
        assertEquals(AudioQuality.HQ, entry.metadata.value?.quality)
        assertEquals(1, dao.writes)

        val lowerQuality = AudioTechnicalMetadata(22050, 64000, 1, 0, "audio/mpeg", 123.0)
        entry.record(lowerQuality)
        entry.persist(path, dao)
        val restored = AudioMetadataCache.Entry()
        restored.restore(path, dao)
        assertEquals(lowerQuality, restored.metadata.value)
        assertNull(restored.metadata.value?.quality)
    }

    private suspend fun withDatabase(block: suspend (() -> AppDatabase) -> Unit) {
        val context = RuntimeEnvironment.getApplication()
        // Keep SQLite's WAL paths below the Windows path limit in nested worktrees.
        val name = File.createTempFile("audio-meta-", ".db").absolutePath
        try {
            block {
                Room.databaseBuilder(context, AppDatabase::class.java, name)
                    .addMigrations(AppDatabaseMigrations.MIGRATION_33_34)
                    .allowMainThreadQueries()
                    .build()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    private open class MemoryDao(var saved: AudioMetadataEntity? = null) : AudioMetadataDao {
        var writes = 0
        override suspend fun get(path: String) = saved?.takeIf { it.sourcePath == path }
        override suspend fun upsert(metadata: AudioMetadataEntity) {
            saved = metadata
            writes++
        }
    }

    private inline fun <T> AppDatabase.useDatabase(block: (AppDatabase) -> T): T =
        try { block(this) } finally { close() }
}
