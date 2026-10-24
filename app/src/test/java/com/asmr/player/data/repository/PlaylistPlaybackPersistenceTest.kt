package com.asmr.player.data.repository

import android.app.Application
import androidx.room.Room
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.AppDatabaseMigrations
import com.asmr.player.data.local.db.entities.PlaylistEntity
import com.asmr.player.data.local.db.entities.PlaylistItemEntity
import com.asmr.player.data.local.db.entities.PlaylistPlaybackEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class PlaylistPlaybackPersistenceTest {
    @Test
    fun remoteTrackProgressIsIndependentPerPlaylistAndSurvivesReopen() = runBlocking {
        withDatabase { open ->
            val (favoritesId, userId) = open().useDatabase { database ->
                val favorites = addPlaylist(database, "我的收藏")
                val user = addPlaylist(database, "晚安时刻")
                val repository = PlaylistPlaybackRepository(database.playlistPlaybackDao())
                repository.recordPlayback(favorites, "first", 12_000L, 180_000L, updatedAt = 1L)
                repository.recordPlayback(user, "first", 72_000L, 180_000L, updatedAt = 2L)
                favorites to user
            }
            open().useDatabase { database ->
                val repository = PlaylistPlaybackRepository(database.playlistPlaybackDao())
                assertEquals(12_000L, repository.observePlayback(favoritesId).first()?.positionMs)
                assertEquals(72_000L, repository.observePlayback(userId).first()?.positionMs)
                repository.recordPlayback(favoritesId, "second", 0L, 0L, updatedAt = 3L)
                assertEquals("second", repository.observePlayback(favoritesId).first()?.mediaId)
                assertEquals("first", repository.observePlayback(userId).first()?.mediaId)
            }
        }
    }

    @Test
    fun removingLastPlayedTrackClearsItsRecordAndLateWritesCannotRestoreIt() = runBlocking {
        withDatabase { open ->
            open().useDatabase { database ->
                val firstList = addPlaylist(database, "列表甲")
                val secondList = addPlaylist(database, "列表乙")
                val repository = PlaylistPlaybackRepository(database.playlistPlaybackDao())
                repository.recordPlayback(firstList, "first", 9_000L, 30_000L)
                repository.recordPlayback(secondList, "first", 4_000L, 30_000L)

                database.playlistItemDao().deleteItem(firstList, "first")
                assertNull(repository.observePlayback(firstList).first())
                repository.recordPlayback(firstList, "first", 10_000L, 30_000L)
                assertNull(repository.observePlayback(firstList).first())
                assertEquals(4_000L, repository.observePlayback(secondList).first()?.positionMs)
                assertEquals(listOf("second"), database.playlistItemDao().getItemsOnce(firstList).map { it.mediaId })
            }
        }
    }

    @Test
    fun reorderAndRenameKeepPlaybackButDeletingPlaylistClearsIt() = runBlocking {
        withDatabase { open ->
            open().useDatabase { database ->
                val playlistId = addPlaylist(database, "睡前")
                val repository = PlaylistPlaybackRepository(database.playlistPlaybackDao())
                val playlists = PlaylistRepository(
                    database.playlistDao(), database.playlistItemDao(), database.trackDao(), database.albumDao()
                )
                repository.recordPlayback(playlistId, "first", 3_000L, 40_000L)
                val before = repository.observePlayback(playlistId).first()

                playlists.movePlaylistItemToTop(playlistId, "second")
                playlists.renamePlaylist(playlistId, "晚安")
                assertEquals(before, repository.observePlayback(playlistId).first())
                assertEquals(listOf("second", "first"), database.playlistItemDao().getItemsOnce(playlistId).map { it.mediaId })

                playlists.deletePlaylist(requireNotNull(database.playlistDao().getPlaylistByIdOnce(playlistId)))
                assertNull(repository.observePlayback(playlistId).first())
                repository.recordPlayback(playlistId, "first", 5_000L, 40_000L)
                assertNull(repository.observePlayback(playlistId).first())
            }
        }
    }

    @Test
    fun olderPendingWritesCannotReplaceNewerTrackAndPositionsAreClamped() = runBlocking {
        withDatabase { open ->
            open().useDatabase { database ->
                val playlistId = addPlaylist(database, "晚安")
                val repository = PlaylistPlaybackRepository(database.playlistPlaybackDao())
                repository.recordPlayback(playlistId, "second", 100_000L, 20_000L, true, 200L)
                repository.recordPlayback(playlistId, "first", 5_000L, 10_000L, updatedAt = 100L)
                assertEquals(
                    PlaylistPlaybackEntity(playlistId, "second", 20_000L, 20_000L, true, 200L),
                    repository.observePlayback(playlistId).first()
                )

                repository.recordPlayback(playlistId, "first", -5L, -1L, updatedAt = 300L)
                assertEquals(
                    PlaylistPlaybackEntity(playlistId, "first", 0L, 0L, false, 300L),
                    repository.observePlayback(playlistId).first()
                )
                repository.recordPlayback(-1L, "first", 1L, 1L)
                repository.recordPlayback(playlistId, "", 1L, 1L)
                assertNull(repository.observePlayback(-1L).first())
            }
        }
    }

    @Test
    fun migration36To37PreservesPlaylistOrderAndValidatesRoomSchema() = runBlocking {
        withDatabase { open ->
            val playlistId = open().useDatabase { database ->
                val playlistId = addPlaylist(database, "保留的收藏")
                database.openHelper.writableDatabase.execSQL("DROP TABLE playlist_playback")
                database.openHelper.writableDatabase.version = 36
                playlistId
            }
            open().useDatabase { database ->
                assertEquals("保留的收藏", database.playlistDao().getPlaylistByIdOnce(playlistId)?.name)
                val items = database.playlistItemDao().getItemsOnce(playlistId)
                assertEquals(listOf("first", "second"), items.map { it.mediaId })
                assertEquals(listOf(0, 1), items.map { it.itemOrder })
                assertEquals("ja\thttps://example.com/first.vtt\tvtt", items.first().remoteSubtitleSources)
                assertEquals(37, database.openHelper.writableDatabase.version)
                val repository = PlaylistPlaybackRepository(database.playlistPlaybackDao())
                assertNull(repository.observePlayback(playlistId).first())
                repository.recordPlayback(playlistId, "first", 1_000L, 20_000L)
                assertEquals(1_000L, repository.observePlayback(playlistId).first()?.positionMs)
                database.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use {
                    assertTrue(!it.moveToFirst())
                }
            }
        }
    }

    private suspend fun addPlaylist(database: AppDatabase, name: String): Long {
        val id = database.playlistDao().insertPlaylist(PlaylistEntity(name = name, category = "user"))
        database.playlistItemDao().upsertItems(listOf("first", "second").mapIndexed { index, mediaId ->
            PlaylistItemEntity(
                playlistId = id,
                mediaId = mediaId,
                title = "音轨 $mediaId",
                uri = "https://example.com/$mediaId.mp3",
                remoteSubtitleSources = "ja\thttps://example.com/$mediaId.vtt\tvtt",
                itemOrder = index
            )
        })
        return id
    }

    private suspend fun withDatabase(block: suspend (() -> AppDatabase) -> Unit) {
        val context = RuntimeEnvironment.getApplication()
        val name = File.createTempFile("playlist-playback-", ".db").absolutePath
        try {
            block {
                Room.databaseBuilder(context, AppDatabase::class.java, name)
                    .addMigrations(AppDatabaseMigrations.MIGRATION_36_37)
                    .allowMainThreadQueries()
                    .build()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    private inline fun <T> AppDatabase.useDatabase(block: (AppDatabase) -> T): T =
        try { block(this) } finally { close() }
}
