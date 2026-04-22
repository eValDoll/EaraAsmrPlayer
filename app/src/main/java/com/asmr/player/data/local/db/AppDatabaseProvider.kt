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
        return Room.databaseBuilder(
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
                AppDatabaseMigrations.MIGRATION_17_18,
                AppDatabaseMigrations.MIGRATION_18_19
            )
            // Never wipe the local database during app upgrades.
            // If a migration is missing, fail loudly so user data can still be recovered.
            .build()
    }
}
