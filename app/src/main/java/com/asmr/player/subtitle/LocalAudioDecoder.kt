package com.asmr.player.subtitle

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

internal data class DecodedAudioChunk(
    val channelSamples: List<FloatArray>,
    val startMs: Long,
    val durationMs: Long,
    val totalDurationMs: Long
)

internal class LocalAudioDecoder(
    private val context: Context,
    private val targetSampleRateHz: Int = DEFAULT_TARGET_SAMPLE_RATE
) {
    init {
        require(targetSampleRateHz > 0)
    }

    suspend fun decode(
        path: String,
        startAtMs: Long = 0L,
        onChunk: suspend (DecodedAudioChunk) -> Unit
    ) = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        try {
            setDataSource(extractor, path)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index)
                    .getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: error("文件中没有可解码的音轨")
            extractor.selectTrack(trackIndex)
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME)
                ?: error("无法识别音频编码")
            val totalDurationMs = inputFormat.longOrDefault(MediaFormat.KEY_DURATION) / 1_000L
            val normalizedStartMs = startAtMs.coerceIn(0L, totalDurationMs.coerceAtLeast(startAtMs))
            if (normalizedStartMs > 0L) {
                extractor.seekTo(normalizedStartMs * 1_000L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            }

            decoder = MediaCodec.createDecoderByType(mime).apply {
                configure(inputFormat, null, null, 0)
                start()
            }

            val bufferInfo = MediaCodec.BufferInfo()
            var inputEnded = false
            var outputEnded = false
            var outputFormat = inputFormat
            var resamplers: List<StreamingLinearResampler>? = null
            var emittedSamples = normalizedStartMs * targetSampleRateHz / 1_000L

            suspend fun emit(channelSamples: List<FloatArray>) {
                if (channelSamples.isEmpty() || channelSamples.first().isEmpty()) return
                val sampleCount = channelSamples.first().size
                check(channelSamples.all { it.size == sampleCount }) { "音频声道长度不一致" }
                val startMs = emittedSamples * 1_000L / targetSampleRateHz
                emittedSamples += sampleCount
                onChunk(
                    DecodedAudioChunk(
                        channelSamples = channelSamples,
                        startMs = startMs,
                        durationMs = sampleCount * 1_000L / targetSampleRateHz,
                        totalDurationMs = totalDurationMs
                    )
                )
            }

            while (!outputEnded) {
                coroutineContext.ensureActive()
                if (!inputEnded) {
                    val inputIndex = decoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inputIndex)
                            ?: error("无法取得音频解码输入缓冲区")
                        inputBuffer.clear()
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputEnded = true
                        } else {
                            decoder.queueInputBuffer(
                                inputIndex,
                                0,
                                sampleSize,
                                extractor.sampleTime.coerceAtLeast(0L),
                                0
                            )
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = decoder.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> outputFormat = decoder.outputFormat
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (outputIndex >= 0) {
                        if (
                            bufferInfo.size > 0 &&
                            bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0
                        ) {
                            val outputBuffer = decoder.getOutputBuffer(outputIndex)
                                ?: error("无法取得音频解码输出缓冲区")
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            val sampleRate = outputFormat.intOrDefault(
                                MediaFormat.KEY_SAMPLE_RATE,
                                inputFormat.intOrDefault(MediaFormat.KEY_SAMPLE_RATE, targetSampleRateHz)
                            )
                            val decodedChannels = decodeToChannels(outputBuffer.slice(), outputFormat)
                            val requestedStartUs = normalizedStartMs * 1_000L
                            val bufferStartUs = bufferInfo.presentationTimeUs.coerceAtLeast(0L)
                            val framesToDiscard = framesToDiscardBeforeCheckpoint(
                                bufferStartUs = bufferStartUs,
                                requestedStartUs = requestedStartUs,
                                sampleRate = sampleRate,
                                frameCount = decodedChannels.firstOrNull()?.size ?: 0
                            )
                            val channels = if (framesToDiscard > 0) {
                                decodedChannels.map { samples -> samples.copyOfRange(framesToDiscard, samples.size) }
                            } else {
                                decodedChannels
                            }
                            if (channels.firstOrNull()?.isEmpty() != false) {
                                decoder.releaseOutputBuffer(outputIndex, false)
                                outputEnded = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                                continue
                            }
                            if (resamplers == null) {
                                val retainedStartUs = bufferStartUs + framesToDiscard * 1_000_000L / sampleRate
                                emittedSamples = maxOf(
                                    emittedSamples,
                                    retainedStartUs * targetSampleRateHz / 1_000_000L
                                )
                            }
                            val activeResamplers = resamplers ?: channels.map {
                                StreamingLinearResampler(
                                    inputSampleRate = sampleRate,
                                    outputSampleRate = targetSampleRateHz,
                                    outputChunkSize = targetSampleRateHz * DECODE_CHUNK_DURATION_SECONDS
                                )
                            }.also {
                                resamplers = it
                            }
                            check(activeResamplers.size == channels.size) { "音频声道数在解码期间发生变化" }
                            val completed = activeResamplers.zip(channels).map { (resampler, samples) ->
                                resampler.consume(samples)
                            }
                            val completedCount = completed.firstOrNull()?.size ?: 0
                            check(completed.all { it.size == completedCount }) { "音频声道重采样不同步" }
                            repeat(completedCount) { index ->
                                emit(completed.map { chunks -> chunks[index] })
                            }
                        }
                        outputEnded = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        decoder.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }
            resamplers?.map { it.finish() }?.let { finalChunks ->
                if (finalChunks.any { it != null }) {
                    check(finalChunks.all { it != null }) { "音频声道尾部不同步" }
                    emit(finalChunks.map { requireNotNull(it) })
                }
            }
        } finally {
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun setDataSource(extractor: MediaExtractor, path: String) {
        val uri = Uri.parse(path)
        when (uri.scheme?.lowercase()) {
            "content", "file" -> extractor.setDataSource(context, uri, null)
            else -> extractor.setDataSource(path)
        }
    }

    private fun decodeToChannels(buffer: ByteBuffer, format: MediaFormat): List<FloatArray> {
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        val channels = format.intOrDefault(MediaFormat.KEY_CHANNEL_COUNT, 1).coerceAtLeast(1)
        val encoding = format.intOrDefault(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
        val bytesPerSample = when (encoding) {
            AudioFormat.ENCODING_PCM_8BIT -> 1
            AudioFormat.ENCODING_PCM_16BIT -> 2
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
            AudioFormat.ENCODING_PCM_32BIT, AudioFormat.ENCODING_PCM_FLOAT -> 4
            else -> error("不支持的 PCM 编码：$encoding")
        }
        val frameCount = buffer.remaining() / (bytesPerSample * channels)
        val interleaved = FloatArray(frameCount * channels) {
            readPcmSample(buffer, encoding)
        }
        val retainedChannels = channels.coerceAtMost(MAX_RETAINED_CHANNELS)
        return List(retainedChannels) { channel ->
            FloatArray(frameCount) { frame ->
                interleaved[frame * channels + channel]
            }
        }
    }

    private fun readPcmSample(buffer: ByteBuffer, encoding: Int): Float {
        return when (encoding) {
            AudioFormat.ENCODING_PCM_8BIT -> ((buffer.get().toInt() and 0xff) - 128) / 128f
            AudioFormat.ENCODING_PCM_16BIT -> buffer.short / 32_768f
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> {
                val raw = (buffer.get().toInt() and 0xff) or
                    ((buffer.get().toInt() and 0xff) shl 8) or
                    ((buffer.get().toInt() and 0xff) shl 16)
                val signed = if (raw and 0x800000 != 0) raw or -0x1000000 else raw
                signed / 8_388_608f
            }
            AudioFormat.ENCODING_PCM_32BIT -> buffer.int / 2_147_483_648f
            AudioFormat.ENCODING_PCM_FLOAT -> buffer.float
            else -> error("不支持的 PCM 编码：$encoding")
        }
    }

    private fun MediaFormat.intOrDefault(key: String, default: Int): Int {
        return if (containsKey(key)) getInteger(key) else default
    }

    private fun MediaFormat.longOrDefault(key: String): Long {
        return if (containsKey(key)) getLong(key) else 0L
    }

    companion object {
        private const val DEFAULT_TARGET_SAMPLE_RATE = 16_000
        private const val CODEC_TIMEOUT_US = 10_000L
        private const val DECODE_CHUNK_DURATION_SECONDS = 30
        private const val MAX_RETAINED_CHANNELS = 2
    }
}

internal fun framesToDiscardBeforeCheckpoint(
    bufferStartUs: Long,
    requestedStartUs: Long,
    sampleRate: Int,
    frameCount: Int
): Int {
    require(sampleRate > 0)
    require(frameCount >= 0)
    if (bufferStartUs >= requestedStartUs) return 0
    val missingDurationUs = requestedStartUs - bufferStartUs
    val frames = ((missingDurationUs * sampleRate + 999_999L) / 1_000_000L)
    return frames.coerceAtMost(frameCount.toLong()).toInt()
}
