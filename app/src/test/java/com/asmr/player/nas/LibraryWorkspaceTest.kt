package com.asmr.player.nas

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.asmr.player.LocalizedRobolectricTest
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.AppDatabaseProvider
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.data.local.db.entities.AlbumGroupEntity
import com.asmr.player.data.local.db.entities.DownloadTaskEntity
import com.asmr.player.data.local.db.entities.PlaylistItemEntity
import com.asmr.player.data.local.db.entities.PlaylistEntity
import com.asmr.player.data.remote.download.DownloadDestinationStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LibraryWorkspaceTest : LocalizedRobolectricTest() {
    @Test fun nasDatabaseIsSelectedBeforeApplicationOnCreate() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("library_workspace", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("nas", true).putString("address", "https://nas.test/audio/").commit()
        try {
            android.app.Instrumentation.newApplication(com.asmr.player.AsmrApp::class.java, context)
            val database = AppDatabaseProvider.buildDatabase(context)
            try {
                val expected = "nas_${WebDavLocation("https://nas.test/audio/", "").id}_asmr_player.db"
                assertEquals(expected, database.openHelper.databaseName)
            } finally {
                database.close()
            }
        } finally {
            prefs.edit().clear().commit()
            LibraryWorkspace.initialize(context)
        }
    }

    @Test fun sameAlbumTasksAndCollectionsCanBeEditedIndependently() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("library_workspace", Context.MODE_PRIVATE)
        val work = "RJ123456"
        fun selectNas() {
            prefs.edit().putBoolean("nas", true).putString("address", "https://nas.test/audio/").apply()
            LibraryWorkspace.initialize(context)
        }
        suspend fun withDatabase(block: suspend (AppDatabase) -> Unit) {
            val db = AppDatabaseProvider.buildDatabase(context)
            try { block(db) } finally { db.close() }
        }
        suspend fun populate(db: AppDatabase, title: String) {
            db.albumDao().upsertImportedAlbum(AlbumEntity(title = title, path = "web://$work", rjCode = work, workId = work))
            db.downloadDao().insertTask(DownloadTaskEntity(taskKey = work, title = title, rootDir = title, createdAt = 1, updatedAt = 1))
            val favorites = db.playlistDao().insertPlaylist(PlaylistEntity(name = "我的收藏", category = "system"))
            db.playlistItemDao().upsertItems(listOf(PlaylistItemEntity(favorites, work, title, uri = "https://audio.test/$work")))
            db.playlistDao().insertPlaylist(PlaylistEntity(name = "夜间", category = title))
            db.albumGroupDao().insertGroup(AlbumGroupEntity(name = title))
        }
        LibraryWorkspace.initialize(context)
        assertEquals("asmr_player.db", LibraryWorkspace.name(AppDatabase.DATABASE_NAME))
        val localRoot = DownloadDestinationStore(context).current().root
        val localTag = LibraryWorkspace.workerTag
        try {
            withDatabase { populate(it, "local") }
            selectNas()
            val nasTag = LibraryWorkspace.workerTag
            assertFalse(LibraryWorkspace.ownsWork(setOf(localTag)))
            assertFalse(LibraryWorkspace.ownsWork(emptySet()))
            assertTrue(LibraryWorkspace.ownsWork(setOf(nasTag)))
            assertNotEquals(localRoot, DownloadDestinationStore(context).current().root)
            withDatabase { db ->
                assertTrue(db.albumDao().getAllAlbumsOnce().isEmpty())
                assertNull(db.playlistDao().getPlaylistByNameOnce("我的收藏"))
                assertNull(db.albumGroupDao().getGroupByNameOnce("local"))
                populate(db, "nas")
                assertEquals("nas", db.albumDao().getAlbumByWorkIdOnce(work)?.title)
            }
            LibraryWorkspace.select(context, null)
            LibraryWorkspace.initialize(context)
            assertTrue(LibraryWorkspace.ownsWork(emptySet()))
            assertFalse(LibraryWorkspace.ownsWork(setOf(nasTag)))
            withDatabase { db ->
                assertEquals("local", db.albumDao().getAlbumByWorkIdOnce(work)?.title)
                assertEquals("local", db.downloadDao().getTaskByKey(work)?.title)
                val favorites = checkNotNull(db.playlistDao().getPlaylistByNameOnce("我的收藏"))
                assertEquals("local", db.playlistItemDao().getItemsOnce(favorites.id).single().title)
                assertEquals("local", db.playlistDao().getPlaylistByNameOnce("夜间")?.category)
                assertNull(db.albumGroupDao().getGroupByNameOnce("nas"))
            }
        } finally {
            prefs.edit().clear().commit()
            LibraryWorkspace.initialize(context)
        }
    }
}
