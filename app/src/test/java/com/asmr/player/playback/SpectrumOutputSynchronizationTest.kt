package com.asmr.player.playback

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.DefaultAudioSink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@UnstableApi
class SpectrumOutputSynchronizationTest {

    @Test
    fun pcmBufferDurationUsesConfiguredAudioTrackCapacity() {
        assertEquals(
            250,
            pcmBufferDurationMillis(
                bufferSizeInBytes = 48_000,
                pcmFrameSize = 4,
                sampleRate = 48_000
            )
        )
        assertNull(
            pcmBufferDurationMillis(
                bufferSizeInBytes = 48_000,
                pcmFrameSize = 0,
                sampleRate = 48_000
            )
        )
    }

    @Test
    fun visualDelayIncludesAudioTrackBufferAndHardwarePipeline() {
        assertEquals(
            265,
            spectrumVisualDelayMillis(
                audioTrackBufferDurationMillis = 250,
                outputSampleRate = 48_000,
                outputFramesPerBuffer = 240
            )
        )
        assertEquals(
            310,
            spectrumVisualDelayMillis(
                audioTrackBufferDurationMillis = 250,
                outputSampleRate = null,
                outputFramesPerBuffer = null
            )
        )
        assertEquals(
            400,
            spectrumVisualDelayMillis(
                audioTrackBufferDurationMillis = 750,
                outputSampleRate = 48_000,
                outputFramesPerBuffer = 240
            )
        )
    }

    @Test
    fun providerReportsPcmDurationWithoutChangingMedia3BufferChoice() {
        var reportedDurationMillis: Int? = null
        val delegate = DefaultAudioSink.AudioTrackBufferSizeProvider {
                _, _, _, _, _, _, _ ->
            48_000
        }
        val provider = SpectrumOutputBufferSizeProvider(delegate) { durationMillis ->
            reportedDurationMillis = durationMillis
        }

        val selectedBufferSize = provider.getBufferSizeInBytes(
            minBufferSizeInBytes = 8_000,
            encoding = 2,
            outputMode = DefaultAudioSink.OUTPUT_MODE_PCM,
            pcmFrameSize = 4,
            sampleRate = 48_000,
            bitrate = 0,
            maxAudioTrackPlaybackSpeed = 1.0
        )

        assertEquals(48_000, selectedBufferSize)
        assertEquals(250, reportedDurationMillis)
    }
}
