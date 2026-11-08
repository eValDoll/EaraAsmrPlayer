package com.asmr.player.data.local.db

import androidx.room.Room
import com.asmr.player.LocalizedRobolectricTest
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.data.local.db.entities.DownloadTaskEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import java.io.File

class TagMetadataMigrationTest : LocalizedRobolectricTest() {
    @Test fun migrationKeepsLegacyTagIdentityAndDownloads() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = File.createTempFile("tag-migration-", ".db").absolutePath
        fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabaseMigrations.MIGRATION_37_38).allowMainThreadQueries().build()
        try {
            val album = AlbumEntity(title = "作品", path = "/work", tags = "耳かき,我的标签")
            val task = DownloadTaskEntity(taskKey = "legacy", title = "下载", rootDir = "/downloads", albumTagsCsv = "耳かき", createdAt = 1, updatedAt = 2)
            var albumId = 0L
            var taskId = 0L
            open().useDatabase { db ->
                albumId = db.albumDao().insertAlbum(album)
                taskId = db.downloadDao().insertTask(task)
                removeTagMetadataColumnsForLegacySchema(db.openHelper.writableDatabase)
                db.openHelper.writableDatabase.version = 37
            }
            open().useDatabase { db ->
                assertEquals(album.copy(id = albumId), db.albumDao().getAlbumById(albumId))
                assertEquals(task.copy(id = taskId), db.downloadDao().getTaskById(taskId))
                assertEquals(38, db.openHelper.writableDatabase.version)
            }
        } finally { context.deleteDatabase(name) }
    }
    private inline fun <T> AppDatabase.useDatabase(block: (AppDatabase) -> T): T = try { block(this) } finally { close() }
}
