package com.asmr.player.playback

import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/**
 * 把可即时开关的应用音效合并成一个 Media3 音频处理节点。
 *
 * 原先每个旁路处理器都会复制一次完整 PCM 缓冲；默认音效关闭时一次输入会经历多次无效
 * 内存搬运。组合链只调用当前真正生效的处理器，最终再把结果交给 Media3，因此默认路径只
 * 有一次必要拷贝，启用音效时仍严格保持处理顺序。
 */
@UnstableApi
internal class DynamicAudioProcessorChain(
    processors: Array<out RuntimeAudioProcessor>,
) : AudioProcessor {
    private val processors = processors.copyOf()
    private var pendingOutputAudioFormat = AudioFormat.NOT_SET
    private var outputAudioFormat = AudioFormat.NOT_SET
    private var outputBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded = false

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        var outputFormat = inputAudioFormat
        processors.forEach { processor ->
            outputFormat = processor.configure(outputFormat)
        }
        pendingOutputAudioFormat = outputFormat
        return outputFormat
    }

    override fun isActive(): Boolean = outputAudioFormat != AudioFormat.NOT_SET

    override fun flush() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        inputEnded = false
        outputAudioFormat = pendingOutputAudioFormat
        processors.forEach(RuntimeAudioProcessor::flush)
    }

    override fun reset() {
        flush()
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        pendingOutputAudioFormat = AudioFormat.NOT_SET
        outputAudioFormat = AudioFormat.NOT_SET
        processors.forEach(RuntimeAudioProcessor::reset)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        check(!outputBuffer.hasRemaining()) { "Previous audio output has not been consumed" }

        var currentBuffer = inputBuffer
        var appliedProcessor = false
        processors.forEach { processor ->
            if (processor.isRuntimeActive()) {
                appliedProcessor = true
                processor.queueInput(currentBuffer)
                currentBuffer = processor.output
            }
        }

        outputBuffer = if (appliedProcessor) {
            currentBuffer
        } else {
            // 默认音效全关时直接把输入的独立读取窗口交给 AudioSink。底层 PCM 内存不会复制，
            // 原输入仍按 AudioProcessor 契约推进到末尾。
            inputBuffer.slice().also {
                inputBuffer.position(inputBuffer.limit())
            }
        }
    }

    override fun queueEndOfStream() {
        processors.forEach { processor ->
            if (processor.isRuntimeActive()) processor.queueEndOfStream()
        }
        inputEnded = true
    }

    override fun getOutput(): ByteBuffer {
        val output = outputBuffer
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        return output
    }

    override fun isEnded(): Boolean = inputEnded && !outputBuffer.hasRemaining()
}
