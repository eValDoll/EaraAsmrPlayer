package com.asmr.player.util

import android.os.SystemClock
import android.util.Log
import com.asmr.player.BuildConfig
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

private data class NowPlayingLaunchSession(
    val id: Long,
    val source: String,
    val startedAtMs: Long
)

object NowPlayingLaunchTrace {
    private const val TAG = "NowPlayingTrace"
    private val nextId = AtomicLong(1L)
    private val activeSession = AtomicReference<NowPlayingLaunchSession?>(null)

    fun begin(source: String, detail: String = ""): Long {
        if (!BuildConfig.DEBUG) return 0L
        val session = NowPlayingLaunchSession(
            id = nextId.getAndIncrement(),
            source = source,
            startedAtMs = SystemClock.elapsedRealtime()
        )
        activeSession.set(session)
        Log.d(
            TAG,
            "[${session.id}] begin source=$source detail=$detail thread=${Thread.currentThread().name}"
        )
        return session.id
    }

    fun mark(stage: String, detail: String = "") {
        if (!BuildConfig.DEBUG) return
        val now = SystemClock.elapsedRealtime()
        val session = activeSession.get()
        if (session == null) {
            Log.d(TAG, "[-] $stage detail=$detail thread=${Thread.currentThread().name}")
            return
        }
        Log.d(
            TAG,
            "[${session.id}] +${now - session.startedAtMs}ms $stage source=${session.source} detail=$detail thread=${Thread.currentThread().name}"
        )
    }

    fun clear(reason: String = "") {
        if (!BuildConfig.DEBUG) return
        val session = activeSession.getAndSet(null) ?: return
        val now = SystemClock.elapsedRealtime()
        Log.d(
            TAG,
            "[${session.id}] +${now - session.startedAtMs}ms clear source=${session.source} detail=$reason thread=${Thread.currentThread().name}"
        )
    }
}
