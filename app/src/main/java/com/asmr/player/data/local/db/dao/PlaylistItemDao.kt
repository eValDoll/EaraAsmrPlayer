package com.asmr.player.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.asmr.player.data.local.db.entities.PlaylistItemEntity
import com.asmr.player.data.local.db.entities.PlaylistItemWithSubtitles
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistItemDao {
    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY itemOrder ASC, rowid ASC")
    fun observeItems(playlistId: Long): Flow<List<PlaylistItemEntity>>

    @Query(
        """
        SELECT
            pi.playlistId,
            pi.mediaId,
            pi.title,
            pi.artist,
            pi.uri,
            COALESCE(
                NULLIF(a.coverThumbPath, ''),
                NULLIF(a.coverPath, ''),
                NULLIF(a.coverUrl, ''),
                NULLIF(pi.artworkUri, ''),
                ''
            ) AS artworkUri,
            COALESCE(
                NULLIF(a.coverPath, ''),
                NULLIF(a.coverUrl, ''),
                NULLIF(pi.artworkUri, ''),
                NULLIF(a.coverThumbPath, ''),
                ''
            ) AS playbackArtworkUri,
            pi.itemOrder,
            (
                EXISTS(
                    SELECT 1
                    FROM tracks t
                    JOIN subtitles s ON s.trackId = t.id
                    WHERE t.path = pi.mediaId
                )
                OR EXISTS(
                    SELECT 1
                    FROM tracks t
                    JOIN remote_subtitle_sources rs ON rs.trackId = t.id
                    WHERE t.path = pi.mediaId
                )
            ) AS hasSubtitles
        FROM playlist_items pi
        LEFT JOIN tracks t ON t.path = pi.mediaId
        LEFT JOIN albums a ON a.id = t.albumId
        WHERE pi.playlistId = :playlistId
        ORDER BY pi.itemOrder ASC, pi.rowid ASC
        """
    )
    fun observeItemsWithSubtitles(playlistId: Long): Flow<List<PlaylistItemWithSubtitles>>

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun clearPlaylist(playlistId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItems(items: List<PlaylistItemEntity>)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId AND mediaId = :mediaId")
    suspend fun deleteItem(playlistId: Long, mediaId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM playlist_items WHERE playlistId = :playlistId AND mediaId = :mediaId)")
    fun observeIsItemInPlaylist(playlistId: Long, mediaId: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM playlist_items WHERE playlistId = :playlistId AND mediaId = :mediaId)")
    suspend fun isItemInPlaylist(playlistId: Long, mediaId: String): Boolean

    @Transaction
    suspend fun replaceAll(playlistId: Long, items: List<PlaylistItemEntity>) {
        clearPlaylist(playlistId)
        upsertItems(items)
    }
}
