package com.asmr.player.playback

import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

@UnstableApi
class BalanceAudioProcessor : BaseAudioProcessor(), RuntimeAudioProcessor {

    @Volatile
    private var gains = BalanceGains()
    private var passthrough = false

    fun setBalance(balance: Float) {
        val resolvedBalance = balance.coerceIn(-1f, 1f)
        if (gains.balance == resolvedBalance) return
        gains = BalanceGains(
            balance = resolvedBalance,
            left = if (resolvedBalance > 0f) 1f - resolvedBalance else 1f,
            right = if (resolvedBalance < 0f) 1f + resolvedBalance else 1f,
        )
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        passthrough = inputAudioFormat.channelCount != 2 ||
            (inputAudioFormat.encoding != androidx.media3.common.C.ENCODING_PCM_16BIT &&
                inputAudioFormat.encoding != androidx.media3.common.C.ENCODING_PCM_FLOAT)
        return inputAudioFormat
    }

    override fun isRuntimeActive(): Boolean {
        val currentGains = gains
        return !passthrough && (currentGains.left != 1f || currentGains.right != 1f)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return

        val count = inputBuffer.remaining()
        val outputBuffer = replaceOutputBuffer(count)
        outputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        val currentGains = gains

        if (passthrough || (currentGains.left == 1f && currentGains.right == 1f)) {
            outputBuffer.put(inputBuffer)
        } else {
            inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
            val encoding = inputAudioFormat.encoding
            if (encoding == androidx.media3.common.C.ENCODING_PCM_FLOAT) {
                while (inputBuffer.remaining() >= 8) {
                    val left = inputBuffer.float
                    val right = inputBuffer.float
                    val newLeft = (left * currentGains.left).coerceIn(-1f, 1f)
                    val newRight = (right * currentGains.right).coerceIn(-1f, 1f)
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
                    val newLeft = (left * currentGains.left).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    val newRight = (right * currentGains.right).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
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

private data class BalanceGains(
    val balance: Float = 0f,
    val left: Float = 1f,
    val right: Float = 1f,
)
