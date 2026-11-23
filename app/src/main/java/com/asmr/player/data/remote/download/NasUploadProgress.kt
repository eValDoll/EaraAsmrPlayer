package com.asmr.player.data.remote.download

import android.net.Uri
import com.asmr.player.nas.LibraryWorkspace
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal data class NasUploadProgress(val sent: Long, val total: Long, val bytesPerSecond: Long) {
    val fraction: Float get() = if (total > 0) (sent.toDouble() / total).toFloat().coerceIn(0f, 1f) else 0f
    val percent: Int get() = (fraction * 100).toInt()
}

internal class NasUploadTracker(private val nanoTime: () -> Long = System::nanoTime) {
    private val transfers = MutableStateFlow(emptyMap<String, NasUploadProgress>())
    val active = transfers.asStateFlow()

    suspend fun <T> track(
        workId: String,
        total: Long,
        writeFiles: suspend (copy: (InputStream, OutputStream) -> Unit) -> T,
    ): T {
        val context = currentCoroutineContext()
        val buffer = ByteArray(64 * 1024)
        var sent = 0L
        var lastSent = 0L
        var lastTime = nanoTime()
        fun report(speed: Long) = transfers.update { it + (workId to NasUploadProgress(sent, total, speed)) }

        report(0L)
        try {
            return writeFiles { input, output ->
                while (true) {
                    context.ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    sent += count
                    val now = nanoTime()
                    val elapsedMs = (now - lastTime) / 1_000_000L
                    if (sent == total || elapsedMs >= 1_000L) {
                        report(if (sent == total) 0L else (sent - lastSent) * 1_000L / elapsedMs)
                        lastSent = sent
                        lastTime = now
                    }
                }
            }
        } finally {
            transfers.update { it - workId }
        }
    }

    companion object {
        val shared = NasUploadTracker()
    }
}

internal suspend fun <T> publishDownloadFiles(
    directory: String,
    workId: String,
    uploadSize: () -> Long,
    writeFiles: suspend (copy: (InputStream, OutputStream) -> Unit) -> T,
): T = if (LibraryWorkspace.owns(Uri.parse(directory))) {
    NasUploadTracker.shared.track(workId, uploadSize(), writeFiles)
} else {
    writeFiles { input, output -> input.copyTo(output) }
}
