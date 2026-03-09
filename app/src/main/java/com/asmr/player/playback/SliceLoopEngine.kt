package com.asmr.player.playback

import androidx.media3.common.Player
import com.asmr.player.domain.model.Slice

class SliceLoopEngine(
    private val endThresholdMs: Long = 200L,
    private val sameTargetCooldownMs: Long = 650L
) {
    private var lastActionAtElapsedMs: Long = 0L
    private var lastSeekTargetMs: Long = -1L
    private var lastMediaId: String? = null

    fun decide(nowElapsedMs: Long, input: SliceLoopInput): SliceLoopAction {
        if (input.userScrubbing) return SliceLoopAction.None

        val mediaId = input.mediaId ?: return SliceLoopAction.None
        val preview = input.previewSlice
        if (preview != null) {
            return decidePreview(nowElapsedMs, mediaId, input.positionMs, preview)
        }
        if (!input.sliceModeEnabled) return SliceLoopAction.None
        val slices = input.slices
        if (slices.isEmpty()) return SliceLoopAction.None

        if (mediaId != lastMediaId) {
            lastMediaId = mediaId
            lastSeekTargetMs = -1L
            lastActionAtElapsedMs = 0L
        }

        val sorted = if (slices.isSortedByStart()) slices else slices.sortedBy { it.startMs }
        val pos = input.positionMs.coerceAtLeast(0L)

        val activeIndex = sorted.indexOfFirst { s -> pos >= s.startMs && pos < s.endMs }
        if (activeIndex < 0) {
            val next = sorted.firstOrNull { it.startMs > pos } ?: sorted.firstOrNull()
            if (next != null) {
                return seekIfNotThrottled(nowElapsedMs, pos, next.startMs)
            }
            return SliceLoopAction.None
        }

        val active = sorted[activeIndex]
        if (pos < active.endMs - endThresholdMs) return SliceLoopAction.None

        return if (activeIndex < sorted.lastIndex) {
            seekIfNotThrottled(nowElapsedMs, pos, sorted[activeIndex + 1].startMs)
        } else {
            if (input.repeatMode == Player.REPEAT_MODE_ONE) {
                seekIfNotThrottled(nowElapsedMs, pos, sorted.first().startMs)
            } else {
                SliceLoopAction.SkipToNext
            }
        }
    }

    private fun decidePreview(nowElapsedMs: Long, mediaId: String, positionMs: Long, preview: Slice): SliceLoopAction {
        if (mediaId != lastMediaId) {
            lastMediaId = mediaId
            lastSeekTargetMs = -1L
            lastActionAtElapsedMs = 0L
        }
        val pos = positionMs.coerceAtLeast(0L)
        if (pos < preview.startMs) {
            return seekIfNotThrottled(nowElapsedMs, pos, preview.startMs)
        }
        if (pos >= preview.endMs - endThresholdMs) {
            return SliceLoopAction.PauseAndClearPreview
        }
        if (pos >= preview.endMs) {
            return SliceLoopAction.PauseAndClearPreview
        }
        return SliceLoopAction.None
    }

    private fun seekIfNotThrottled(nowElapsedMs: Long, currentPosMs: Long, targetMs: Long): SliceLoopAction {
        val target = targetMs.coerceAtLeast(0L)
        if (kotlin.math.abs(currentPosMs - target) <= endThresholdMs) return SliceLoopAction.None

        val tooSoon = nowElapsedMs - lastActionAtElapsedMs < sameTargetCooldownMs
        if (tooSoon && lastSeekTargetMs == target) return SliceLoopAction.None

        lastActionAtElapsedMs = nowElapsedMs
        lastSeekTargetMs = target
        return SliceLoopAction.SeekTo(target)
    }
}

data class SliceLoopInput(
    val sliceModeEnabled: Boolean,
    val userScrubbing: Boolean,
    val mediaId: String?,
    val positionMs: Long,
    val durationMs: Long,
    val repeatMode: Int,
    val previewSlice: Slice?,
    val slices: List<Slice>
)

sealed interface SliceLoopAction {
    data object None : SliceLoopAction
    data class SeekTo(val positionMs: Long) : SliceLoopAction
    data object SkipToNext : SliceLoopAction
    data object PauseAndClearPreview : SliceLoopAction
}

private fun List<Slice>.isSortedByStart(): Boolean {
    for (i in 1 until size) {
        if (this[i - 1].startMs > this[i].startMs) return false
    }
    return true
}
