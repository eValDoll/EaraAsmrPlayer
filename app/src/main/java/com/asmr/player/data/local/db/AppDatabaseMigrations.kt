package com.asmr.player.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object AppDatabaseMigrations {
    val MIGRATION_4_5: Migration = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `download_tasks` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `taskKey` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `rootDir` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_download_tasks_taskKey` ON `download_tasks` (`taskKey`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `download_items` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `taskId` INTEGER NOT NULL,
                    `workId` TEXT NOT NULL,
                    `url` TEXT NOT NULL,
                    `relativePath` TEXT NOT NULL,
                    `fileName` TEXT NOT NULL,
                    `targetDir` TEXT NOT NULL,
                    `filePath` TEXT NOT NULL,
                    `state` TEXT NOT NULL,
                    `downloaded` INTEGER NOT NULL,
                    `total` INTEGER NOT NULL,
                    `speed` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    FOREIGN KEY(`taskId`) REFERENCES `download_tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_download_items_taskId` ON `download_items` (`taskId`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_download_items_workId` ON `download_items` (`workId`)")
        }
    }

    val MIGRATION_5_6: Migration = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE tracks ADD COLUMN `group` TEXT NOT NULL DEFAULT ''")
        }
    }

    val MIGRATION_6_7: Migration = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `playlists` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `category` TEXT NOT NULL DEFAULT 'default',
                    `createdAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )

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
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_items_playlistId` ON `playlist_items` (`playlistId`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `playlist_track_cross_ref` (
                    `playlistId` INTEGER NOT NULL,
                    `trackId` INTEGER NOT NULL,
                    `trackOrder` INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(`playlistId`, `trackId`)
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `subtitles` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `trackId` INTEGER NOT NULL,
                    `startMs` INTEGER NOT NULL,
                    `endMs` INTEGER NOT NULL,
                    `text` TEXT NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_subtitles_trackId` ON `subtitles` (`trackId`)")
        }
    }

    val MIGRATION_7_8: Migration = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `tags` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `nameNormalized` TEXT NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_name` ON `tags` (`name`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_nameNormalized` ON `tags` (`nameNormalized`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `album_tag` (
                    `albumId` INTEGER NOT NULL,
                    `tagId` INTEGER NOT NULL,
                    `source` INTEGER NOT NULL,
                    PRIMARY KEY(`albumId`, `tagId`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_album_tag_albumId` ON `album_tag` (`albumId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_album_tag_tagId` ON `album_tag` (`tagId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_album_tag_source` ON `album_tag` (`source`)")

            db.execSQL(
                """
                CREATE VIRTUAL TABLE IF NOT EXISTS `album_fts`
                USING FTS4(`title`, `circle`, `cv`, `rjCode`, `workId`, `tagsToken`)
                """.trimIndent()
            )

            db.execSQL(
                """
                INSERT OR REPLACE INTO `album_fts`(`rowid`, `title`, `circle`, `cv`, `rjCode`, `workId`, `tagsToken`)
                SELECT `id`,
                       IFNULL(`title`, ''),
                       IFNULL(`circle`, ''),
                       IFNULL(`cv`, ''),
                       IFNULL(`rjCode`, ''),
                       IFNULL(`workId`, ''),
                       TRIM(REPLACE(IFNULL(`tags`, ''), ',', ' '))
                FROM `albums`
                """.trimIndent()
            )
        }
    }

    val MIGRATION_8_9: Migration = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `album_play_stats` (
                    `albumId` INTEGER NOT NULL,
                    `lastPlayedAt` INTEGER NOT NULL,
                    `playCount` INTEGER NOT NULL,
                    PRIMARY KEY(`albumId`)
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_9_10: Migration = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `track_tag` (
                    `trackId` INTEGER NOT NULL,
                    `tagId` INTEGER NOT NULL,
                    `source` INTEGER NOT NULL,
                    PRIMARY KEY(`trackId`, `tagId`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_track_tag_trackId` ON `track_tag` (`trackId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_track_tag_tagId` ON `track_tag` (`tagId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_track_tag_source` ON `track_tag` (`source`)")
        }
    }

    val MIGRATION_10_11: Migration = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `local_tree_cache` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `albumId` INTEGER NOT NULL,
                    `cacheKey` TEXT NOT NULL,
                    `stamp` INTEGER NOT NULL,
                    `payloadJson` TEXT NOT NULL,
                    `updatedAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_local_tree_cache_albumId_cacheKey` ON `local_tree_cache` (`albumId`, `cacheKey`)")
        }
    }

    val MIGRATION_12_13: Migration = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE download_tasks ADD COLUMN `subtitle` TEXT NOT NULL DEFAULT ''")
        }
    }

    val MIGRATION_13_14: Migration = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `remote_subtitle_sources` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `trackId` INTEGER NOT NULL,
                    `url` TEXT NOT NULL,
                    `language` TEXT NOT NULL,
                    `ext` TEXT NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_remote_subtitle_sources_trackId` ON `remote_subtitle_sources` (`trackId`)")
        }
    }

    val MIGRATION_14_15: Migration = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE albums ADD COLUMN `coverThumbPath` TEXT NOT NULL DEFAULT ''")
        }
    }

    val MIGRATION_15_16: Migration = object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `album_groups` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `album_group_items` (
                    `groupId` INTEGER NOT NULL,
                    `mediaId` TEXT NOT NULL,
                    `itemOrder` INTEGER NOT NULL DEFAULT 0,
                    `createdAt` INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(`groupId`, `mediaId`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_album_group_items_groupId` ON `album_group_items` (`groupId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_album_group_items_mediaId` ON `album_group_items` (`mediaId`)")

            db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_items_mediaId` ON `playlist_items` (`mediaId`)")

            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS `trg_tracks_delete_cleanup_lists`
                AFTER DELETE ON `tracks`
                BEGIN
                    DELETE FROM `playlist_items` WHERE `mediaId` = OLD.`path`;
                    DELETE FROM `album_group_items` WHERE `mediaId` = OLD.`path`;
                END
                """.trimIndent()
            )
        }
    }

    val MIGRATION_16_17: Migration = object : Migration(16, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `track_slices` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `trackMediaId` TEXT NOT NULL,
                    `startMs` INTEGER NOT NULL,
                    `endMs` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_track_slices_trackMediaId` ON `track_slices` (`trackMediaId`)")
        }
    }

    val MIGRATION_17_18: Migration = object : Migration(17, 18) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `track_playback_progress` (
                    `mediaId` TEXT NOT NULL,
                    `albumId` INTEGER,
                    `trackId` INTEGER,
                    `positionMs` INTEGER NOT NULL,
                    `durationMs` INTEGER NOT NULL,
                    `completed` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`mediaId`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_track_playback_progress_albumId` ON `track_playback_progress` (`albumId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_track_playback_progress_updatedAt` ON `track_playback_progress` (`updatedAt`)")
        }
    }

    val MIGRATION_18_19: Migration = object : Migration(18, 19) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE download_tasks ADD COLUMN `albumTitle` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE download_tasks ADD COLUMN `albumCircle` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE download_tasks ADD COLUMN `albumCv` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE download_tasks ADD COLUMN `albumTagsCsv` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE download_tasks ADD COLUMN `albumCoverUrl` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE download_tasks ADD COLUMN `albumDescription` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE download_tasks ADD COLUMN `albumWorkId` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE download_tasks ADD COLUMN `albumRjCode` TEXT NOT NULL DEFAULT ''")
        }
    }

    val MIGRATION_19_20: Migration = object : Migration(19, 20) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `manual_lyrics_sources` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `exactTargetKey` TEXT NOT NULL,
                    `fallbackTargetKey` TEXT NOT NULL,
                    `canonicalMediaId` TEXT NOT NULL,
                    `sourceUri` TEXT NOT NULL,
                    `displayName` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_manual_lyrics_sources_exactTargetKey` ON `manual_lyrics_sources` (`exactTargetKey`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_manual_lyrics_sources_fallbackTargetKey` ON `manual_lyrics_sources` (`fallbackTargetKey`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_manual_lyrics_sources_canonicalMediaId` ON `manual_lyrics_sources` (`canonicalMediaId`)"
            )
        }
    }

    val MIGRATION_20_21: Migration = object : Migration(20, 21) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE playlist_items ADD COLUMN `albumTitle` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE playlist_items ADD COLUMN `albumId` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE playlist_items ADD COLUMN `trackId` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE playlist_items ADD COLUMN `rjCode` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE playlist_items ADD COLUMN `albumWorkId` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE playlist_items ADD COLUMN `trackGroup` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE playlist_items ADD COLUMN `lyricsRelativePathNoExt` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE playlist_items ADD COLUMN `mimeType` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE playlist_items ADD COLUMN `isVideo` INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATION_21_22: Migration = object : Migration(21, 22) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE albums ADD COLUMN `audioTrackCount` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE albums ADD COLUMN `audioTotalDuration` REAL NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE albums ADD COLUMN `audioTotalSizeBytes` INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATION_22_23: Migration = object : Migration(22, 23) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `listening_sessions` (" +
                    "`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                    "`albumId` INTEGER NOT NULL DEFAULT -1, " +
                    "`rjCode` TEXT NOT NULL DEFAULT '', " +
                    "`title` TEXT NOT NULL DEFAULT '', " +
                    "`circle` TEXT NOT NULL DEFAULT '', " +
                    "`cv` TEXT NOT NULL DEFAULT '', " +
                    "`tags` TEXT NOT NULL DEFAULT '', " +
                    "`coverUrl` TEXT NOT NULL DEFAULT '', " +
                    "`coverPath` TEXT NOT NULL DEFAULT '', " +
                    "`coverThumbPath` TEXT NOT NULL DEFAULT '', " +
                    "`listeningDate` TEXT NOT NULL, " +
                    "`startAtMs` INTEGER NOT NULL, " +
                    "`lastActiveAtMs` INTEGER NOT NULL, " +
                    "`durationMs` INTEGER NOT NULL DEFAULT 0, " +
                    "`trafficBytes` INTEGER NOT NULL DEFAULT 0, " +
                    "`trackCount` INTEGER NOT NULL DEFAULT 0)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_listening_sessions_listeningDate` " +
                    "ON `listening_sessions` (`listeningDate`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_listening_sessions_albumId` " +
                    "ON `listening_sessions` (`albumId`)"
            )
        }
    }

    val MIGRATION_23_24: Migration = object : Migration(23, 24) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `online_saved_resources` (" +
                    "`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                    "`albumId` INTEGER NOT NULL, " +
                    "`relativePath` TEXT NOT NULL, " +
                    "`url` TEXT NOT NULL, " +
                    "`fileType` TEXT NOT NULL)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_online_saved_resources_albumId` " +
                    "ON `online_saved_resources` (`albumId`)"
            )
        }
    }

    val MIGRATION_24_25: Migration = object : Migration(24, 25) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `subtitle_task_results` (" +
                    "`trackId` INTEGER NOT NULL, " +
                    "`workId` TEXT NOT NULL, " +
                    "`state` TEXT NOT NULL, " +
                    "`message` TEXT NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`trackId`), " +
                    "FOREIGN KEY(`trackId`) REFERENCES `tracks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
        }
    }

    val MIGRATION_25_26: Migration = object : Migration(25, 26) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS `subtitle_task_results`")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `subtitle_tasks` (" +
                    "`id` TEXT NOT NULL, `origin` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                    "`rjCode` TEXT NOT NULL, `state` TEXT NOT NULL, `warning` TEXT NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `subtitle_task_items` (" +
                    "`id` TEXT NOT NULL, `taskId` TEXT NOT NULL, `trackId` INTEGER NOT NULL, " +
                    "`trackTitle` TEXT NOT NULL, `trackPath` TEXT NOT NULL, `mode` TEXT NOT NULL, " +
                    "`queueSequence` INTEGER NOT NULL, `state` TEXT NOT NULL, `suspendedFromState` TEXT NOT NULL, " +
                    "`transcriptionChunkCursor` INTEGER NOT NULL, `transcriptionProgress` INTEGER NOT NULL, " +
                    "`transcribedMs` INTEGER NOT NULL, `totalDurationMs` INTEGER NOT NULL, " +
                    "`translationCursor` INTEGER NOT NULL, `translationTotal` INTEGER NOT NULL, " +
                    "`translationBatchIndex` INTEGER NOT NULL, `translationBatchTotal` INTEGER NOT NULL, " +
                    "`attempt` INTEGER NOT NULL, `nextAttemptAt` INTEGER NOT NULL, `errorMessage` TEXT NOT NULL, " +
                    "`originalHash` TEXT NOT NULL, `lastPublishedHash` TEXT NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`taskId`) REFERENCES `subtitle_tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                    "FOREIGN KEY(`trackId`) REFERENCES `tracks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_subtitle_task_items_taskId` ON `subtitle_task_items` (`taskId`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_subtitle_task_items_trackId` ON `subtitle_task_items` (`trackId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_subtitle_task_items_state_queueSequence` ON `subtitle_task_items` (`state`, `queueSequence`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_subtitle_task_items_nextAttemptAt` ON `subtitle_task_items` (`nextAttemptAt`)")

            createItemChildTable(
                db = db,
                table = "subtitle_task_snapshots",
                columns = "`captionIndex` INTEGER NOT NULL, `startMs` INTEGER NOT NULL, `endMs` INTEGER NOT NULL, `text` TEXT NOT NULL",
                secondPrimaryKey = "captionIndex"
            )
            createItemChildTable(
                db = db,
                table = "subtitle_transcription_chunks",
                columns = "`chunkIndex` INTEGER NOT NULL, `startMs` INTEGER NOT NULL, `endMs` INTEGER NOT NULL, " +
                    "`segmentsJson` TEXT NOT NULL, `tokensJson` TEXT NOT NULL, `createdAt` INTEGER NOT NULL",
                secondPrimaryKey = "chunkIndex"
            )
            createItemChildTable(
                db = db,
                table = "subtitle_translation_sources",
                columns = "`sourceIndex` INTEGER NOT NULL, `startMs` INTEGER NOT NULL, `endMs` INTEGER NOT NULL, `text` TEXT NOT NULL",
                secondPrimaryKey = "sourceIndex"
            )
            createItemChildTable(
                db = db,
                table = "subtitle_fallback_captions",
                columns = "`captionIndex` INTEGER NOT NULL, `firstSourceIndex` INTEGER NOT NULL, `lastSourceIndex` INTEGER NOT NULL, " +
                    "`startMs` INTEGER NOT NULL, `endMs` INTEGER NOT NULL, `text` TEXT NOT NULL",
                secondPrimaryKey = "captionIndex"
            )
            createItemChildTable(
                db = db,
                table = "subtitle_committed_captions",
                columns = "`captionIndex` INTEGER NOT NULL, `firstSourceIndex` INTEGER NOT NULL, `lastSourceIndex` INTEGER NOT NULL, " +
                    "`startMs` INTEGER NOT NULL, `endMs` INTEGER NOT NULL, `correctedJapanese` TEXT NOT NULL, `chineseText` TEXT NOT NULL",
                secondPrimaryKey = "captionIndex"
            )
        }
    }

    val MIGRATION_26_27: Migration = object : Migration(26, 27) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE albums ADD COLUMN `displayTitle` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE tracks ADD COLUMN `displayTitle` TEXT NOT NULL DEFAULT ''")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `subtitle_title_owners` (
                    `taskId` TEXT NOT NULL,
                    `kind` TEXT NOT NULL,
                    `targetId` INTEGER NOT NULL,
                    `displayTitle` TEXT NOT NULL DEFAULT '',
                    `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`taskId`, `kind`, `targetId`),
                    FOREIGN KEY(`taskId`) REFERENCES `subtitle_tasks`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_subtitle_title_owners_kind_targetId` " +
                    "ON `subtitle_title_owners` (`kind`, `targetId`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_subtitle_title_owners_taskId` " +
                    "ON `subtitle_title_owners` (`taskId`)"
            )
        }
    }

    val MIGRATION_27_28: Migration = object : Migration(27, 28) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE subtitles ADD COLUMN `japaneseText` TEXT NOT NULL DEFAULT ''")
        }
    }

    private fun createItemChildTable(
        db: SupportSQLiteDatabase,
        table: String,
        columns: String,
        secondPrimaryKey: String
    ) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `$table` (`itemId` TEXT NOT NULL, $columns, " +
                "PRIMARY KEY(`itemId`, `$secondPrimaryKey`), " +
                "FOREIGN KEY(`itemId`) REFERENCES `subtitle_task_items`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_${table}_itemId` ON `$table` (`itemId`)")
    }
}
