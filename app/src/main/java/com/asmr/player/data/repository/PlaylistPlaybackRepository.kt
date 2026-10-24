package com.asmr.player.data.repository

import com.asmr.player.data.local.db.dao.PlaylistPlaybackDao
import com.asmr.player.data.local.db.entities.PlaylistPlaybackEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistPlaybackRepository @Inject constructor(
    private val playlistPlaybackDao: PlaylistPlaybackDao
) {
    fun observePlayback(playlistId: Long): Flow<PlaylistPlaybackEntity?> =
        playlistPlaybackDao.observePlayback(playlistId).distinctUntilChanged()

    suspend fun recordPlayback(
        playlistId: Long,
        mediaId: String,
        positionMs: Long,
        durationMs: Long,
        isCompleted: Boolean = false,
        updatedAt: Long = System.currentTimeMillis()
    ) {
        if (playlistId <= 0L || mediaId.isBlank()) return
        val duration = durationMs.coerceAtLeast(0L)
        val position = positionMs.coerceAtLeast(0L).let {
            if (duration > 0L) it.coerceAtMost(duration) else it
        }
        playlistPlaybackDao.recordPlayback(
            playlistId = playlistId,
            mediaId = mediaId,
            positionMs = position,
            durationMs = duration,
            isCompleted = isCompleted,
            updatedAt = updatedAt
        )
    }
}
