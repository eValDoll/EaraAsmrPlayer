package com.asmr.player.data.local.db

import androidx.room.Room
import com.asmr.player.LocalizedRobolectricTest
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.robolectric.RuntimeEnvironment

class AsmrOneCloudDatabaseTest : LocalizedRobolectricTest() {
    @Test fun cacheAndOutboxSurviveDatabaseReopenWithoutTouchingLibrary() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "cloud-test-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, AsmrOneCloudDatabase::class.java, name).build()
        val list = AsmrPlaylistCache("alice", "id", "{}", 0)
        val operation = AsmrMembershipOperation("alice", "id", 42, true, "{}", "intent", 1)
        try {
            open().let { db ->
                try {
                    db.cloudDao().putPlaylists(listOf(list))
                    db.cloudDao().putOperation(operation)
                } finally { db.close() }
            }
            open().let { db ->
                try {
                    assertEquals(listOf(list), db.cloudDao().playlists("alice").first())
                    assertEquals(listOf(operation), db.cloudDao().pending("alice"))
                    assertTrue(db.cloudDao().pending("bob").isEmpty())
                    db.cloudDao().finish("alice", "id", 42, "old-intent")
                    assertEquals(1, db.cloudDao().pending("alice").size)
                    db.cloudDao().finish("alice", "id", 42, "intent")
                    assertTrue(db.cloudDao().pending("alice").isEmpty())
                } finally { db.close() }
            }
            assertNotEquals(AppDatabase.DATABASE_NAME, name)
        } finally { context.deleteDatabase(name) }
    }
}
