package com.asmr.player.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
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

    @Query("SELECT * FROM albums WHERE id = :albumId LIMIT 1")
    fun observeAlbumById(albumId: Long): Flow<AlbumEntity?>

    @Query("SELECT * FROM albums WHERE workId = :workId COLLATE NOCASE OR rjCode = :workId COLLATE NOCASE ORDER BY id ASC")
    suspend fun getAlbumsByWorkIdOnce(workId: String): List<AlbumEntity>

    @Query(
        "SELECT * FROM albums WHERE " +
            "REPLACE(REPLACE(UPPER(TRIM(workId)), ' ', ''), char(9), '') = UPPER(:workId) OR " +
            "REPLACE(REPLACE(UPPER(TRIM(rjCode)), ' ', ''), char(9), '') = UPPER(:workId) " +
            "ORDER BY id ASC"
    )
    suspend fun getAlbumsByNormalizedWorkIdOnce(workId: String): List<AlbumEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbum(album: AlbumEntity): Long

    @Transaction
    suspend fun upsertImportedAlbum(album: AlbumEntity): Long {
        val current = if (album.id > 0L) getAlbumById(album.id) else null
        val updated = if (current == null) album else {
            val currentSource = current.manualCoverPath.ifBlank { current.coverPath.ifBlank { current.coverUrl } }
            val importedSource = current.manualCoverPath.ifBlank { album.coverPath.ifBlank { album.coverUrl } }
            album.copy(
                manualCoverPath = current.manualCoverPath,
                coverThumbPath = current.coverThumbPath.takeIf { currentSource == importedSource }.orEmpty(),
            )
        }
        return insertAlbum(updated)
    }

    @Update
    suspend fun updateAlbum(album: AlbumEntity)

    @Query("UPDATE albums SET displayTitle = :displayTitle WHERE id = :albumId")
    suspend fun updateAlbumDisplayTitle(albumId: Long, displayTitle: String)

    @Query(
        "UPDATE albums SET audioTrackCount = :trackCount, audioTotalDuration = :totalDuration, " +
            "audioTotalSizeBytes = :totalSizeBytes WHERE id = :albumId"
    )
    suspend fun updateAlbumAudioAggregate(albumId: Long, trackCount: Int, totalDuration: Double, totalSizeBytes: Long)

    @Query("UPDATE albums SET manualCoverPath = :manualCoverPath, coverThumbPath = '' WHERE id = :albumId")
    suspend fun setManualCoverPath(albumId: Long, manualCoverPath: String)

    @Query(
        "UPDATE albums SET coverPath = :coverPath, " +
            "coverThumbPath = CASE WHEN manualCoverPath = '' THEN :coverThumbPath ELSE coverThumbPath END " +
            "WHERE id = :albumId AND coverPath = :expectedCoverPath AND coverUrl = :expectedCoverUrl"
    )
    suspend fun updateAutomaticCoverIfSourceMatches(
        albumId: Long,
        expectedCoverPath: String,
        expectedCoverUrl: String,
        coverPath: String,
        coverThumbPath: String,
    ): Int

    @Query(
        "UPDATE albums SET coverThumbPath = :coverThumbPath WHERE id = :albumId " +
            "AND manualCoverPath = :manualCoverPath AND coverPath = :coverPath AND coverUrl = :coverUrl"
    )
    suspend fun updateAlbumCoverThumbIfSourceMatches(
        albumId: Long,
        manualCoverPath: String,
        coverPath: String,
        coverUrl: String,
        coverThumbPath: String,
    ): Int

    @Delete
    suspend fun deleteAlbum(album: AlbumEntity)
}
