package com.asmr.player.data.local.db

import android.app.Application
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class AppDatabaseMigrationsTest {
    @Test
    fun migration28To29_preservesTaskItemAndAddsEmptyModelSnapshot() {
        val context = RuntimeEnvironment.getApplication()
        val dbName = "migration-test-${System.nanoTime()}.db"
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()
        if (dbFile.exists()) dbFile.delete()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(28) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE subtitle_task_items (" +
                                "`id` TEXT NOT NULL PRIMARY KEY, `trackTitle` TEXT NOT NULL, " +
                                "`transcriptionProgress` INTEGER NOT NULL)"
                        )
                        db.execSQL(
                            "INSERT INTO subtitle_task_items VALUES('item-1','晚安音声',37)"
                        )
                    }

                    override fun onUpgrade(
                        db: androidx.sqlite.db.SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) = Unit
                })
                .build()
        )
        val db = helper.writableDatabase

        AppDatabaseMigrations.MIGRATION_28_29.migrate(db)

        db.query(
            "SELECT trackTitle, transcriptionProgress, transcriptionModelId " +
                "FROM subtitle_task_items WHERE id = 'item-1'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("晚安音声", cursor.getString(0))
            assertEquals(37, cursor.getInt(1))
            assertEquals("", cursor.getString(2))
        }

        db.close()
        helper.close()
        File(dbFile.absolutePath).delete()
        File(dbFile.absolutePath + "-wal").delete()
        File(dbFile.absolutePath + "-shm").delete()
    }

    @Test
    fun migration27To28_preservesChineseAndAddsHiddenJapaneseText() {
        val context = RuntimeEnvironment.getApplication()
        val dbName = "migration-test-${System.nanoTime()}.db"
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()
        if (dbFile.exists()) dbFile.delete()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(27) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE subtitles (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "`trackId` INTEGER NOT NULL, `startMs` INTEGER NOT NULL, `endMs` INTEGER NOT NULL, `text` TEXT NOT NULL)"
                        )
                        db.execSQL(
                            "INSERT INTO subtitles(trackId,startMs,endMs,text) VALUES(7,0,1000,'晚安')"
                        )
                    }

                    override fun onUpgrade(
                        db: androidx.sqlite.db.SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) = Unit
                })
                .build()
        )
        val db = helper.writableDatabase

        AppDatabaseMigrations.MIGRATION_27_28.migrate(db)

        db.query("SELECT text, japaneseText FROM subtitles WHERE trackId = 7").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("晚安", cursor.getString(0))
            assertEquals("", cursor.getString(1))
        }

        db.close()
        helper.close()
        File(dbFile.absolutePath).delete()
        File(dbFile.absolutePath + "-wal").delete()
        File(dbFile.absolutePath + "-shm").delete()
    }

    @Test
    fun migration25To26_preservesSubtitlesAndDropsLegacyTaskResults() {
        val context = RuntimeEnvironment.getApplication()
        val dbName = "migration-test-${System.nanoTime()}.db"
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()
        if (dbFile.exists()) dbFile.delete()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(25) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE tracks (`id` INTEGER NOT NULL PRIMARY KEY)")
                        db.execSQL(
                            "CREATE TABLE subtitles (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "`trackId` INTEGER NOT NULL, `startMs` INTEGER NOT NULL, `endMs` INTEGER NOT NULL, `text` TEXT NOT NULL)"
                        )
                        db.execSQL(
                            "CREATE TABLE subtitle_task_results (`trackId` INTEGER NOT NULL PRIMARY KEY, " +
                                "`workId` TEXT NOT NULL, `state` TEXT NOT NULL, `message` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL)"
                        )
                        db.execSQL("INSERT INTO tracks(id) VALUES(7)")
                        db.execSQL("INSERT INTO subtitles(trackId,startMs,endMs,text) VALUES(7,0,1000,'保留的字幕')")
                        db.execSQL("INSERT INTO subtitle_task_results VALUES(7,'legacy','SUCCEEDED','不可信结果',100)")
                    }

                    override fun onUpgrade(
                        db: androidx.sqlite.db.SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) = Unit
                })
                .build()
        )
        val db = helper.writableDatabase

        AppDatabaseMigrations.MIGRATION_25_26.migrate(db)

        db.query("SELECT text FROM subtitles WHERE trackId = 7").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("保留的字幕", cursor.getString(0))
        }
        db.query("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='subtitle_task_results'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM subtitle_task_items").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }

        db.close()
        helper.close()
        File(dbFile.absolutePath).delete()
        File(dbFile.absolutePath + "-wal").delete()
        File(dbFile.absolutePath + "-shm").delete()
    }

    @Test
    fun migration24To25_createsEmptyPerTrackSubtitleTaskResults() {
        val context = RuntimeEnvironment.getApplication()
        val dbName = "migration-test-${System.nanoTime()}.db"
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()
        if (dbFile.exists()) dbFile.delete()

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(24) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE tracks (`id` INTEGER NOT NULL PRIMARY KEY)")
                    }

                    override fun onUpgrade(
                        db: androidx.sqlite.db.SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) = Unit
                })
                .build()
        )
        val db = helper.writableDatabase
        AppDatabaseMigrations.MIGRATION_24_25.migrate(db)

        db.query("SELECT COUNT(*) FROM subtitle_task_results").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        db.execSQL("INSERT INTO tracks(id) VALUES(7)")
        db.execSQL(
            "INSERT INTO subtitle_task_results(trackId, workId, state, message, updatedAt) " +
                "VALUES(7, 'task-id', 'FAILED', '本次翻译失败', 100)"
        )
        db.query("SELECT state, message FROM subtitle_task_results WHERE trackId = 7").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("FAILED", cursor.getString(0))
            assertEquals("本次翻译失败", cursor.getString(1))
        }

        db.close()
        helper.close()
        File(dbFile.absolutePath).delete()
        File(dbFile.absolutePath + "-wal").delete()
        File(dbFile.absolutePath + "-shm").delete()
    }

    @Test
    fun migration20To21_addsPlaylistPlaybackContextColumns() {
        val context = RuntimeEnvironment.getApplication()
        val dbName = "migration-test-${System.nanoTime()}.db"
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()
        if (dbFile.exists()) dbFile.delete()

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(20) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS `playlist_items` (
                                `playlistId` INTEGER NOT NULL,
                                `mediaId` TEXT NOT NULL,
                                `title` TEXT NOT NULL,
                                `artist` TEXT NOT NULL DEFAULT '',
                                `uri` TEXT NOT NULL,
                                `artworkUri` TEXT NOT NULL DEFAULT '',
                                `itemOrder` INTEGER NOT NULL DEFAULT 0,
                                PRIMARY KEY(`playlistId`, `mediaId`)
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            "INSERT INTO playlist_items(playlistId, mediaId, title, artist, uri, artworkUri, itemOrder) VALUES(1, 'a', 'Track A', 'Artist', 'file:///a.mp3', '', 0)"
                        )
                    }

                    override fun onUpgrade(
                        db: androidx.sqlite.db.SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) = Unit
                })
                .build()
        )
        val database = helper.writableDatabase
        AppDatabaseMigrations.MIGRATION_20_21.migrate(database)

        val cursor = database.query(
            "SELECT albumTitle, albumId, trackId, rjCode, albumWorkId, trackGroup, lyricsRelativePathNoExt, mimeType, isVideo FROM playlist_items WHERE playlistId = 1 AND mediaId = 'a'"
        )
        cursor.use {
            it.moveToFirst()
            assertEquals("", it.getString(0))
            assertEquals(0L, it.getLong(1))
            assertEquals(0L, it.getLong(2))
            assertEquals("", it.getString(3))
            assertEquals("", it.getString(4))
            assertEquals("", it.getString(5))
            assertEquals("", it.getString(6))
            assertEquals("", it.getString(7))
            assertEquals(0, it.getInt(8))
        }

        database.close()
        helper.close()
        File(dbFile.absolutePath).delete()
        File(dbFile.absolutePath + "-wal").delete()
        File(dbFile.absolutePath + "-shm").delete()
    }
}
