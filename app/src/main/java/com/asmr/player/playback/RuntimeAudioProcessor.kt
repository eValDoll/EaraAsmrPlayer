package com.asmr.player.playback

import androidx.media3.common.audio.AudioProcessor

/**
 * 音效处理器的运行时旁路契约。
 *
 * Media3 只会在音频格式重新配置时重新判断 [AudioProcessor.isActive]，而应用内音效可以在
 * 播放过程中即时开关。因此各处理器仍保持格式级 active，由组合链在每个输入缓冲到达时跳过
 * 当前没有实际工作的处理器，避免为旁路音效反复复制整块 PCM。
 */
interface RuntimeAudioProcessor : AudioProcessor {
    fun isRuntimeActive(): Boolean
}
