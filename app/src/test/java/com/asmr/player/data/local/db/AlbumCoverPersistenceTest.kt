package com.asmr.player.data.local.db

import android.app.Application
import androidx.room.Room
import com.asmr.player.data.local.db.entities.AlbumEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class AlbumCoverPersistenceTest {
    @Test
    fun manualCoverIncludingDefaultCoverFileSurvivesReopenAndCanBeCleared() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = File.createTempFile("album-cover-", ".db").absolutePath
        fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .allowMainThreadQueries().build()
        try {
            val id = open().useDatabase { database ->
                val dao = database.albumDao()
                val id = dao.insertAlbum(AlbumEntity(
                    title = "作品", path = "/albums/work", coverPath = "/albums/work/cover.jpg",
                    coverThumbPath = "/thumbs/automatic_v2.jpg",
                ))
                dao.setManualCoverPath(id, "/albums/work/cover.jpg")
                assertEquals("", dao.getAlbumById(id)?.coverThumbPath)
                id
            }
            open().useDatabase { database ->
                val dao = database.albumDao()
                assertEquals("/albums/work/cover.jpg", dao.getAlbumById(id)?.manualCoverPath)
                assertEquals("/albums/work/cover.jpg", dao.getAlbumById(id)?.coverPath)
                dao.setManualCoverPath(id, "")
                assertEquals("", dao.getAlbumById(id)?.manualCoverPath)
                assertEquals("/albums/work/cover.jpg", dao.getAlbumById(id)?.coverPath)
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test
    fun thumbnailFromPreviousSelectionCannotOverwriteCurrentCoverOrMetadata() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        database.useDatabase {
            val dao = database.albumDao()
            val id = dao.insertAlbum(AlbumEntity(
                title = "作品", path = "/albums/work", coverPath = "/albums/work/cover.jpg",
            ))
            val previous = requireNotNull(dao.getAlbumById(id))
            dao.setManualCoverPath(id, "/albums/work/custom.png")
            dao.updateAlbumDisplayTitle(id, "翻译标题")
            assertEquals(0, dao.updateAlbumCoverThumbIfSourceMatches(
                id, previous.manualCoverPath, previous.coverPath, previous.coverUrl, "/thumbs/stale_v2.jpg",
            ))
            val selected = requireNotNull(dao.getAlbumById(id))
            assertEquals("", selected.coverThumbPath)
            assertEquals("翻译标题", selected.displayTitle)
            assertEquals(1, dao.updateAlbumCoverThumbIfSourceMatches(
                id, selected.manualCoverPath, selected.coverPath, selected.coverUrl, "/thumbs/manual_v2.jpg",
            ))
            assertEquals("/thumbs/manual_v2.jpg", dao.getAlbumById(id)?.coverThumbPath)
            dao.setManualCoverPath(id, "")
            assertEquals(0, dao.updateAlbumCoverThumbIfSourceMatches(
                id, selected.manualCoverPath, selected.coverPath, selected.coverUrl, "/thumbs/manual_v2.jpg",
            ))
        }
    }

    @Test
    fun updatingAutomaticCoverKeepsManualThumbnailAndRejectsStaleAutomaticSource() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        database.useDatabase {
            val dao = database.albumDao()
            val id = dao.insertAlbum(AlbumEntity(
                title = "作品", path = "/albums/work", coverPath = "/albums/work/cover.jpg",
                manualCoverPath = "/albums/work/custom.png", coverThumbPath = "/thumbs/manual_v2.jpg",
            ))
            assertEquals(1, dao.updateAutomaticCoverIfSourceMatches(
                id, "/albums/work/cover.jpg", "", "/embedded/art.jpg", "/thumbs/embedded_v2.jpg",
            ))
            assertEquals("/albums/work/custom.png", dao.getAlbumById(id)?.manualCoverPath)
            assertEquals("/thumbs/manual_v2.jpg", dao.getAlbumById(id)?.coverThumbPath)
            assertEquals(0, dao.updateAutomaticCoverIfSourceMatches(
                id, "/albums/work/cover.jpg", "", "/stale.jpg", "/thumbs/stale_v2.jpg",
            ))
            assertEquals("/embedded/art.jpg", dao.getAlbumById(id)?.coverPath)
        }
    }

    @Test
    fun scanKeepsSelectionChangedAfterInitialReadAndDropsThumbnailWhenAutomaticSourceChanges() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        database.useDatabase {
            val dao = database.albumDao()
            val id = dao.insertAlbum(AlbumEntity(
                title = "作品", path = "/albums/work", coverPath = "/albums/work/cover.jpg",
                coverThumbPath = "/thumbs/original_v2.jpg",
            ))
            val beforeScan = requireNotNull(dao.getAlbumById(id))
            dao.setManualCoverPath(id, "/albums/work/custom.png")
            dao.upsertImportedAlbum(beforeScan.copy(title = "扫描标题", coverPath = "/embedded/art.jpg"))
            assertEquals("/albums/work/custom.png", dao.getAlbumById(id)?.manualCoverPath)
            assertEquals("/embedded/art.jpg", dao.getAlbumById(id)?.coverPath)
            assertEquals("", dao.getAlbumById(id)?.coverThumbPath)
            val selected = requireNotNull(dao.getAlbumById(id))
            dao.setManualCoverPath(id, "")
            dao.updateAlbumCoverThumbIfSourceMatches(id, "", selected.coverPath, selected.coverUrl, "/thumbs/embedded_v2.jpg")
            dao.upsertImportedAlbum(selected.copy(coverPath = "/albums/work/new-cover.jpg"))
            assertEquals("", dao.getAlbumById(id)?.manualCoverPath)
            assertEquals("", dao.getAlbumById(id)?.coverThumbPath)
        }
    }

    @Test
    fun migration35To36PreservesAutomaticCoverAndValidatesRoomSchema() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = File.createTempFile("album-migration-", ".db").absolutePath
        fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabaseMigrations.MIGRATION_35_36, AppDatabaseMigrations.MIGRATION_36_37)
            .allowMainThreadQueries().build()
        try {
            val original = AlbumEntity(
                title = "保留的作品", path = "/albums/work", coverPath = "/albums/work/cover.jpg",
                coverThumbPath = "/thumbs/automatic_v2.jpg",
            )
            val id = open().useDatabase { database ->
                val id = database.albumDao().insertAlbum(original)
                removeManualCoverColumnForLegacySchema(database.openHelper.writableDatabase)
                database.openHelper.writableDatabase.version = 35
                id
            }
            open().useDatabase { database ->
                assertEquals(original.copy(id = id), database.albumDao().getAlbumById(id))
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
    private inline fun <T> AppDatabase.useDatabase(block: (AppDatabase) -> T): T =
        try { block(this) } finally { close() }
}
