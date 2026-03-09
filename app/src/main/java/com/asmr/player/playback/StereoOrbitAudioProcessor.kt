package com.asmr.player.playback

import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@UnstableApi
class StereoOrbitAudioProcessor : BaseAudioProcessor() {

    @Volatile
    private var enabled: Boolean = false

    @Volatile
    private var autoOrbitEnabled: Boolean = false

    @Volatile
    private var orbitSpeedDegPerSec: Float = 25f

    @Volatile
    private var distance: Float = 5f

    @Volatile
    private var azimuthDeg: Float = 0f

    private var passthrough = false
    private var sampleRateHz = 44_100

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    fun setAutoOrbitEnabled(enabled: Boolean) {
        this.autoOrbitEnabled = enabled
    }

    fun setOrbitSpeedDegPerSec(speed: Float) {
        orbitSpeedDegPerSec = speed.coerceIn(0f, 360f)
    }

    fun setDistance(distance: Float) {
        this.distance = distance.coerceIn(0f, 20f)
    }

    fun setAzimuthDeg(deg: Float) {
        azimuthDeg = ((deg % 360f) + 360f) % 360f
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        sampleRateHz = inputAudioFormat.sampleRate
        passthrough = inputAudioFormat.channelCount != 2 ||
            (inputAudioFormat.encoding != androidx.media3.common.C.ENCODING_PCM_16BIT &&
                inputAudioFormat.encoding != androidx.media3.common.C.ENCODING_PCM_FLOAT)
        return inputAudioFormat
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
        val bytesPerSample = if (encoding == androidx.media3.common.C.ENCODING_PCM_FLOAT) 4 else 2
        val frameCount = countBytes / (bytesPerSample * 2)
        val dtSec = if (sampleRateHz > 0) frameCount.toDouble() / sampleRateHz.toDouble() else 0.0

        val autoOrbitSnapshot = autoOrbitEnabled
        val speedDeg = orbitSpeedDegPerSec
        var az = azimuthDeg
        if (autoOrbitSnapshot && dtSec > 0.0) {
            az = (az + (speedDeg * dtSec.toFloat())) % 360f
            azimuthDeg = az
        }

        val theta = az.toDouble() * PI / 180.0
        val pan = sin(theta).toFloat().coerceIn(-1f, 1f)
        val t = ((pan + 1f) * (PI / 4.0)).toFloat()
        val leftPanGain = cos(t.toDouble()).toFloat()
        val rightPanGain = sin(t.toDouble()).toFloat()

        val d = distance
        val distanceGain = (1f / (1f + 0.07f * d)).coerceIn(0.35f, 1f)
        val leftGain = (leftPanGain * distanceGain).coerceIn(0f, 1f)
        val rightGain = (rightPanGain * distanceGain).coerceIn(0f, 1f)

        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        if (encoding == androidx.media3.common.C.ENCODING_PCM_FLOAT) {
            while (inputBuffer.remaining() >= 8) {
                val left = inputBuffer.float
                val right = inputBuffer.float
                outputBuffer.putFloat((left * leftGain).coerceIn(-1f, 1f))
                outputBuffer.putFloat((right * rightGain).coerceIn(-1f, 1f))
            }
            if (inputBuffer.hasRemaining()) {
                outputBuffer.put(inputBuffer)
            }
        } else {
            while (inputBuffer.remaining() >= 4) {
                val left = inputBuffer.short
                val right = inputBuffer.short
                val outLeft = (left * leftGain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                val outRight = (right * rightGain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                outputBuffer.putShort(outLeft)
                outputBuffer.putShort(outRight)
            }
            if (inputBuffer.hasRemaining()) {
                outputBuffer.put(inputBuffer)
            }
        }
        outputBuffer.flip()
    }
}

