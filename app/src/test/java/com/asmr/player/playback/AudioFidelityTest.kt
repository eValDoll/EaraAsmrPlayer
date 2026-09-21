package com.asmr.player.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessingPipeline
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.google.common.collect.ImmutableList
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

@UnstableApi
class AudioFidelityTest {
    @Test
    @Suppress("DEPRECATION")
    fun defaultMedia3ProcessorsPreserveStereoAtNormalSpeed() {
        val processors = DefaultAudioSink.DefaultAudioProcessorChain(Effects().chain)
        val pipeline = AudioProcessingPipeline(ImmutableList.copyOf(processors.audioProcessors))
        val format = AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT)
        assertEquals(format, pipeline.configure(format))
        pipeline.flush()
        repeat(3) {
            val samples = stereoSamples(C.ENCODING_PCM_16BIT)
            val input = samples.toBuffer()
            pipeline.queueInput(input)
            assertEquals(input.limit(), input.position())
            assertArrayEquals(samples, pipeline.output.readBytes())
        }
        pipeline.reset()
    }

    @Test
    fun disabledEffectsPreserveStereoPcmAtCommonSampleRates() {
        for (sampleRate in listOf(44_100, 48_000, 96_000)) {
            for (encoding in listOf(C.ENCODING_PCM_16BIT, C.ENCODING_PCM_FLOAT)) {
                val chain = Effects().chain
                val format = AudioFormat(sampleRate, 2, encoding)
                assertEquals(format, chain.configure(format))
                chain.flush()
                repeat(3) { assertPassthrough(chain, stereoSamples(encoding)) }
                chain.reset()
            }
        }
    }

    @Test
    fun spectrumCaptureDoesNotChangeSamplesOrLeakBetweenChannels() {
        val effects = Effects()
        effects.chain.configure(AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT))
        effects.chain.flush()
        StereoSpectrumBus.registerCaptureConsumer()
        try {
            repeat(3) { assertPassthrough(effects.chain, stereoSamples(C.ENCODING_PCM_16BIT)) }
        } finally {
            StereoSpectrumBus.unregisterCaptureConsumer()
            effects.chain.reset()
        }
    }

    @Test
    fun disablingPreviouslyUsedEffectsImmediatelyRestoresOriginalSamples() {
        for (encoding in listOf(C.ENCODING_PCM_16BIT, C.ENCODING_PCM_FLOAT)) {
            val effects = Effects()
            effects.chain.configure(AudioFormat(48_000, 2, encoding))
            effects.chain.flush()
            effects.eq.setBandLevels(List(10) { 300 })
            effects.eq.setEnabled(true)
            effects.channel.setMode(2)
            effects.orbit.setEnabled(true)
            effects.scene.setEnabled(true)
            effects.threshold.setEnabled(true)
            effects.balance.setBalance(0.5f)
            effects.chain.queueInput(stereoSamples(encoding).toBuffer())
            effects.chain.output.readBytes()

            effects.eq.setEnabled(false)
            effects.channel.setMode(0)
            effects.orbit.setEnabled(false)
            effects.scene.setEnabled(false)
            effects.threshold.setEnabled(false)
            effects.balance.setBalance(0f)

            // 关闭开关后不重新 configure/flush，验证实时旁路不会留下混音或滤波尾音。
            repeat(3) { assertPassthrough(effects.chain, stereoSamples(encoding)) }
            effects.chain.reset()
        }
    }

    private fun assertPassthrough(chain: DynamicAudioProcessorChain, samples: ByteArray) {
        val input = samples.toBuffer()
        chain.queueInput(input)
        val output = chain.output
        assertEquals(input.limit(), input.position())
        // 解码器可以立即复用输入缓冲，输出必须仍保留原始左右声道。
        input.clear()
        while (input.hasRemaining()) input.put(0)
        assertArrayEquals(samples, output.readBytes())
    }

    private fun stereoSamples(encoding: Int): ByteArray {
        // 包含左右单独发声、反相、极小幅度和满幅样本，可检出混单声道、串音、衰减和削波。
        val samples = shortArrayOf(
            32_767, 0, 0, -32_768, 12_000, -12_000, 1, -1,
            -3, 7, 24_631, -17_319, 0, 0, -32_768, 32_767,
        )
        val buffer = ByteBuffer.allocate(samples.size * if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2)
            .order(ByteOrder.nativeOrder())
        samples.forEach {
            if (encoding == C.ENCODING_PCM_FLOAT) buffer.putFloat(it / 32768f) else buffer.putShort(it)
        }
        return buffer.array()
    }

    private class Effects {
        val eq = GraphicEqualizerAudioProcessor()
        val channel = ChannelModeAudioProcessor()
        val orbit = StereoOrbitAudioProcessor()
        val scene = SceneEffectAudioProcessor()
        val threshold = VolumeThresholdAudioProcessor()
        val balance = BalanceAudioProcessor()
        val chain = DynamicAudioProcessorChain(
            arrayOf(
                StereoSpectrumTapAudioProcessor(StereoPcmRingBuffer(frameSize = 4, slotCount = 4)) {},
                eq, channel, orbit, scene, threshold, balance,
            )
        )
    }
}

private fun ByteArray.toBuffer(): ByteBuffer = ByteBuffer.allocateDirect(size)
    .order(ByteOrder.nativeOrder())
    .apply { put(this@toBuffer); flip() }

private fun ByteBuffer.readBytes(): ByteArray = ByteArray(remaining()).also(::get)
