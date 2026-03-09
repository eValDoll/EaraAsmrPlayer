package com.asmr.player.work

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.SystemClock
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.asmr.player.data.local.db.AppDatabaseProvider

class TrackDurationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val albumId = inputData.getLong(KEY_ALBUM_ID, 0L)
        if (albumId <= 0L) return Result.failure()

        val database = AppDatabaseProvider.get(applicationContext)
        val trackDao = database.trackDao()

        val startedAt = SystemClock.elapsedRealtime()
        var processed = 0

        while (processed < MAX_TRACKS_PER_RUN && SystemClock.elapsedRealtime() - startedAt < MAX_RUN_TIME_MS) {
            val batch = trackDao.getTracksNeedingDuration(albumId = albumId, limit = BATCH_SIZE)
            if (batch.isEmpty()) return Result.success()

            batch.forEach { row ->
                val durationSeconds = extractDurationSeconds(applicationContext, row.path)
                if (durationSeconds > 0.0) {
                    trackDao.updateTrackDuration(trackId = row.id, duration = durationSeconds)
                }
            }
            processed += batch.size
        }

        val hasMore = trackDao.getTracksNeedingDuration(albumId = albumId, limit = 1).isNotEmpty()
        return if (hasMore) Result.retry() else Result.success()
    }

    private fun extractDurationSeconds(context: Context, path: String): Double {
        if (path.isBlank()) return 0.0
        val retriever = MediaMetadataRetriever()
        return try {
            if (path.startsWith("content://", ignoreCase = true)) {
                retriever.setDataSource(context, Uri.parse(path))
            } else {
                retriever.setDataSource(path)
            }
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: return 0.0
            if (durationMs <= 0L) return 0.0
            durationMs.toDouble() / 1000.0
        } catch (_: Exception) {
            0.0
        } finally {
            runCatching { retriever.release() }
        }
    }

    companion object {
        const val KEY_ALBUM_ID = "albumId"
        private const val BATCH_SIZE = 40
        private const val MAX_TRACKS_PER_RUN = 300
        private const val MAX_RUN_TIME_MS = 20_000L
    }
}
