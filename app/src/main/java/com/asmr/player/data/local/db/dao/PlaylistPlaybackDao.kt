package com.asmr.player.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.asmr.player.data.local.db.entities.PlaylistPlaybackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistPlaybackDao {
    @Query("SELECT * FROM playlist_playback WHERE playlistId = :playlistId")
    fun observePlayback(playlistId: Long): Flow<PlaylistPlaybackEntity?>

    @Query(
        """
        INSERT OR REPLACE INTO playlist_playback
            (playlistId, mediaId, positionMs, durationMs, isCompleted, updatedAt)
        SELECT :playlistId, :mediaId, :positionMs, :durationMs, :isCompleted, :updatedAt
        FROM playlist_items
        WHERE playlistId = :playlistId AND mediaId = :mediaId
          AND NOT EXISTS (
              SELECT 1 FROM playlist_playback
              WHERE playlistId = :playlistId AND updatedAt > :updatedAt
          )
        """
    )
    suspend fun recordPlayback(
        playlistId: Long,
        mediaId: String,
        positionMs: Long,
        durationMs: Long,
        isCompleted: Boolean,
        updatedAt: Long
    )
}
