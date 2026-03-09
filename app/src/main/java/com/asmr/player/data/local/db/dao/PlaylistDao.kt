package com.asmr.player.data.local.db.dao

import androidx.room.*
import com.asmr.player.data.local.db.entities.PlaylistEntity
import com.asmr.player.data.local.db.entities.PlaylistTrackCrossRef
import com.asmr.player.data.local.db.entities.TrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query(
        """
        SELECT
            p.id AS id,
            p.name AS name,
            p.category AS category,
            p.createdAt AS createdAt,
            (SELECT COUNT(*) FROM playlist_items i WHERE i.playlistId = p.id) AS itemCount,
            (
                SELECT COALESCE(
                    NULLIF(a.coverThumbPath, ''),
                    NULLIF(a.coverPath, ''),
                    NULLIF(a.coverUrl, ''),
                    NULLIF(i.artworkUri, '')
                )
                FROM playlist_items i
                LEFT JOIN tracks t ON t.path = i.mediaId
                LEFT JOIN albums a ON a.id = t.albumId
                WHERE i.playlistId = p.id
                ORDER BY i.itemOrder ASC, i.rowid ASC
                LIMIT 1
            ) AS firstArtworkUri,
            (
                SELECT i.uri
                FROM playlist_items i
                WHERE i.playlistId = p.id
                ORDER BY i.itemOrder ASC, i.rowid ASC
                LIMIT 1
            ) AS firstItemUri
        FROM playlists p
        ORDER BY p.createdAt DESC
        """
    )
    fun observePlaylistsWithStats(): Flow<List<PlaylistStatsRow>>

    @Query("SELECT * FROM playlists WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getPlaylistByNameOnce(name: String): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE name = :name COLLATE NOCASE LIMIT 1")
    fun getPlaylistByName(name: String): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlists WHERE category = :category")
    fun getPlaylistsByCategory(category: String): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id LIMIT 1")
    suspend fun getPlaylistByIdOnce(id: Long): PlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun updatePlaylistName(id: Long, name: String)

    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTrackCrossRef(crossRef: PlaylistTrackCrossRef)

    @Query("DELETE FROM playlist_track_cross_ref WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun deleteTrackFromPlaylist(playlistId: Long, trackId: Long)

    @Transaction
    @Query("""
        SELECT t.* FROM tracks t
        INNER JOIN playlist_track_cross_ref ref ON t.id = ref.trackId
        WHERE ref.playlistId = :playlistId
        ORDER BY ref.trackOrder ASC
    """)
    fun getTracksForPlaylist(playlistId: Long): Flow<List<TrackEntity>>
}

data class PlaylistStatsRow(
    val id: Long,
    val name: String,
    val category: String,
    val createdAt: Long,
    val itemCount: Int,
    val firstArtworkUri: String?,
    val firstItemUri: String?
)
