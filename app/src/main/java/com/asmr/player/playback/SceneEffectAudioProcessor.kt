package com.asmr.player.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.asmr.player.data.settings.SceneEffectPresets
import com.asmr.player.data.settings.SceneDelayTap
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.tanh

@UnstableApi
class SceneEffectAudioProcessor : BaseAudioProcessor(), RuntimeAudioProcessor {

    private val lock = Any()

    private var pendingEnabled = false
    private var pendingPresetId = SceneEffectPresets.DefaultPresetId
    private var pendingAmount = SceneEffectPresets.DefaultAmount
    private var settingsDirty = true
    @Volatile
    private var runtimeActive = false

    private var passthrough = false
    private var activeSampleRate = 0
    private var activeChannels = 0

    private var dryMix = 1f
    private var directMix = 0f
    private var reflectionMix = 0f
    private var feedback = 0f
    private var monoMix = 0f
    private var stereoWidth = 1f
    private var outputGain = 1f
    private var softClipDrive = 0f

    private var delayLeft = FloatArray(1)
    private var delayRight = FloatArray(1)
    private var delayIndex = 0
    private var delayTaps: List<ResolvedDelayTap> = emptyList()
    private var feedbackDelaySamples = 0
    private var feedbackStateLeft = 0f
    private var feedbackStateRight = 0f

    private var highPassAlpha = 0f
    private var highPassStages = 0
    private var highPassInLeft = FloatArray(0)
    private var highPassInRight = FloatArray(0)
    private var highPassOutLeft = FloatArray(0)
    private var highPassOutRight = FloatArray(0)

    private var lowPassAlpha = 1f
    private var lowPassStages = 0
    private var lowPassLeft = FloatArray(0)
    private var lowPassRight = FloatArray(0)
    private val frameOut = FloatArray(2)

    fun setEnabled(enabled: Boolean) {
        synchronized(lock) {
            if (pendingEnabled != enabled) {
                pendingEnabled = enabled
                settingsDirty = true
                updateRuntimeActiveLocked()
            }
        }
    }

    fun setPreset(presetId: String) {
        synchronized(lock) {
            if (pendingPresetId != presetId) {
                pendingPresetId = presetId
                settingsDirty = true
                updateRuntimeActiveLocked()
            }
        }
    }

    fun setAmount(amountPercent: Int) {
        val normalized = amountPercent.coerceIn(0, 100)
        synchronized(lock) {
            if (pendingAmount != normalized) {
                pendingAmount = normalized
                settingsDirty = true
                updateRuntimeActiveLocked()
            }
        }
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        passthrough =
            inputAudioFormat.channelCount != 2 ||
                (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
                    inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT)
        synchronized(lock) {
            settingsDirty = true
            updateRuntimeActiveLocked()
        }
        return inputAudioFormat
    }

    override fun isRuntimeActive(): Boolean = runtimeActive

    override fun onFlush() {
        clearState()
        rebuildIfNeeded(force = true)
    }

    override fun onReset() {
        passthrough = false
        activeSampleRate = 0
        activeChannels = 0
        delayLeft = FloatArray(1)
        delayRight = FloatArray(1)
        delayIndex = 0
        delayTaps = emptyList()
        feedbackDelaySamples = 0
        highPassInLeft = FloatArray(0)
        highPassInRight = FloatArray(0)
        highPassOutLeft = FloatArray(0)
        highPassOutRight = FloatArray(0)
        lowPassLeft = FloatArray(0)
        lowPassRight = FloatArray(0)
        clearState()
        synchronized(lock) {
            settingsDirty = true
            updateRuntimeActiveLocked()
        }
    }

    private fun updateRuntimeActiveLocked() {
        runtimeActive = !passthrough && pendingEnabled && pendingAmount > 0
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return

        rebuildIfNeeded(force = false)

        val count = inputBuffer.remaining()
        val outputBuffer = replaceOutputBuffer(count)
        outputBuffer.order(ByteOrder.LITTLE_ENDIAN)

        if (passthrough || (directMix == 0f && reflectionMix == 0f && dryMix == 1f)) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        val encoding = inputAudioFormat.encoding
        if (encoding == C.ENCODING_PCM_FLOAT) {
            while (inputBuffer.remaining() >= 8) {
                val left = inputBuffer.float
                val right = inputBuffer.float
                processStereoFrame(left, right, frameOut)
                outputBuffer.putFloat(frameOut[0])
                outputBuffer.putFloat(frameOut[1])
            }
        } else {
            while (inputBuffer.remaining() >= 4) {
                val left = inputBuffer.short / 32768f
                val right = inputBuffer.short / 32768f
                processStereoFrame(left, right, frameOut)
                outputBuffer.putShort(floatToShort(frameOut[0]))
                outputBuffer.putShort(floatToShort(frameOut[1]))
            }
        }

        if (inputBuffer.hasRemaining()) {
            outputBuffer.put(inputBuffer)
        }
        outputBuffer.flip()
    }

    private fun rebuildIfNeeded(force: Boolean) {
        val sampleRate = inputAudioFormat.sampleRate
        val channels = inputAudioFormat.channelCount

        val enabled: Boolean
        val presetId: String
        val amount: Int
        synchronized(lock) {
            if (!force && !settingsDirty && sampleRate == activeSampleRate && channels == activeChannels) {
                return
            }
            enabled = pendingEnabled
            presetId = pendingPresetId
            amount = pendingAmount
            settingsDirty = false
        }

        activeSampleRate = sampleRate
        activeChannels = channels

        if (passthrough || !enabled || sampleRate <= 0 || channels != 2 || amount <= 0) {
            applyBypass()
            clearState()
            return
        }

        val preset = SceneEffectPresets.resolve(presetId)
        val intensity = amount.coerceIn(0, 100) / 100f
        val wetBoost = 1f + 0.45f * intensity * intensity
        val aggressiveBlend = (intensity * (1f + 0.20f * intensity)).coerceAtMost(1.2f)
        val profile = preset.profile

        dryMix = lerp(1f, profile.dryMix, aggressiveBlend).coerceIn(0f, 1f)
        directMix = profile.directMix * intensity * wetBoost
        reflectionMix = profile.reflectionMix * intensity * wetBoost
        feedback = (profile.feedback * intensity * wetBoost).coerceIn(0f, 0.88f)
        monoMix = (profile.monoMix * intensity * wetBoost).coerceIn(0f, 1f)
        stereoWidth = lerp(1f, profile.stereoWidth, aggressiveBlend).coerceIn(0f, 1.25f)
        outputGain = lerp(1f, profile.outputGain, intensity)
        softClipDrive = profile.softClipDrive * intensity * wetBoost

        highPassStages = if (profile.highPassHz > 0f) profile.highPassStages.coerceIn(0, 4) else 0
        lowPassStages = if (profile.lowPassHz > 0f) profile.lowPassStages.coerceIn(0, 4) else 0
        highPassAlpha = if (highPassStages > 0) computeHighPassAlpha(profile.highPassHz, sampleRate) else 0f
        lowPassAlpha = if (lowPassStages > 0) computeLowPassAlpha(profile.lowPassHz, sampleRate) else 1f

        highPassInLeft = FloatArray(highPassStages)
        highPassInRight = FloatArray(highPassStages)
        highPassOutLeft = FloatArray(highPassStages)
        highPassOutRight = FloatArray(highPassStages)
        lowPassLeft = FloatArray(lowPassStages)
        lowPassRight = FloatArray(lowPassStages)

        delayTaps = preset.profile.taps.mapNotNull { it.resolve(sampleRate) }
        feedbackDelaySamples = delayTaps.maxOfOrNull { it.delaySamples } ?: 0
        val maxDelay = (feedbackDelaySamples + 4).coerceAtLeast(1)
        delayLeft = FloatArray(maxDelay)
        delayRight = FloatArray(maxDelay)
        delayIndex = 0
        feedbackStateLeft = 0f
        feedbackStateRight = 0f
    }

    private fun applyBypass() {
        dryMix = 1f
        directMix = 0f
        reflectionMix = 0f
        feedback = 0f
        monoMix = 0f
        stereoWidth = 1f
        outputGain = 1f
        softClipDrive = 0f
        delayTaps = emptyList()
        feedbackDelaySamples = 0
        highPassStages = 0
        lowPassStages = 0
    }

    private fun clearState() {
        delayLeft.fill(0f)
        delayRight.fill(0f)
        delayIndex = 0
        feedbackStateLeft = 0f
        feedbackStateRight = 0f
        highPassInLeft.fill(0f)
        highPassInRight.fill(0f)
        highPassOutLeft.fill(0f)
        highPassOutRight.fill(0f)
        lowPassLeft.fill(0f)
        lowPassRight.fill(0f)
    }

    private fun processStereoFrame(inputLeft: Float, inputRight: Float, out: FloatArray) {
        val mono = (inputLeft + inputRight) * 0.5f
        val widthLeft = mono + (inputLeft - mono) * stereoWidth
        val widthRight = mono + (inputRight - mono) * stereoWidth
        val sourceLeft = lerp(widthLeft, mono, monoMix)
        val sourceRight = lerp(widthRight, mono, monoMix)

        val filteredLeft = filterChain(sourceLeft, isLeft = true)
        val filteredRight = filterChain(sourceRight, isLeft = false)

        var reflectedLeft = 0f
        var reflectedRight = 0f
        if (delayTaps.isNotEmpty()) {
            for (tap in delayTaps) {
                val readIndex = wrappedDelayIndex(delayIndex - tap.delaySamples)
                val tapLeft = delayLeft[readIndex]
                val tapRight = delayRight[readIndex]
                val cross = tap.crossfeed.coerceIn(0f, 1f)
                val direct = 1f - cross
                reflectedLeft += (tapLeft * direct + tapRight * cross) * tap.gain
                reflectedRight += (tapRight * direct + tapLeft * cross) * tap.gain
            }
        }

        val delayedFeedbackLeft = if (feedbackDelaySamples > 0) delayLeft[wrappedDelayIndex(delayIndex - feedbackDelaySamples)] else 0f
        val delayedFeedbackRight = if (feedbackDelaySamples > 0) delayRight[wrappedDelayIndex(delayIndex - feedbackDelaySamples)] else 0f
        val writeLeft = filteredLeft + delayedFeedbackLeft * feedback
        val writeRight = filteredRight + delayedFeedbackRight * feedback
        delayLeft[delayIndex] = writeLeft.coerceIn(-1.25f, 1.25f)
        delayRight[delayIndex] = writeRight.coerceIn(-1.25f, 1.25f)
        delayIndex = wrappedDelayIndex(delayIndex + 1)

        var outLeft = inputLeft * dryMix + filteredLeft * directMix + reflectedLeft * reflectionMix
        var outRight = inputRight * dryMix + filteredRight * directMix + reflectedRight * reflectionMix

        outLeft *= outputGain
        outRight *= outputGain

        if (softClipDrive > 0f) {
            outLeft = softClip(outLeft, softClipDrive)
            outRight = softClip(outRight, softClipDrive)
        }

        out[0] = outLeft.coerceIn(-1f, 1f)
        out[1] = outRight.coerceIn(-1f, 1f)
    }

    private fun filterChain(sample: Float, isLeft: Boolean): Float {
        var out = sample

        if (highPassStages > 0) {
            for (stage in 0 until highPassStages) {
                val inputPrev = if (isLeft) highPassInLeft[stage] else highPassInRight[stage]
                val outputPrev = if (isLeft) highPassOutLeft[stage] else highPassOutRight[stage]
                val filtered = highPassAlpha * (outputPrev + out - inputPrev)
                if (isLeft) {
                    highPassInLeft[stage] = out
                    highPassOutLeft[stage] = filtered
                } else {
                    highPassInRight[stage] = out
                    highPassOutRight[stage] = filtered
                }
                out = filtered
            }
        }

        if (lowPassStages > 0) {
            for (stage in 0 until lowPassStages) {
                val prev = if (isLeft) lowPassLeft[stage] else lowPassRight[stage]
                val filtered = prev + lowPassAlpha * (out - prev)
                if (isLeft) {
                    lowPassLeft[stage] = filtered
                } else {
                    lowPassRight[stage] = filtered
                }
                out = filtered
            }
        }

        return out
    }

    private fun computeLowPassAlpha(cutoffHz: Float, sampleRate: Int): Float {
        val safeCutoff = cutoffHz.coerceIn(10f, sampleRate * 0.45f)
        val dt = 1f / sampleRate.coerceAtLeast(1)
        val rc = 1f / (2f * PI.toFloat() * safeCutoff)
        return (dt / (rc + dt)).coerceIn(0.0001f, 1f)
    }

    private fun computeHighPassAlpha(cutoffHz: Float, sampleRate: Int): Float {
        val safeCutoff = cutoffHz.coerceIn(10f, sampleRate * 0.45f)
        val dt = 1f / sampleRate.coerceAtLeast(1)
        val rc = 1f / (2f * PI.toFloat() * safeCutoff)
        return (rc / (rc + dt)).coerceIn(0.0001f, 0.9999f)
    }

    private fun wrappedDelayIndex(index: Int): Int {
        val size = delayLeft.size.coerceAtLeast(1)
        var wrapped = index % size
        if (wrapped < 0) wrapped += size
        return wrapped
    }

    private fun floatToShort(value: Float): Short {
        return (value.coerceIn(-1f, 1f) * 32767f)
            .toInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            .toShort()
    }

    private fun lerp(start: Float, end: Float, amount: Float): Float {
        return start + (end - start) * amount.coerceIn(0f, 1f)
    }

    private fun softClip(value: Float, drive: Float): Float {
        val scale = (1f + drive * 4f).toDouble()
        val shaped = tanh((value * scale).coerceIn(-8.0, 8.0))
        val normalizer = tanh(scale).takeIf { kotlin.math.abs(it) > 1e-6 } ?: 1.0
        return (shaped / normalizer).toFloat().coerceIn(-1f, 1f)
    }

    private data class ResolvedDelayTap(
        val delaySamples: Int,
        val gain: Float,
        val crossfeed: Float
    )

    private fun SceneDelayTap.resolve(sampleRate: Int): ResolvedDelayTap? {
        if (delayMs <= 0 || gain <= 0f || sampleRate <= 0) return null
        val samples = ((delayMs.toLong() * sampleRate) / 1000L).toInt().coerceAtLeast(1)
        return ResolvedDelayTap(
            delaySamples = samples,
            gain = gain,
            crossfeed = crossfeed.coerceIn(0f, 1f)
        )
    }
}
