package com.asmr.player.playback

import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class VolumeFader(
    private val scope: CoroutineScope
) {
    private var job: Job? = null

    fun cancel() {
        job?.cancel()
        job = null
    }

    fun fadeTo(
        player: Player,
        targetVolume: Float,
        durationMs: Long,
        onEnd: (() -> Unit)? = null
    ) {
        val target = targetVolume.coerceIn(0f, 1f)
        if (durationMs <= 0L) {
            cancel()
            player.volume = target
            onEnd?.invoke()
            return
        }

        val start = player.volume.coerceIn(0f, 1f)
        if (kotlin.math.abs(start - target) < 0.0005f) {
            cancel()
            player.volume = target
            onEnd?.invoke()
            return
        }

        cancel()
        job = scope.launch {
            val startMs = android.os.SystemClock.uptimeMillis()
            val endMs = startMs + durationMs
            while (isActive) {
                val now = android.os.SystemClock.uptimeMillis()
                val t = ((now - startMs).toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                val eased = t * t * (3f - 2f * t)
                val v = start + (target - start) * eased
                player.volume = v.coerceIn(0f, 1f)
                if (now >= endMs) break
                delay(16)
            }
            player.volume = target
            if (isActive) onEnd?.invoke()
        }
    }
}
