package com.asmr.player.playback

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl

@UnstableApi
internal class PlaybackRecoveryLoadControl : DefaultLoadControl() {
    @Volatile private var recovering = false

    fun beginRecovery() {
        recovering = true
    }

    fun clearRecovery() {
        recovering = false
    }

    override fun shouldStartPlayback(parameters: LoadControl.Parameters): Boolean {
        if (!super.shouldStartPlayback(parameters)) return false
        if (!recovering) return true

        // 恢复初期实测出现约 10 秒的数据供给间歇，留出余量后再续播，避免只播一两秒又停。
        val requiredBufferUs = if (parameters.targetLiveOffsetUs == C.TIME_UNSET) {
            RECOVERY_BUFFER_US
        } else {
            minOf(RECOVERY_BUFFER_US, parameters.targetLiveOffsetUs / 2)
        }
        val canStart = Util.getPlayoutDurationForMediaDuration(
            parameters.bufferedDurationUs, parameters.playbackSpeed,
        ) >= requiredBufferUs || allocator.totalBytesAllocated >= DEFAULT_MIN_BUFFER_SIZE
        // 沿用默认字节上限，避免高码率音频达到内存上限后仍等待更长时间缓冲。
        if (canStart) recovering = false
        return canStart
    }

    private companion object {
        const val RECOVERY_BUFFER_US = 12_000_000L
    }
}
