package com.asmr.player.playback

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

@UnstableApi
class BalanceAudioProcessor : BaseAudioProcessor() {

    private var balance = 0f // -1.0 (Left) to 1.0 (Right)
    private var leftGain = 1f
    private var rightGain = 1f
    private var passthrough = false

    fun setBalance(balance: Float) {
        if (this.balance != balance) {
            this.balance = balance.coerceIn(-1f, 1f)
            // Linear gain reduction
            leftGain = if (balance > 0) 1f - balance else 1f
            rightGain = if (balance < 0) 1f + balance else 1f
        }
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        passthrough = inputAudioFormat.channelCount != 2 ||
            (inputAudioFormat.encoding != androidx.media3.common.C.ENCODING_PCM_16BIT &&
                inputAudioFormat.encoding != androidx.media3.common.C.ENCODING_PCM_FLOAT)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return

        val count = inputBuffer.remaining()
        val outputBuffer = replaceOutputBuffer(count)
        outputBuffer.order(ByteOrder.LITTLE_ENDIAN)

        if (passthrough || (leftGain == 1f && rightGain == 1f)) {
            outputBuffer.put(inputBuffer)
        } else {
            inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
            val encoding = inputAudioFormat.encoding
            if (encoding == androidx.media3.common.C.ENCODING_PCM_FLOAT) {
                while (inputBuffer.remaining() >= 8) {
                    val left = inputBuffer.float
                    val right = inputBuffer.float
                    val newLeft = (left * leftGain).coerceIn(-1f, 1f)
                    val newRight = (right * rightGain).coerceIn(-1f, 1f)
                    outputBuffer.putFloat(newLeft)
                    outputBuffer.putFloat(newRight)
                }
                if (inputBuffer.hasRemaining()) {
                    outputBuffer.put(inputBuffer)
                }
            } else {
                while (inputBuffer.remaining() >= 4) {
                    val left = inputBuffer.short
                    val right = inputBuffer.short
                    val newLeft = (left * leftGain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    val newRight = (right * rightGain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    outputBuffer.putShort(newLeft)
                    outputBuffer.putShort(newRight)
                }
                if (inputBuffer.hasRemaining()) {
                    outputBuffer.put(inputBuffer)
                }
            }
        }
        outputBuffer.flip()
    }
}
