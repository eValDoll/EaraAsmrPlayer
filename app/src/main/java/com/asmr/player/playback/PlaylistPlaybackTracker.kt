package com.asmr.player.playback

internal data class PlaylistPlaybackProgress(
    val playlistId: Long,
    val mediaId: String,
    val positionMs: Long,
    val durationMs: Long,
    val isCompleted: Boolean,
    val updatedAt: Long,
)

/** Keeps paused/restored and failed requests out of a playlist's actual playback history. */
internal class PlaylistPlaybackTracker {
    private var current: PlaylistPlaybackProgress? = null
    private var lastPersistElapsedMs = 0L
    private var requestReplaced = false

    fun invalidateCurrentRequest() {
        requestReplaced = true
    }

    fun capturePreviousPosition(
        playlistId: Long?,
        mediaId: String?,
        positionMs: Long,
        isCompleted: Boolean,
    ) {
        val previous = current ?: return
        if (previous.playlistId != playlistId || previous.mediaId != mediaId) return
        current = previous.copy(
            positionMs = boundedPosition(positionMs, previous.durationMs),
            isCompleted = isCompleted,
        )
    }

    fun update(
        playlistId: Long?,
        mediaId: String?,
        positionMs: Long,
        durationMs: Long,
        isPlaying: Boolean,
        isEnded: Boolean,
        force: Boolean,
        nowElapsedMs: Long,
        nowEpochMs: Long,
    ): List<PlaylistPlaybackProgress> {
        val previous = current
        val sameTrack = previous != null && previous.playlistId == playlistId && previous.mediaId == mediaId && !requestReplaced
        requestReplaced = false
        val writes = mutableListOf<PlaylistPlaybackProgress>()
        if (!sameTrack) {
            previous?.let { writes += it.copy(updatedAt = nowEpochMs) }
            current = null
            lastPersistElapsedMs = 0L
            if (!isPlaying || playlistId == null || playlistId <= 0L || mediaId.isNullOrBlank()) return writes
        }
        if (playlistId == null || mediaId.isNullOrBlank()) return writes
        val duration = durationMs.takeIf { it > 0L } ?: current?.durationMs ?: 0L
        val position = boundedPosition(positionMs, duration)
        val sample = PlaylistPlaybackProgress(
            playlistId = playlistId,
            mediaId = mediaId,
            positionMs = position,
            durationMs = duration,
            isCompleted = isEnded || (duration > 0L && position >= duration),
            updatedAt = nowEpochMs,
        )
        current = sample
        if (!sameTrack || force || nowElapsedMs - lastPersistElapsedMs >= 10_000L) {
            writes += sample
            lastPersistElapsedMs = nowElapsedMs
        }
        return writes
    }

    private fun boundedPosition(positionMs: Long, durationMs: Long): Long =
        if (durationMs > 0L) positionMs.coerceIn(0L, durationMs) else positionMs.coerceAtLeast(0L)
}
