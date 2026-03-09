package com.asmr.player.data.local.db

import android.content.Context
import androidx.room.Room

object AppDatabaseProvider {
    @Volatile
    private var instance: AppDatabase? = null

    fun get(context: Context): AppDatabase {
        return instance ?: synchronized(this) {
            instance ?: buildDatabase(context).also { instance = it }
        }
    }

    private fun buildDatabase(context: Context): AppDatabase {
        val appContext = context.applicationContext
        val builder = Room.databaseBuilder(
            appContext,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            .addMigrations(
                AppDatabaseMigrations.MIGRATION_4_5,
                AppDatabaseMigrations.MIGRATION_5_6,
                AppDatabaseMigrations.MIGRATION_6_7,
                AppDatabaseMigrations.MIGRATION_7_8,
                AppDatabaseMigrations.MIGRATION_8_9,
                AppDatabaseMigrations.MIGRATION_9_10,
                AppDatabaseMigrations.MIGRATION_10_11,
                AppDatabaseMigrations.MIGRATION_12_13,
                AppDatabaseMigrations.MIGRATION_13_14,
                AppDatabaseMigrations.MIGRATION_14_15,
                AppDatabaseMigrations.MIGRATION_15_16,
                AppDatabaseMigrations.MIGRATION_16_17,
                AppDatabaseMigrations.MIGRATION_17_18
            )
            .fallbackToDestructiveMigration()
            .fallbackToDestructiveMigrationOnDowngrade()

        val db = builder.build()
        val openResult = runCatching { db.openHelper.writableDatabase }
        if (openResult.isSuccess) return db

        val msg = openResult.exceptionOrNull()?.message.orEmpty()
        val shouldReset = msg.contains("Room cannot verify the data integrity", ignoreCase = true) ||
            msg.contains("A migration from", ignoreCase = true)
        if (!shouldReset) {
            openResult.getOrThrow()
        }

        runCatching { db.close() }
        runCatching { appContext.deleteDatabase(AppDatabase.DATABASE_NAME) }
        return builder.build()
    }
}
