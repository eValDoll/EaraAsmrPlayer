package com.asmr.player.playback

import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.sqrt

@UnstableApi
class VolumeThresholdAudioProcessor : BaseAudioProcessor() {

    companion object {
        const val MODE_THRESHOLD = 0
        const val MODE_LOUDNESS = 1
    }

    @Volatile
    private var enabled: Boolean = false

    @Volatile
    private var minDb: Float = -24f

    @Volatile
    private var maxDb: Float = -6f

    @Volatile
    private var mode: Int = MODE_LOUDNESS

    @Volatile
    private var loudnessTargetDb: Float = -18f

    private var passthrough = false
    private var currentGain = 1f
    private var limiterGain = 1f
    private var sampleRateHz = 44_100
    private val peakSafety = 0.95f
    private val boostNoiseFloorDb = -75f
    private val gateOpenDb = -70f
    private val gateCloseDb = -75f
    private var gateOpen = false
    private var momentaryEmaPower = 0.0
    private var integratedEmaPower = 0.0
    private var elapsedSec = 0.0

    fun resetForNewItem() {
        currentGain = 1f
        limiterGain = 1f
        gateOpen = false
        momentaryEmaPower = 0.0
        integratedEmaPower = 0.0
        elapsedSec = 0.0
    }

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    fun setMode(mode: Int) {
        this.mode = mode.coerceIn(MODE_THRESHOLD, MODE_LOUDNESS)
    }

    fun setThresholds(minDb: Float, maxDb: Float) {
        var minV = minDb
        var maxV = maxDb
        if (minV >= maxV) {
            minV = maxV - 1f
        }
        this.minDb = minV
        this.maxDb = maxV
    }

    fun setLoudnessTargetDb(targetDb: Float) {
        loudnessTargetDb = targetDb.coerceIn(-60f, 0f)
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        sampleRateHz = inputAudioFormat.sampleRate
        passthrough =
            (inputAudioFormat.encoding != androidx.media3.common.C.ENCODING_PCM_16BIT &&
                inputAudioFormat.encoding != androidx.media3.common.C.ENCODING_PCM_FLOAT)
        resetForNewItem()
        return inputAudioFormat
    }

    override fun onFlush() {
        resetForNewItem()
    }

    override fun onReset() {
        resetForNewItem()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return

        val countBytes = inputBuffer.remaining()
        val outputBuffer = replaceOutputBuffer(countBytes)
        outputBuffer.order(ByteOrder.LITTLE_ENDIAN)

        val enabledSnapshot = enabled
        if (passthrough || !enabledSnapshot) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        val encoding = inputAudioFormat.encoding
        val channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        val bytesPerSample = if (encoding == androidx.media3.common.C.ENCODING_PCM_FLOAT) 4 else 2
        val frameCount = countBytes / (bytesPerSample * channels)
        val dtSec = if (sampleRateHz > 0) frameCount.toDouble() / sampleRateHz.toDouble() else 0.0
        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        val levels = computeLevels(inputBuffer, encoding)
        val rms = levels.rms
        val peak = levels.peak
        val db = if (rms <= 1e-9f) -120f else (20f * log10(rms))

        val modeSnapshot = mode
        val minGain = 0.125f
        if (db >= gateOpenDb) gateOpen = true
        if (db <= gateCloseDb) gateOpen = false

        if (dtSec > 0.0) elapsedSec += dtSec

        var desiredGain = when (modeSnapshot) {
            MODE_LOUDNESS -> {
                if (dtSec > 0.0 && gateOpen) {
                    val power = (rms * rms).toDouble()
                    val alphaMomentary = exp(-dtSec / 0.4)
                    val alphaIntegrated = exp(-dtSec / 3.0)
                    momentaryEmaPower =
                        if (momentaryEmaPower <= 0.0) power else (momentaryEmaPower * alphaMomentary + power * (1.0 - alphaMomentary))
                    integratedEmaPower =
                        if (integratedEmaPower <= 0.0) power else (integratedEmaPower * alphaIntegrated + power * (1.0 - alphaIntegrated))
                }
                val loudPower =
                    if (elapsedSec < 2.0) momentaryEmaPower.coerceAtLeast(0.0) else integratedEmaPower.coerceAtLeast(0.0)
                val loudDb = if (loudPower <= 1e-12) -120f else (10f * log10(loudPower.toFloat()))
                val deltaDb = loudnessTargetDb - loudDb
                var g = dbToGain(deltaDb).coerceIn(minGain, 8f)
                if (!gateOpen && g > 1f) g = 1f
                g
            }
            else -> {
                val minT = minDb
                val maxT = maxDb
                val desiredGainDb = when {
                    db > maxT -> (maxT - db)
                    db < minT && gateOpen -> (minT - db)
                    else -> 0f
                }
                dbToGain(desiredGainDb).coerceIn(minGain, 8f)
            }
        }

        if (desiredGain > 1f && peak > 1e-9f) {
            val peakCap = (peakSafety / peak).coerceAtMost(8f)
            desiredGain = minOf(desiredGain, peakCap)
        }

        if (!gateOpen && desiredGain > 1f) desiredGain = 1f
        val attackMs = if (modeSnapshot == MODE_LOUDNESS) 300.0 else 30.0
        val releaseMs = if (modeSnapshot == MODE_LOUDNESS) 3_000.0 else 200.0
        val (maxUpDbPerSec, maxDownDbPerSec) = if (modeSnapshot == MODE_LOUDNESS) 12f to 24f else 6f to 18f
        val gainEnd =
            limitGainSlew(
                current = currentGain,
                target = smoothToward(currentGain, desiredGain, dtSec, attackMs, releaseMs),
                dtSec = dtSec,
                maxUpDbPerSec = maxUpDbPerSec,
                maxDownDbPerSec = maxDownDbPerSec,
            ).coerceIn(minGain, 8f)

        if (gainEnd == 1f && currentGain == 1f && limiterGain == 1f) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        val limiterThreshold = peakSafety
        val limiterReleaseMs = 80.0
        val limiterReleaseAlpha = if (sampleRateHz > 0) exp(-1.0 / (sampleRateHz.toDouble() * (limiterReleaseMs / 1000.0))).toFloat() else 0f

        if (encoding == androidx.media3.common.C.ENCODING_PCM_FLOAT) {
            processFloat(
                inputBuffer = inputBuffer,
                outputBuffer = outputBuffer,
                channels = channels,
                frameCount = frameCount,
                gainStart = currentGain,
                gainEnd = gainEnd,
                limiterThreshold = limiterThreshold,
                limiterReleaseAlpha = limiterReleaseAlpha,
            )
        } else {
            processShort(
                inputBuffer = inputBuffer,
                outputBuffer = outputBuffer,
                channels = channels,
                frameCount = frameCount,
                gainStart = currentGain,
                gainEnd = gainEnd,
                limiterThreshold = limiterThreshold,
                limiterReleaseAlpha = limiterReleaseAlpha,
            )
        }
        currentGain = gainEnd
        outputBuffer.flip()
    }

    private data class Levels(
        val rms: Float,
        val peak: Float,
    )

    private fun computeLevels(input: ByteBuffer, encoding: Int): Levels {
        val dup = input.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        var sumSq = 0.0
        var peak = 0.0
        var n = 0
        if (encoding == androidx.media3.common.C.ENCODING_PCM_FLOAT) {
            while (dup.remaining() >= 4) {
                val v = dup.float.toDouble()
                val av = kotlin.math.abs(v)
                if (av > peak) peak = av
                sumSq += v * v
                n++
            }
        } else {
            while (dup.remaining() >= 2) {
                val s = dup.short.toInt()
                val v = (s / 32768.0)
                val av = kotlin.math.abs(v)
                if (av > peak) peak = av
                sumSq += v * v
                n++
            }
        }
        if (n <= 0) return Levels(0f, 0f)
        val rms = sqrt(sumSq / n.toDouble()).toFloat()
        return Levels(rms, peak.toFloat().coerceIn(0f, 1f))
    }

    private fun dbToGain(db: Float): Float {
        val dbDouble = db.toDouble()
        return exp(dbDouble * ln(10.0) / 20.0).toFloat()
    }

    private fun smoothToward(current: Float, target: Float, dtSec: Double, attackMs: Double, releaseMs: Double): Float {
        if (dtSec <= 0.0) return target
        val tauSec = if (target < current) (attackMs / 1000.0) else (releaseMs / 1000.0)
        if (tauSec <= 0.0) return target
        val alpha = exp(-dtSec / tauSec).toFloat()
        return target + (current - target) * alpha
    }

    private fun limitGainSlew(
        current: Float,
        target: Float,
        dtSec: Double,
        maxUpDbPerSec: Float,
        maxDownDbPerSec: Float,
    ): Float {
        if (dtSec <= 0.0) return target
        val maxUp = dbToGain((maxUpDbPerSec * dtSec).toFloat())
        val maxDown = dbToGain((maxDownDbPerSec * dtSec).toFloat())
        val minAllowed = (current / maxDown).coerceAtMost(current)
        val maxAllowed = (current * maxUp).coerceAtLeast(current)
        return target.coerceIn(minAllowed, maxAllowed)
    }

    private fun processFloat(
        inputBuffer: ByteBuffer,
        outputBuffer: ByteBuffer,
        channels: Int,
        frameCount: Int,
        gainStart: Float,
        gainEnd: Float,
        limiterThreshold: Float,
        limiterReleaseAlpha: Float,
    ) {
        var gL = limiterGain
        val frames = frameCount.coerceAtLeast(1)
        val inv = if (frames <= 1) 0f else (1f / (frames - 1).toFloat())
        val tmp = FloatArray(channels)
        for (frame in 0 until frames) {
            if (inputBuffer.remaining() < 4 * channels) break
            val t = frame * inv
            val g = gainStart + (gainEnd - gainStart) * t

            var peak = 0f
            for (c in 0 until channels) {
                val v = inputBuffer.float
                tmp[c] = v
                val av = kotlin.math.abs(v)
                if (av > peak) peak = av
            }

            val predictedPeak = peak * g * gL
            if (predictedPeak > limiterThreshold && predictedPeak > 1e-9f) {
                val needed = (limiterThreshold / (peak * g)).coerceIn(0f, 1f)
                gL = minOf(gL, needed)
            } else {
                gL = 1f + (gL - 1f) * limiterReleaseAlpha
            }

            for (c in 0 until channels) {
                val out = tmp[c] * g * gL
                outputBuffer.putFloat(softClip(out))
            }
        }
        limiterGain = gL
    }

    private fun processShort(
        inputBuffer: ByteBuffer,
        outputBuffer: ByteBuffer,
        channels: Int,
        frameCount: Int,
        gainStart: Float,
        gainEnd: Float,
        limiterThreshold: Float,
        limiterReleaseAlpha: Float,
    ) {
        var gL = limiterGain
        val frames = frameCount.coerceAtLeast(1)
        val inv = if (frames <= 1) 0f else (1f / (frames - 1).toFloat())
        val tmp = IntArray(channels)
        for (frame in 0 until frames) {
            if (inputBuffer.remaining() < 2 * channels) break
            val t = frame * inv
            val g = gainStart + (gainEnd - gainStart) * t

            var peak = 0f
            for (c in 0 until channels) {
                val s = inputBuffer.short.toInt()
                tmp[c] = s
                val av = kotlin.math.abs(s).toFloat() / 32768f
                if (av > peak) peak = av
            }

            val predictedPeak = peak * g * gL
            if (predictedPeak > limiterThreshold && predictedPeak > 1e-9f) {
                val needed = (limiterThreshold / (peak * g)).coerceIn(0f, 1f)
                gL = minOf(gL, needed)
            } else {
                gL = 1f + (gL - 1f) * limiterReleaseAlpha
            }

            for (c in 0 until channels) {
                val v = tmp[c].toFloat() / 32768f
                val out = softClip(v * g * gL)
                val sOut = (out * 32767f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                outputBuffer.putShort(sOut)
            }
        }
        limiterGain = gL
    }

    private fun softClip(v: Float): Float {
        val a = kotlin.math.abs(v)
        val t = 0.98f
        if (a <= t) return v
        val sign = if (v < 0f) -1f else 1f
        val x = ((a - t) / (1f - t)).coerceIn(0f, 8f)
        val k = 2.0
        val shaped = (1f - exp((-k * x).toDouble()).toFloat()).coerceIn(0f, 1f)
        val y = t + (1f - t) * shaped
        return (sign * y).coerceIn(-1f, 1f)
    }
}
