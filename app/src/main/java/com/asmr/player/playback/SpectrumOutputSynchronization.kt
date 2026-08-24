package com.asmr.player.playback

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.DefaultAudioSink

internal const val DefaultSpectrumAudioTrackBufferDurationMillis = 250
internal const val SpectrumPcmRingSlotCount = 80
private const val DefaultSpectrumHardwarePeriodMillis = 20
private const val SpectrumHardwarePipelinePeriodCount = 3
private const val MaximumSpectrumVisualDelayMillis = 400

/**
 * 保留 Media3 原本的缓冲选择，仅记录 PCM 最终进入 AudioTrack 前可排队的时长。
 * 频谱据此回看对应的 PCM 帧，避免用缩短音频缓冲来换取视觉同步。
 */
@UnstableApi
internal class SpectrumOutputBufferSizeProvider(
    private val delegate: DefaultAudioSink.AudioTrackBufferSizeProvider =
        DefaultAudioSink.AudioTrackBufferSizeProvider.DEFAULT,
    private val onPcmBufferDurationChanged: (Int) -> Unit
) : DefaultAudioSink.AudioTrackBufferSizeProvider {
    override fun getBufferSizeInBytes(
        minBufferSizeInBytes: Int,
        encoding: Int,
        outputMode: Int,
        pcmFrameSize: Int,
        sampleRate: Int,
        bitrate: Int,
        maxAudioTrackPlaybackSpeed: Double
    ): Int {
        val bufferSizeInBytes = delegate.getBufferSizeInBytes(
            minBufferSizeInBytes,
            encoding,
            outputMode,
            pcmFrameSize,
            sampleRate,
            bitrate,
            maxAudioTrackPlaybackSpeed
        )
        if (outputMode == DefaultAudioSink.OUTPUT_MODE_PCM) {
            pcmBufferDurationMillis(
                bufferSizeInBytes = bufferSizeInBytes,
                pcmFrameSize = pcmFrameSize,
                sampleRate = sampleRate
            )?.let(onPcmBufferDurationChanged)
        }
        return bufferSizeInBytes
    }
}

internal fun pcmBufferDurationMillis(
    bufferSizeInBytes: Int,
    pcmFrameSize: Int,
    sampleRate: Int
): Int? {
    if (bufferSizeInBytes <= 0 || pcmFrameSize <= 0 || sampleRate <= 0) return null
    val bytesPerSecond = pcmFrameSize.toLong() * sampleRate.toLong()
    return ((bufferSizeInBytes.toLong() * 1_000L + bytesPerSecond / 2L) / bytesPerSecond)
        .toInt()
}

internal fun spectrumVisualDelayMillis(
    audioTrackBufferDurationMillis: Int,
    outputSampleRate: Int?,
    outputFramesPerBuffer: Int?
): Int {
    val hardwarePeriodMillis = if (
        outputSampleRate != null &&
        outputFramesPerBuffer != null &&
        outputSampleRate > 0 &&
        outputFramesPerBuffer > 0
    ) {
        ((outputFramesPerBuffer.toLong() * 1_000L + outputSampleRate - 1L) /
            outputSampleRate.toLong()).toInt()
    } else {
        DefaultSpectrumHardwarePeriodMillis
    }
    return (
        audioTrackBufferDurationMillis.coerceAtLeast(0) +
            hardwarePeriodMillis * SpectrumHardwarePipelinePeriodCount
    ).coerceIn(0, MaximumSpectrumVisualDelayMillis)
}
