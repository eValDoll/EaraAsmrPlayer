package com.asmr.player.util

import android.os.SystemClock
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.remote.download.DownloadStorageGateway
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

data class GlobalSyncState(
    val tokenId: Long,
    val startedAtElapsedMs: Long
)

@Singleton
class SyncCoordinator @Inject constructor(private val database: AppDatabase, storage: DownloadStorageGateway) {
    class Token internal constructor(internal val id: Long, internal val lease: LocalFileOperationCoordinator.Lease)

    private val nextTokenId = AtomicLong(1L)
    private val mutex = Mutex()
    private val scopes = LocalFileScopes(database, storage)
    private val _state = MutableStateFlow<GlobalSyncState?>(null)
    val state: StateFlow<GlobalSyncState?> = _state.asStateFlow()

    suspend fun tryBegin(scope: LocalFileScope = LocalFileScope.Global): Token? {
        if (!mutex.tryLock()) return null
        val lease = LocalFileOperationCoordinator.shared.tryAcquire(LocalFileOperation.LIBRARY, scope = scope) ?: run {
            mutex.unlock()
            return null
        }
        try {
            val unfinished = withContext(Dispatchers.IO) {
                scopes.hasDownloads(scope) || scopes.hasSubtitles(scope)
            }
            if (unfinished) {
                lease.close()
                mutex.unlock()
                return null
            }
        } catch (error: Throwable) {
            lease.close()
            mutex.unlock()
            throw error
        }
        val id = nextTokenId.getAndIncrement()
        _state.value = GlobalSyncState(
            tokenId = id,
            startedAtElapsedMs = SystemClock.elapsedRealtime()
        )
        return Token(id, lease)
    }

    fun end(token: Token) {
        val current = _state.value ?: return
        if (current.tokenId != token.id) return
        _state.value = null
        token.lease.close()
        mutex.unlock()
    }
}

