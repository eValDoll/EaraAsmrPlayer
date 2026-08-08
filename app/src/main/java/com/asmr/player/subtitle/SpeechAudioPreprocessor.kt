package com.asmr.player.subtitle

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

internal object SpeechAudioPreprocessor {
    fun process(samples: FloatArray, sampleRateHz: Int): FloatArray {
        return normalizeSpeechSegment(filterForVad(samples, sampleRateHz), sampleRateHz)
    }

    fun filterForVad(samples: FloatArray, sampleRateHz: Int): FloatArray {
        require(sampleRateHz > 0)
        if (samples.isEmpty()) return samples
        return highPass(samples, sampleRateHz)
    }

    fun normalizeSpeechSegment(samples: FloatArray, sampleRateHz: Int): FloatArray {
        require(sampleRateHz > 0)
        if (samples.isEmpty()) return samples
        val activeRms = estimateActiveRms(samples, sampleRateHz)
        if (activeRms < MIN_MEANINGFUL_RMS) return samples

        val requestedGain = (TARGET_ACTIVE_RMS / activeRms).coerceIn(MIN_GAIN, MAX_GAIN)
        val peak = samples.maxOf { abs(it) }
        val peakSafeGain = if (peak > 0f) TARGET_PEAK / peak else requestedGain
        val gain = minOf(requestedGain, peakSafeGain).coerceAtLeast(MIN_GAIN)
        return FloatArray(samples.size) { index ->
            (samples[index] * gain).coerceIn(-TARGET_PEAK, TARGET_PEAK)
        }
    }

    private fun highPass(samples: FloatArray, sampleRateHz: Int): FloatArray {
        val timeConstant = 1.0 / (2.0 * PI * HIGH_PASS_HZ)
        val sampleInterval = 1.0 / sampleRateHz
        val alpha = (timeConstant / (timeConstant + sampleInterval)).toFloat()
        val output = FloatArray(samples.size)
        var previousInput = samples.first()
        var previousOutput = 0f
        samples.forEachIndexed { index, input ->
            val filtered = alpha * (previousOutput + input - previousInput)
            output[index] = filtered
            previousInput = input
            previousOutput = filtered
        }
        return output
    }

    private fun estimateActiveRms(samples: FloatArray, sampleRateHz: Int): Float {
        val frameSize = (sampleRateHz * FRAME_DURATION_MS / 1_000)
            .coerceAtLeast(1)
        val frameRms = ArrayList<Float>((samples.size + frameSize - 1) / frameSize)
        var offset = 0
        while (offset < samples.size) {
            val end = minOf(offset + frameSize, samples.size)
            var squareSum = 0.0
            for (index in offset until end) {
                val value = samples[index].toDouble()
                squareSum += value * value
            }
            frameRms += sqrt(squareSum / (end - offset)).toFloat()
            offset = end
        }
        if (frameRms.isEmpty()) return 0f

        frameRms.sort()
        val firstActiveIndex = (frameRms.size * ACTIVE_FRAME_PERCENTILE)
            .toInt()
            .coerceIn(0, frameRms.lastIndex)
        var logSum = 0.0
        var count = 0
        for (index in firstActiveIndex..frameRms.lastIndex) {
            val rms = frameRms[index]
            if (rms >= MIN_MEANINGFUL_RMS) {
                logSum += ln(rms.toDouble())
                count += 1
            }
        }
        return if (count > 0) kotlin.math.exp(logSum / count).toFloat() else 0f
    }

    private const val HIGH_PASS_HZ = 70.0
    private const val FRAME_DURATION_MS = 25
    private const val ACTIVE_FRAME_PERCENTILE = 0.6f
    private const val TARGET_ACTIVE_RMS = 0.1f
    private const val TARGET_PEAK = 0.95f
    private const val MIN_MEANINGFUL_RMS = 0.0001f
    private const val MIN_GAIN = 0.5f
    private const val MAX_GAIN = 4f
}
