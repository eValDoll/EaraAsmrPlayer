package com.asmr.player.ui.library

import com.asmr.player.data.remote.dlsite.DlsiteCloudSyncCandidate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class CloudSyncSelectionRequestQueue {
    private data class PendingRequest(
        val requestId: Long,
        val albumId: Long?,
        val albumTitle: String,
        val candidates: List<DlsiteCloudSyncCandidate>,
        val deferred: CompletableDeferred<String?>
    )

    private val lock = Any()
    private val queued = ArrayDeque<PendingRequest>()
    private var current: PendingRequest? = null
    private var nextRequestId: Long = 1L
    private var processedInSession: Int = 0
    private var batchSessionActive = false
    private var ignoreRemainingInBatchSession = false
    private val _dialogState = MutableStateFlow<CloudSyncSelectionDialogState?>(null)
    val dialogState: StateFlow<CloudSyncSelectionDialogState?> = _dialogState.asStateFlow()

    fun beginBatchSession() {
        synchronized(lock) {
            batchSessionActive = true
            ignoreRemainingInBatchSession = false
            if (current == null && queued.isEmpty()) {
                processedInSession = 0
            }
        }
    }

    fun endBatchSession() {
        synchronized(lock) {
            batchSessionActive = false
            ignoreRemainingInBatchSession = false
            if (current == null && queued.isEmpty()) {
                processedInSession = 0
            }
        }
    }

    fun enqueue(
        albumId: Long?,
        albumTitle: String,
        candidates: List<DlsiteCloudSyncCandidate>
    ): CompletableDeferred<String?> {
        val normalizedCandidates = candidates.distinctBy { it.workno }.filter { it.workno.isNotBlank() }
        val deferred = CompletableDeferred<String?>()
        if (normalizedCandidates.isEmpty()) {
            deferred.complete(null)
            return deferred
        }

        synchronized(lock) {
            if (batchSessionActive && ignoreRemainingInBatchSession) {
                deferred.complete(null)
                return deferred
            }
            if (current == null && queued.isEmpty()) {
                processedInSession = 0
            }
            val request = PendingRequest(
                requestId = nextRequestId++,
                albumId = albumId,
                albumTitle = albumTitle.trim(),
                candidates = normalizedCandidates,
                deferred = deferred
            )
            if (current == null) {
                current = request
            } else {
                queued.addLast(request)
            }
            publishLocked()
        }
        return deferred
    }

    fun resolveCurrent(workno: String?) {
        val deferred = synchronized(lock) {
            val request = current ?: return
            processedInSession += 1
            advanceLocked()
            publishLocked()
            request.deferred
        }
        deferred.complete(workno?.trim()?.uppercase()?.ifBlank { null })
    }

    fun ignoreAllRemainingInBatch() {
        val deferreds = synchronized(lock) {
            if (!batchSessionActive) return
            ignoreRemainingInBatchSession = true
            val removed = mutableListOf<CompletableDeferred<String?>>()
            current?.deferred?.let(removed::add)
            queued.forEach { removed += it.deferred }
            current = null
            queued.clear()
            processedInSession = 0
            publishLocked()
            removed
        }
        deferreds.forEach { it.complete(null) }
    }

    fun cancelForAlbum(albumId: Long) {
        if (albumId <= 0L) return
        val deferreds = synchronized(lock) {
            val removed = mutableListOf<CompletableDeferred<String?>>()
            if (current?.albumId == albumId) {
                current?.deferred?.let(removed::add)
                processedInSession += 1
                advanceLocked()
            }
            val iter = queued.iterator()
            while (iter.hasNext()) {
                val request = iter.next()
                if (request.albumId == albumId) {
                    removed += request.deferred
                    iter.remove()
                }
            }
            publishLocked()
            removed
        }
        deferreds.forEach { it.complete(null) }
    }

    fun cancelAll() {
        val deferreds = synchronized(lock) {
            val removed = mutableListOf<CompletableDeferred<String?>>()
            current?.deferred?.let(removed::add)
            queued.forEach { removed += it.deferred }
            current = null
            queued.clear()
            processedInSession = 0
            batchSessionActive = false
            ignoreRemainingInBatchSession = false
            publishLocked()
            removed
        }
        deferreds.forEach { it.complete(null) }
    }

    fun pendingCount(): Int = synchronized(lock) { unresolvedCountLocked() }

    private fun advanceLocked() {
        current = queued.removeFirstOrNull()
        if (current == null) {
            processedInSession = 0
        }
    }

    private fun unresolvedCountLocked(): Int {
        return (if (current != null) 1 else 0) + queued.size
    }

    private fun publishLocked() {
        val active = current
        _dialogState.value = if (active == null) {
            null
        } else {
            CloudSyncSelectionDialogState(
                albumTitle = active.albumTitle,
                candidates = active.candidates,
                currentPosition = processedInSession + 1,
                totalCount = processedInSession + unresolvedCountLocked()
            )
        }
    }
}
