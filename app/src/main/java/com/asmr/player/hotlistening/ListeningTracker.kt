package com.asmr.player.hotlistening

import androidx.media3.common.MediaItem
import com.asmr.player.playback.PlaybackSnapshot
import com.asmr.player.ui.player.isOnlineMedia
import com.asmr.player.util.DlsiteWorkNo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(FlowPreview::class)
class ListeningTracker @Inject constructor(
    private val hotListeningApi: HotListeningApi
) {
    private var observationJob: Job? = null
    private val reportScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeSession = ListenSession()
    private var playCountReportedMediaId: String? = null

    fun start(scope: CoroutineScope, playback: StateFlow<PlaybackSnapshot>) {
        stop()
        observationJob = scope.launch {
            playback
                .sample(TrackerObservationIntervalMs)
                .map { snapshot ->
                    val item = snapshot.currentMediaItem
                    val mediaId = item?.mediaId ?: ""
                    val isOnline = item.isOnlineMedia()
                    val rjCode = extractRjCode(item)
                    TrackerSnapshot(
                        mediaId = mediaId,
                        rjCode = rjCode,
                        isPlaying = snapshot.isPlaying,
                        isOnline = isOnline,
                        positionMs = snapshot.positionMs.coerceAtLeast(0L),
                        observedAtMs = android.os.SystemClock.elapsedRealtime()
                    )
                }
                .distinctUntilChanged { old, new ->
                    old.mediaId == new.mediaId &&
                        old.rjCode == new.rjCode &&
                        old.isPlaying == new.isPlaying &&
                        old.isOnline == new.isOnline &&
                        old.positionMs == new.positionMs
                }
                .collect { snapshot ->
                    handleSnapshot(snapshot, scope)
                }
        }
    }

    fun stop() {
        flushDuration()
        observationJob?.cancel()
        observationJob = null
    }

    fun flushNow() {
        flushDuration()
    }

    private fun handleSnapshot(snapshot: TrackerSnapshot, scope: CoroutineScope) {
        val previous = activeSession
        if (snapshot.mediaId.isBlank()) {
            flushDuration()
            playCountReportedMediaId = null
            return
        }

        val mediaChanged = previous.mediaId.isNotBlank() && previous.mediaId != snapshot.mediaId
        val rjChanged = previous.rjCode.isNotBlank() && previous.rjCode != snapshot.rjCode
        if (mediaChanged || rjChanged) {
            flushDuration()
            playCountReportedMediaId = null
            activeSession = ListenSession(
                mediaId = snapshot.mediaId,
                rjCode = snapshot.rjCode,
                isOnline = snapshot.isOnline,
                isPlaying = snapshot.isPlaying,
                lastObservedAtMs = snapshot.observedAtMs
            )
            return
        }

        if (previous.mediaId.isBlank() && snapshot.mediaId.isNotBlank()) {
            activeSession = ListenSession(
                mediaId = snapshot.mediaId,
                rjCode = snapshot.rjCode,
                isOnline = snapshot.isOnline,
                isPlaying = snapshot.isPlaying,
                lastObservedAtMs = snapshot.observedAtMs
            )
            return
        }

        activeSession = previous.addElapsedUntil(snapshot.observedAtMs).copy(
            mediaId = snapshot.mediaId,
            rjCode = snapshot.rjCode,
            isOnline = snapshot.isOnline,
            isPlaying = snapshot.isPlaying,
            lastObservedAtMs = snapshot.observedAtMs
        )

        val session = activeSession
        if (session.playedMs >= REPORT_THRESHOLD_MS && playCountReportedMediaId != session.mediaId) {
            playCountReportedMediaId = session.mediaId
            reportPlayCount(session.rjCode, scope)
        }

        if (!snapshot.isPlaying && previous.isPlaying) {
            flushDuration()
        }
    }

    private fun flushDuration() {
        activeSession = activeSession.addElapsedUntil(android.os.SystemClock.elapsedRealtime())
        val session = activeSession
        if (session.playedMs >= REPORT_THRESHOLD_MS && session.rjCode.isNotBlank() && hotListeningApi.isBackendConfigured) {
            val durationMs = session.playedMs
            val rjCode = session.rjCode
            if (playCountReportedMediaId != session.mediaId) {
                playCountReportedMediaId = session.mediaId
                reportScope.launch {
                    runCatching { hotListeningApi.reportListen(rjCode) }
                }
            }
            reportScope.launch {
                runCatching { hotListeningApi.reportListenDuration(rjCode, durationMs) }
            }
        }
        activeSession = ListenSession()
    }

    private fun reportPlayCount(rjCode: String, scope: CoroutineScope) {
        if (rjCode.isBlank() || !hotListeningApi.isBackendConfigured) return
        scope.launch {
            runCatching {
                hotListeningApi.reportListen(rjCode)
            }
        }
    }

    private fun extractRjCode(item: MediaItem?): String {
        if (item == null) return ""
        val metadata = item.mediaMetadata
        val extras = metadata.extras
        val mediaId = item.mediaId.trim()
        val uri = item.localConfiguration?.uri?.toString().orEmpty().trim()

        return DlsiteWorkNo.extractWorkNo(
            listOfNotNull(
                extras?.getString("rj_code"),
                mediaId,
                uri,
                metadata.albumTitle?.toString(),
                metadata.title?.toString()
            ).joinToString(" ")
        )
    }

    private companion object {
        private const val REPORT_THRESHOLD_MS = 30_000L
        private const val TrackerObservationIntervalMs = 1_000L
    }
}

private data class TrackerSnapshot(
    val mediaId: String,
    val rjCode: String,
    val isPlaying: Boolean,
    val isOnline: Boolean,
    val positionMs: Long,
    val observedAtMs: Long
)

private data class ListenSession(
    val mediaId: String = "",
    val rjCode: String = "",
    val isOnline: Boolean = false,
    val isPlaying: Boolean = false,
    val lastObservedAtMs: Long = 0L,
    val playedMs: Long = 0L
) {
    fun addElapsedUntil(nowMs: Long): ListenSession {
        if (!isPlaying || lastObservedAtMs <= 0L || nowMs <= lastObservedAtMs) return this
        return copy(
            playedMs = playedMs + (nowMs - lastObservedAtMs),
            lastObservedAtMs = nowMs
        )
    }
}
