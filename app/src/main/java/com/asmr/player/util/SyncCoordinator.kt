package com.asmr.player.util

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

data class GlobalSyncState(
    val tokenId: Long,
    val label: String,
    val startedAtElapsedMs: Long
)

@Singleton
class SyncCoordinator @Inject constructor() {
    class Token internal constructor(internal val id: Long)

    private val mutex = Mutex()
    private val nextTokenId = AtomicLong(1L)
    private val _state = MutableStateFlow<GlobalSyncState?>(null)
    val state: StateFlow<GlobalSyncState?> = _state.asStateFlow()

    fun tryBegin(label: String): Token? {
        if (!mutex.tryLock()) return null
        val id = nextTokenId.getAndIncrement()
        _state.value = GlobalSyncState(
            tokenId = id,
            label = label.trim().ifBlank { "同步" },
            startedAtElapsedMs = SystemClock.elapsedRealtime()
        )
        return Token(id)
    }

    fun end(token: Token) {
        val current = _state.value ?: return
        if (current.tokenId != token.id) return
        _state.value = null
        mutex.unlock()
    }
}

