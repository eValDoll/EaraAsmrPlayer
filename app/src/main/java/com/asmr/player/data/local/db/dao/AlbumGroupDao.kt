package com.asmr.player.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.asmr.player.data.local.db.entities.AlbumGroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumGroupDao {
    @Query(
        """
        SELECT
            g.id AS id,
            g.name AS name,
            g.createdAt AS createdAt,
            (SELECT COUNT(*) FROM album_group_items i WHERE i.groupId = g.id) AS itemCount,
            (
                SELECT COUNT(DISTINCT t.albumId)
                FROM album_group_items i
                INNER JOIN tracks t ON t.path = i.mediaId
                WHERE i.groupId = g.id
            ) AS albumCount,
            (
                SELECT COALESCE(
                    NULLIF(a.coverThumbPath, ''),
                    NULLIF(a.coverPath, ''),
                    NULLIF(a.coverUrl, '')
                )
                FROM album_group_items i
                LEFT JOIN tracks t ON t.path = i.mediaId
                LEFT JOIN albums a ON a.id = t.albumId
                WHERE i.groupId = g.id
                ORDER BY i.itemOrder ASC, i.rowid ASC
                LIMIT 1
            ) AS firstArtworkUri
        FROM album_groups g
        ORDER BY g.createdAt DESC
        """
    )
    fun observeGroupsWithStats(): Flow<List<AlbumGroupStatsRow>>

    @Query("SELECT * FROM album_groups WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getGroupByNameOnce(name: String): AlbumGroupEntity?

    @Query("SELECT * FROM album_groups WHERE id = :id LIMIT 1")
    suspend fun getGroupByIdOnce(id: Long): AlbumGroupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: AlbumGroupEntity): Long

    @Query("UPDATE album_groups SET name = :name WHERE id = :id")
    suspend fun updateGroupName(id: Long, name: String)

    @Delete
    suspend fun deleteGroup(group: AlbumGroupEntity)
}

data class AlbumGroupStatsRow(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val itemCount: Int,
    val albumCount: Int,
    val firstArtworkUri: String?
)
