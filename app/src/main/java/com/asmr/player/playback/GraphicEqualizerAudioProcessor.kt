package com.asmr.player.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sinh

@UnstableApi
class GraphicEqualizerAudioProcessor : BaseAudioProcessor() {

    companion object {
        private val BandCentersHz = floatArrayOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
        private const val BandwidthOctaves = 1f
        private const val FlatThresholdDb = 0.01f
        private const val MinCenterHz = 10f
    }

    private val lock = Any()

    private var passthrough = false
    private var activeFilters: Array<BiquadCoefficients> = emptyArray()
    private var state1: Array<FloatArray> = emptyArray()
    private var state2: Array<FloatArray> = emptyArray()
    private var activeChannelCount = 0
    private var activeSampleRate = 0

    private var pendingEnabled = false
    private var pendingLevels = IntArray(BandCentersHz.size)
    private var settingsDirty = true

    fun setEnabled(enabled: Boolean) {
        synchronized(lock) {
            if (pendingEnabled != enabled) {
                pendingEnabled = enabled
                settingsDirty = true
            }
        }
    }

    fun setBandLevels(levels: List<Int>) {
        val normalized = IntArray(BandCentersHz.size) { index -> levels.getOrNull(index) ?: 0 }
        synchronized(lock) {
            if (!pendingLevels.contentEquals(normalized)) {
                pendingLevels = normalized
                settingsDirty = true
            }
        }
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        passthrough =
            inputAudioFormat.channelCount <= 0 ||
                (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
                    inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT)
        synchronized(lock) {
            settingsDirty = true
        }
        return inputAudioFormat
    }

    override fun onFlush() {
        clearFilterState()
    }

    override fun onReset() {
        passthrough = false
        activeFilters = emptyArray()
        state1 = emptyArray()
        state2 = emptyArray()
        activeChannelCount = 0
        activeSampleRate = 0
        synchronized(lock) {
            settingsDirty = true
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return

        rebuildFiltersIfNeeded(force = false)

        val count = inputBuffer.remaining()
        val outputBuffer = replaceOutputBuffer(count)
        outputBuffer.order(ByteOrder.LITTLE_ENDIAN)

        if (passthrough || activeFilters.isEmpty()) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        val encoding = inputAudioFormat.encoding
        val channelCount = inputAudioFormat.channelCount.coerceAtLeast(1)

        if (encoding == C.ENCODING_PCM_FLOAT) {
            while (inputBuffer.remaining() >= channelCount * 4) {
                for (channel in 0 until channelCount) {
                    val sample = processSample(inputBuffer.float, channel).coerceIn(-1f, 1f)
                    outputBuffer.putFloat(sample)
                }
            }
        } else {
            while (inputBuffer.remaining() >= channelCount * 2) {
                for (channel in 0 until channelCount) {
                    val input = inputBuffer.short / 32768f
                    val sample = processSample(input, channel).coerceIn(-1f, 1f)
                    val output = (sample * 32767f)
                        .toInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        .toShort()
                    outputBuffer.putShort(output)
                }
            }
        }

        if (inputBuffer.hasRemaining()) {
            outputBuffer.put(inputBuffer)
        }
        outputBuffer.flip()
    }

    private fun rebuildFiltersIfNeeded(force: Boolean) {
        val sampleRate = inputAudioFormat.sampleRate
        val channelCount = inputAudioFormat.channelCount

        val enabled: Boolean
        val levels: IntArray
        synchronized(lock) {
            if (!force && !settingsDirty && sampleRate == activeSampleRate && channelCount == activeChannelCount) {
                return
            }
            enabled = pendingEnabled
            levels = pendingLevels.copyOf()
            settingsDirty = false
        }

        activeSampleRate = sampleRate
        activeChannelCount = channelCount

        if (passthrough || !enabled || sampleRate <= 0 || channelCount <= 0) {
            activeFilters = emptyArray()
            clearFilterState()
            return
        }

        val filters = ArrayList<BiquadCoefficients>(BandCentersHz.size)
        for (index in levels.indices) {
            val gainDb = levels[index] / 100f
            if (abs(gainDb) < FlatThresholdDb) continue
            filters += buildPeakingFilter(
                centerHz = BandCentersHz[index],
                gainDb = gainDb,
                sampleRate = sampleRate
            )
        }

        activeFilters = filters.toTypedArray()
        state1 = Array(activeFilters.size) { FloatArray(channelCount) }
        state2 = Array(activeFilters.size) { FloatArray(channelCount) }
    }

    private fun buildPeakingFilter(centerHz: Float, gainDb: Float, sampleRate: Int): BiquadCoefficients {
        if (sampleRate <= 0) return BiquadCoefficients.Identity

        val safeCenterHz = centerHz
            .coerceAtLeast(MinCenterHz)
            .coerceAtMost(sampleRate * 0.45f)
        val omega = (2.0 * PI * safeCenterHz / sampleRate).toFloat()
        val sinOmega = sin(omega)
        if (abs(sinOmega) < 1e-6f) return BiquadCoefficients.Identity

        val alpha = (sinOmega * sinh((ln(2.0) / 2.0) * BandwidthOctaves * omega / sinOmega)).toFloat()
        val gain = 10.0.pow(gainDb / 40.0).toFloat()
        val cosOmega = cos(omega)

        val b0 = 1f + alpha * gain
        val b1 = -2f * cosOmega
        val b2 = 1f - alpha * gain
        val a0 = 1f + alpha / gain
        val a1 = -2f * cosOmega
        val a2 = 1f - alpha / gain

        if (abs(a0) < 1e-6f) return BiquadCoefficients.Identity

        return BiquadCoefficients(
            b0 = b0 / a0,
            b1 = b1 / a0,
            b2 = b2 / a0,
            a1 = a1 / a0,
            a2 = a2 / a0
        )
    }

    private fun processSample(sample: Float, channel: Int): Float {
        var output = sample
        for (index in activeFilters.indices) {
            val coefficients = activeFilters[index]
            val next = coefficients.b0 * output + state1[index][channel]
            state1[index][channel] = coefficients.b1 * output - coefficients.a1 * next + state2[index][channel]
            state2[index][channel] = coefficients.b2 * output - coefficients.a2 * next
            output = next
        }
        return output
    }

    private fun clearFilterState() {
        for (index in state1.indices) {
            state1[index].fill(0f)
            state2[index].fill(0f)
        }
    }

    private data class BiquadCoefficients(
        val b0: Float,
        val b1: Float,
        val b2: Float,
        val a1: Float,
        val a2: Float
    ) {
        companion object {
            val Identity = BiquadCoefficients(
                b0 = 1f,
                b1 = 0f,
                b2 = 0f,
                a1 = 0f,
                a2 = 0f
            )
        }
    }
}
