package com.asmr.player.data.local.db

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationAndroidTest {
    @Test
    fun migration28To29_preservesItemAndAddsModelSnapshot() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "migration-28-29-${System.nanoTime()}.db"
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(object : SupportSQLiteOpenHelper.Callback(28) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE subtitle_task_items (" +
                                "`id` TEXT NOT NULL PRIMARY KEY, `trackTitle` TEXT NOT NULL)"
                        )
                        db.execSQL("INSERT INTO subtitle_task_items VALUES('item-1','晚安音声')")
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) = Unit
                })
                .build()
        )
        try {
            val database = helper.writableDatabase
            AppDatabaseMigrations.MIGRATION_28_29.migrate(database)
            database.query(
                "SELECT trackTitle, transcriptionModelId " +
                    "FROM subtitle_task_items WHERE id = 'item-1'"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("晚安音声", cursor.getString(0))
                assertEquals("", cursor.getString(1))
            }
        } finally {
            helper.close()
            context.deleteDatabase(databaseName)
        }
    }
}
