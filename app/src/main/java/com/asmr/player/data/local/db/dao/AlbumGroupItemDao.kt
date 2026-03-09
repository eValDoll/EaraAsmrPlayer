package com.asmr.player.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.asmr.player.data.local.db.entities.AlbumGroupItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumGroupItemDao {
    @Query(
        """
        SELECT
            i.groupId AS groupId,
            i.mediaId AS mediaId,
            i.itemOrder AS itemOrder,
            i.createdAt AS createdAt,
            t.id AS trackId,
            t.albumId AS albumId,
            t.title AS trackTitle,
            t.duration AS trackDuration,
            t.path AS trackPath,
            t.`group` AS trackGroup,
            a.title AS albumTitle,
            a.rjCode AS albumRjCode,
            a.workId AS albumWorkId,
            a.coverThumbPath AS albumCoverThumbPath,
            a.coverPath AS albumCoverPath,
            a.coverUrl AS albumCoverUrl
        FROM album_group_items i
        INNER JOIN tracks t ON t.path = i.mediaId
        LEFT JOIN albums a ON a.id = t.albumId
        WHERE i.groupId = :groupId
        ORDER BY
            a.title COLLATE NOCASE ASC,
            a.id ASC,
            i.itemOrder ASC,
            i.rowid ASC
        """
    )
    fun observeGroupTracks(groupId: Long): Flow<List<AlbumGroupTrackRow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItems(items: List<AlbumGroupItemEntity>)

    @Query("DELETE FROM album_group_items WHERE groupId = :groupId AND mediaId = :mediaId")
    suspend fun deleteItem(groupId: Long, mediaId: String)

    @Query("DELETE FROM album_group_items WHERE groupId = :groupId")
    suspend fun clearGroup(groupId: Long)

    @Query(
        """
        DELETE FROM album_group_items
        WHERE groupId = :groupId
          AND mediaId IN (SELECT path FROM tracks WHERE albumId = :albumId)
        """
    )
    suspend fun deleteAlbumFromGroup(groupId: Long, albumId: Long)
}

data class AlbumGroupTrackRow(
    val groupId: Long,
    val mediaId: String,
    val itemOrder: Int,
    val createdAt: Long,
    val trackId: Long,
    val albumId: Long,
    val trackTitle: String,
    val trackDuration: Double,
    val trackPath: String,
    val trackGroup: String,
    val albumTitle: String?,
    val albumRjCode: String?,
    val albumWorkId: String?,
    val albumCoverThumbPath: String?,
    val albumCoverPath: String?,
    val albumCoverUrl: String?
)
