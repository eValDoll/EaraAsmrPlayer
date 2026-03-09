package com.asmr.player.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Update
import androidx.paging.PagingSource
import androidx.sqlite.db.SupportSQLiteQuery
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.data.local.db.entities.AlbumFtsEntity
import com.asmr.player.data.local.db.entities.AlbumPlayStatEntity
import com.asmr.player.data.local.db.entities.AlbumTagEntity
import com.asmr.player.data.local.db.entities.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {
    @Query("SELECT * FROM albums ORDER BY id DESC")
    fun getAllAlbums(): Flow<List<AlbumEntity>>

    @Query("SELECT DISTINCT circle FROM albums WHERE circle != '' ORDER BY circle COLLATE NOCASE ASC")
    fun getDistinctCircles(): Flow<List<String>>

    @Query("SELECT DISTINCT cv FROM albums WHERE cv != '' ORDER BY cv COLLATE NOCASE ASC")
    fun getDistinctCvs(): Flow<List<String>>

    @RawQuery(observedEntities = [AlbumEntity::class, AlbumFtsEntity::class, AlbumPlayStatEntity::class, TagEntity::class, AlbumTagEntity::class])
    fun queryAlbums(query: SupportSQLiteQuery): Flow<List<AlbumEntity>>

    @RawQuery(observedEntities = [AlbumEntity::class, AlbumFtsEntity::class, AlbumPlayStatEntity::class, TagEntity::class, AlbumTagEntity::class])
    fun queryAlbumsPaged(query: SupportSQLiteQuery): PagingSource<Int, AlbumEntity>

    @Query("SELECT * FROM albums ORDER BY id DESC")
    suspend fun getAllAlbumsOnce(): List<AlbumEntity>

    @Query("SELECT * FROM albums WHERE path = :path LIMIT 1")
    suspend fun getAlbumByPathOnce(path: String): AlbumEntity?

    @Query("SELECT * FROM albums WHERE downloadPath = :downloadPath")
    suspend fun getAlbumsByDownloadPathOnce(downloadPath: String): List<AlbumEntity>

    @Query("SELECT * FROM albums WHERE workId = :workId COLLATE NOCASE OR rjCode = :workId COLLATE NOCASE LIMIT 1")
    suspend fun getAlbumByWorkIdOnce(workId: String): AlbumEntity?

    @Query("SELECT * FROM albums WHERE id = :albumId LIMIT 1")
    suspend fun getAlbumById(albumId: Long): AlbumEntity?

    @Query("SELECT * FROM albums WHERE workId = :workId COLLATE NOCASE OR rjCode = :workId COLLATE NOCASE ORDER BY id ASC")
    suspend fun getAlbumsByWorkIdOnce(workId: String): List<AlbumEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbum(album: AlbumEntity): Long

    @Update
    suspend fun updateAlbum(album: AlbumEntity)

    @Delete
    suspend fun deleteAlbum(album: AlbumEntity)
}
