package com.asmr.player.data.repository

import android.content.Context
import android.media.AudioFormat
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import androidx.media3.common.C
import androidx.media3.common.Format
import com.asmr.player.util.AudioTechnicalMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

// 单条目通知，避免一个文件解析完成后使整张列表重新收集、转换。
internal object AudioMetadataCache {
    internal class Entry {
        internal val mutableMetadata = MutableStateFlow<AudioTechnicalMetadata?>(null)
        val metadata: StateFlow<AudioTechnicalMetadata?> = mutableMetadata.asStateFlow()
        @Volatile internal var localAttempted = false
    }

    private val entries = LruCache<String, Entry>(512)
    private val localReadMutex = Mutex()

    @Synchronized
    fun entry(path: String): Entry {
        val key = path.trim()
        return entries.get(key) ?: Entry().also { entries.put(key, it) }
    }

    suspend fun loadLocal(context: Context, path: String, entry: Entry) {
        if (entry.localAttempted || entry.metadata.value?.quality != null || path.isBlank()) return
        val uri = Uri.parse(path.trim())
        if (uri.scheme != null && uri.scheme !in listOf("file", "content")) return
        withContext(Dispatchers.IO) {
            localReadMutex.withLock {
                if (entry.localAttempted || entry.metadata.value?.quality != null) return@withLock
                val metadata = readLocalMetadata(context, path)
                if (metadata?.quality != null || entry.mutableMetadata.value == null) {
                    entry.mutableMetadata.value = metadata
                }
                entry.localAttempted = true
            }
        }
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun recordPlayback(path: String, format: Format, durationMs: Long = 0L) {
        if (path.isBlank()) return
        val entry = entry(path)
        val metadata = AudioTechnicalMetadata(
            sampleRate = format.sampleRate,
            bitrate = format.averageBitrate,
            channelCount = format.channelCount,
            bitsPerSample = when (format.pcmEncoding) {
                C.ENCODING_PCM_16BIT -> 16
                C.ENCODING_PCM_24BIT -> 24
                C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 32
                C.ENCODING_PCM_8BIT -> 8
                else -> 0
            },
            mimeType = format.sampleMimeType.orEmpty(),
            durationSeconds = durationMs.takeIf { it > 0 }?.div(1000.0) ?: entry.metadata.value?.durationSeconds ?: 0.0,
        )
        entry.mutableMetadata.value = metadata
    }

    private fun readLocalMetadata(context: Context, path: String): AudioTechnicalMetadata? {
        val extractor = MediaExtractor()
        return try {
            val uri = Uri.parse(path)
            if (uri.scheme == "content" || uri.scheme == "file") {
                extractor.setDataSource(context, uri, null)
            } else {
                extractor.setDataSource(path)
            }
            val format = (0 until extractor.trackCount).asSequence()
                .map(extractor::getTrackFormat)
                .firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                ?: return null
            var bits = when (format.intOrZero(MediaFormat.KEY_PCM_ENCODING)) {
                AudioFormat.ENCODING_PCM_16BIT -> 16
                AudioFormat.ENCODING_PCM_8BIT -> 8
                AudioFormat.ENCODING_PCM_FLOAT -> 32
                AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24
                AudioFormat.ENCODING_PCM_32BIT -> 32
                else -> format.intOrZero("bits-per-sample")
            }
            var bitrate = format.intOrZero(MediaFormat.KEY_BIT_RATE)
            var sampleRate = format.intOrZero(MediaFormat.KEY_SAMPLE_RATE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && (bits == 0 || bitrate == 0 || sampleRate == 0)) {
                val retriever = MediaMetadataRetriever()
                try {
                    if (uri.scheme == "content" || uri.scheme == "file") retriever.setDataSource(context, uri)
                    else retriever.setDataSource(path)
                    if (bits == 0) bits = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)?.toIntOrNull() ?: 0
                    if (bitrate == 0) bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0
                    if (sampleRate == 0) sampleRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull() ?: 0
                } catch (_: Exception) {
                    // 保留提取器已获得的参数；未知参数不推测。
                } finally {
                    retriever.release()
                }
            }
            AudioTechnicalMetadata(
                sampleRate, bitrate, format.intOrZero(MediaFormat.KEY_CHANNEL_COUNT), bits,
                format.getString(MediaFormat.KEY_MIME).orEmpty(),
                durationSeconds = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                    format.getLong(MediaFormat.KEY_DURATION).coerceAtLeast(0L) / 1_000_000.0
                } else 0.0,
            )
        } catch (_: Exception) {
            null
        } finally {
            extractor.release()
        }
    }

    private fun MediaFormat.intOrZero(key: String): Int =
        if (containsKey(key)) runCatching { getInteger(key) }.getOrDefault(0) else 0
}
