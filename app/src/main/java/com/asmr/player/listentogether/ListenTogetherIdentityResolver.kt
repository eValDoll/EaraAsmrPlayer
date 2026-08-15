package com.asmr.player.listentogether

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.media3.common.MediaItem
import com.asmr.player.util.DlsiteWorkNo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ListenTogetherIdentityResolver @Inject constructor(
    private val okHttpClient: OkHttpClient
) {

    suspend fun resolve(
        context: Context,
        mediaItem: MediaItem,
        fallbackRjCode: String? = null
    ): ListenTogetherTrackIdentity? {
        return withContext(Dispatchers.IO) {
            val metadata = mediaItem.mediaMetadata
            val extras = metadata.extras
            val mediaId = mediaItem.mediaId.trim()
            val rawSourcePath = mediaItem.localConfiguration?.uri?.toString().orEmpty().trim().ifBlank { mediaId }
            if (rawSourcePath.isBlank()) return@withContext null

            val rjCode = DlsiteWorkNo.extractWorkNo(
                listOfNotNull(
                    extras?.getString("rj_code"),
                    fallbackRjCode,
                    mediaId,
                    rawSourcePath,
                    metadata.albumTitle?.toString(),
                    metadata.title?.toString()
                ).joinToString(" ")
            )
            if (rjCode.isBlank()) return@withContext null

            val fileProbe = probeFile(context, rawSourcePath) ?: return@withContext null
            val totalSize = fileProbe.fileSizeBytes
            val streamFactory = fileProbe.inputStreamFactory

            val (fingerprint, algorithm, effectiveSize) = if (totalSize > 0L) {
                val hashHex = XxHash64.hashStreamHexWithSize(
                    inputStreamFactory = streamFactory ?: return@withContext null,
                    fileSizeBytes = totalSize
                )
                Triple("size:${totalSize}_xxh64:$hashHex", "size+xxh64", totalSize)
            } else {
                val stream = streamFactory?.invoke() ?: return@withContext null
                val hashHex = stream.use { XxHash64.hashStreamHex(it) }
                Triple("prefix_xxh64:$hashHex", "prefix+xxh64", 0L)
            }

            val mediaKind = if (extras?.getBoolean("is_video") == true || mediaItem.localConfiguration?.mimeType?.startsWith("video/") == true) {
                ListenTogetherMediaKind.VIDEO
            } else {
                ListenTogetherMediaKind.AUDIO
            }

            ListenTogetherTrackIdentity(
                albumKey = rjCode,
                mediaFingerprint = fingerprint,
                fileSizeBytes = effectiveSize,
                fingerprintAlgorithm = algorithm,
                mediaKind = mediaKind,
                sourcePath = rawSourcePath,
                mediaId = mediaId,
                rjCode = rjCode,
                displayTitle = metadata.title?.toString().orEmpty(),
                displayArtist = metadata.artist?.toString().orEmpty()
            )
        }
    }

    private data class FileProbe(
        val fileSizeBytes: Long,
        val inputStreamFactory: (() -> InputStream?)?
    )

    private fun probeFile(context: Context, sourcePath: String): FileProbe? {
        val normalized = sourcePath.trim()
        if (normalized.isBlank()) return null
        if (normalized.startsWith("http://", ignoreCase = true) || normalized.startsWith("https://", ignoreCase = true)) {
            val rangeRequest = Request.Builder().url(normalized).header("Range", "bytes=0-10239").build()
            val rangeResponse = runCatching {
                okHttpClient.newCall(rangeRequest).execute()
            }.getOrNull() ?: return null

            if (!rangeResponse.isSuccessful) {
                rangeResponse.close()
                return null
            }

            val totalSize = rangeResponse.header("Content-Range")
                ?.substringAfterLast('/')?.trim()?.toLongOrNull()?.takeIf { it > 0L }
                ?: 0L

            val bodyStream = rangeResponse.body?.byteStream()
            if (bodyStream == null) {
                rangeResponse.close()
                return null
            }

            val inputStreamFactory: () -> InputStream? = {
                object : InputStream() {
                    override fun read() = bodyStream.read()
                    override fun read(b: ByteArray, off: Int, len: Int) = bodyStream.read(b, off, len)
                    override fun close() {
                        runCatching { bodyStream.close() }
                        runCatching { rangeResponse.close() }
                    }
                    override fun available() = bodyStream.available()
                }
            }
            return FileProbe(fileSizeBytes = totalSize, inputStreamFactory = inputStreamFactory)
        }
        return when {
            normalized.startsWith("content://", ignoreCase = true) -> {
                val uri = Uri.parse(normalized)
                val size = queryContentSize(context, uri) ?: return null
                FileProbe(
                    fileSizeBytes = size,
                    inputStreamFactory = { runCatching { context.contentResolver.openInputStream(uri) }.getOrNull() }
                )
            }

            normalized.startsWith("file://", ignoreCase = true) -> {
                val path = runCatching { Uri.parse(normalized).path }.getOrNull().orEmpty()
                val file = File(path)
                if (!file.exists() || !file.isFile) return null
                FileProbe(
                    fileSizeBytes = file.length().coerceAtLeast(0L),
                    inputStreamFactory = { runCatching { file.inputStream() }.getOrNull() }
                )
            }

            else -> {
                val file = File(normalized)
                if (!file.exists() || !file.isFile) return null
                FileProbe(
                    fileSizeBytes = file.length().coerceAtLeast(0L),
                    inputStreamFactory = { runCatching { file.inputStream() }.getOrNull() }
                )
            }
        }
    }

    private fun queryContentSize(context: Context, uri: Uri): Long? {
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(DocumentsContract.Document.COLUMN_SIZE, OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val documentIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val openableIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                when {
                    documentIndex >= 0 && !cursor.isNull(documentIndex) -> cursor.getLong(documentIndex)
                    openableIndex >= 0 && !cursor.isNull(openableIndex) -> cursor.getLong(openableIndex)
                    else -> null
                }
            }
        }.getOrNull()?.takeIf { it > 0L }
    }
}
