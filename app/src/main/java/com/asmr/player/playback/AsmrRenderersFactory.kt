package com.asmr.player.playback

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

@UnstableApi
class AsmrRenderersFactory(
    context: Context,
    private val balanceAudioProcessor: BalanceAudioProcessor,
    private val stereoOrbitAudioProcessor: StereoOrbitAudioProcessor,
    private val channelModeAudioProcessor: ChannelModeAudioProcessor,
    private val volumeThresholdAudioProcessor: VolumeThresholdAudioProcessor,
    private val spectrumTapAudioProcessor: StereoSpectrumTapAudioProcessor
) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean
    ): AudioSink {
        return DefaultAudioSink.Builder(context)
            .setAudioProcessors(
                arrayOf(
                    spectrumTapAudioProcessor,
                    channelModeAudioProcessor,
                    stereoOrbitAudioProcessor,
                    volumeThresholdAudioProcessor,
                    balanceAudioProcessor
                )
            )
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .build()
    }
}
