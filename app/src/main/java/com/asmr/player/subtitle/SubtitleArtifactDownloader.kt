package com.asmr.player.subtitle

import com.asmr.player.i18n.appString
import com.asmr.player.R

import com.asmr.player.data.remote.NetworkHeaders
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request

internal object SubtitleArtifactDownloader {
    suspend fun download(
        client: OkHttpClient,
        url: String,
        destination: File,
        expectedBytes: Long,
        onProgress: suspend (downloadedBytes: Long) -> Unit
    ) {
        var existingBytes = destination.length().coerceIn(0L, expectedBytes)
        if (destination.length() != existingBytes) {
            check(destination.delete()) { appString(R.string.ui_unable_to_clean_up_invalid_temporary_download_files) }
            existingBytes = 0L
        }
        onProgress(existingBytes)
        if (existingBytes == expectedBytes) return

        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", "Eara-Android")
            .header(NetworkHeaders.HEADER_SILENT_IO_ERROR, NetworkHeaders.SILENT_IO_ERROR_ON)
        if (existingBytes > 0L) requestBuilder.header("Range", "bytes=$existingBytes-")

        client.newCall(requestBuilder.get().build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException(appString(R.string.ui_download_failed, response.code, response.message))
            }
            val append = existingBytes > 0L && response.code == 206
            if (append) {
                val expectedRangePrefix = "bytes $existingBytes-"
                val contentRange = response.header("Content-Range").orEmpty()
                if (!contentRange.startsWith(expectedRangePrefix)) {
                    destination.delete()
                    throw IOException(appString(R.string.ui_download_source_returned_invalid_breakpoint_data))
                }
            }
            if (!append) existingBytes = 0L

            val body = response.body ?: throw IOException(appString(R.string.ui_download_failed_empty_response_body))
            var downloadedBytes = existingBytes
            var lastUpdateAt = 0L
            FileOutputStream(destination, append).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        output.write(buffer, 0, read)
                        downloadedBytes += read.toLong()
                        check(downloadedBytes <= expectedBytes) { appString(R.string.ui_abnormal_download_file_size) }
                        val now = System.nanoTime() / 1_000_000L
                        if (now - lastUpdateAt >= PROGRESS_UPDATE_INTERVAL_MS) {
                            onProgress(downloadedBytes)
                            lastUpdateAt = now
                        }
                    }
                }
                output.flush()
                output.fd.sync()
            }
            if (downloadedBytes != expectedBytes) {
                throw IOException(appString(R.string.ui_incomplete_download_bytes, downloadedBytes, expectedBytes))
            }
            onProgress(downloadedBytes)
        }
    }

    private const val DOWNLOAD_BUFFER_BYTES = 256 * 1024
    private const val PROGRESS_UPDATE_INTERVAL_MS = 250L
}
