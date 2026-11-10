package com.asmr.player.data.local.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "cloud_playlists", primaryKeys = ["account", "id"])
data class AsmrPlaylistCache(val account: String, val id: String, val payload: String, val position: Int)

@Entity(tableName = "cloud_pages", primaryKeys = ["account", "listId", "page"])
data class AsmrWorkPageCache(val account: String, val listId: String, val page: Int, val payload: String)

@Entity(tableName = "cloud_operations", primaryKeys = ["account", "listId", "workId"])
data class AsmrMembershipOperation(
    val account: String, val listId: String, val workId: Long, val desired: Boolean,
    val workPayload: String, val version: String, val createdAt: Long, val blocked: Boolean = false
)

@Dao
interface AsmrOneCloudDao {
    @Query("SELECT * FROM cloud_playlists WHERE account = :account ORDER BY position")
    fun playlists(account: String): Flow<List<AsmrPlaylistCache>>
    @Query("SELECT * FROM cloud_pages WHERE account = :account AND listId = :listId ORDER BY page")
    fun pages(account: String, listId: String): Flow<List<AsmrWorkPageCache>>
    @Query("SELECT * FROM cloud_operations WHERE account = :account ORDER BY createdAt")
    fun operations(account: String): Flow<List<AsmrMembershipOperation>>
    @Query("SELECT * FROM cloud_operations WHERE account = :account AND blocked = 0 ORDER BY createdAt LIMIT 20")
    suspend fun pending(account: String): List<AsmrMembershipOperation>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putPlaylists(values: List<AsmrPlaylistCache>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putPage(value: AsmrWorkPageCache)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putOperation(value: AsmrMembershipOperation)
    @Query("DELETE FROM cloud_playlists WHERE account = :account")
    suspend fun clearPlaylists(account: String)
    @Query("DELETE FROM cloud_pages WHERE account = :account AND listId = :listId")
    suspend fun clearPages(account: String, listId: String)
    @Query("DELETE FROM cloud_operations WHERE account = :account AND listId = :listId AND workId = :workId AND version = :version")
    suspend fun finish(account: String, listId: String, workId: Long, version: String)
    @Query("UPDATE cloud_operations SET blocked = 1 WHERE account = :account AND listId = :listId AND workId = :workId AND version = :version")
    suspend fun block(account: String, listId: String, workId: Long, version: String)
    @Query("UPDATE cloud_operations SET blocked = 0 WHERE account = :account")
    suspend fun retry(account: String)
    @Query("SELECT * FROM cloud_pages WHERE account = :account AND listId = :listId ORDER BY page")
    suspend fun pagesOnce(account: String, listId: String): List<AsmrWorkPageCache>
    @Query("DELETE FROM cloud_operations WHERE account = :account AND listId = :listId")
    suspend fun clearOperations(account: String, listId: String)
}

// Account cache and outbox are isolated from the user's local library and its migration chain.
@Database(entities = [AsmrPlaylistCache::class, AsmrWorkPageCache::class, AsmrMembershipOperation::class], version = 1, exportSchema = false)
abstract class AsmrOneCloudDatabase : RoomDatabase() {
    abstract fun cloudDao(): AsmrOneCloudDao
}
