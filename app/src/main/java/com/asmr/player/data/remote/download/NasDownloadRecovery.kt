package com.asmr.player.data.remote.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import com.asmr.player.R
import com.asmr.player.i18n.appString
import com.asmr.player.nas.WebDavHttpException
import com.asmr.player.data.remote.useDownloadResponse
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.EOFException
import java.io.IOException
import java.net.ProtocolException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

internal class DownloadHttpException(val code: Int) : IOException(appString(R.string.ui_audio_download_failed, code))

private val retryableStatusCodes = setOf(408, 429, 500, 502, 503, 504)

internal fun Exception.isTransientNasTransferFailure() = when (this) {
    is DownloadHttpException -> code in retryableStatusCodes
    is WebDavHttpException -> status in retryableStatusCodes
    is SocketException, is SocketTimeoutException, is UnknownHostException, is EOFException -> true
    is ProtocolException -> message?.contains("unexpected end of stream", ignoreCase = true) == true
    else -> false
}

internal suspend fun OkHttpClient.readStagedDownload(request: Request, offset: Long, read: suspend (Response) -> Unit) {
    val restart = newCall(request).useDownloadResponse { response ->
        if (response.code == 416 && offset > 0 &&
            !response.header("Content-Range").equals("bytes */$offset", ignoreCase = true)) {
            true
        } else {
            read(response)
            false
        }
    }
    if (restart) newCall(request.newBuilder().removeHeader("Range").build()).useDownloadResponse(read)
}

private val byteRange = Regex("""bytes (\d+)-(\d+)/(\d+)""", RegexOption.IGNORE_CASE)

internal fun resumedDownloadTotal(header: String?, offset: Long, bodyLength: Long): Long {
    val match = header?.let(byteRange::matchEntire)
    val start = match?.groupValues?.get(1)?.toLongOrNull()
    val end = match?.groupValues?.get(2)?.toLongOrNull()
    val total = match?.groupValues?.get(3)?.toLongOrNull()
    if (start != offset || end == null || total == null || end < offset || end >= total ||
        (bodyLength >= 0 && bodyLength != end - offset + 1)) {
        throw IOException(appString(R.string.ui_download_source_returned_invalid_breakpoint_data))
    }
    return total
}

internal suspend fun CoroutineWorker.foregroundNasTransfer(label: String) {
    val channel = "nas_transfers"
    val title = appString(R.string.ui_download_task)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        applicationContext.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(channel, title, NotificationManager.IMPORTANCE_LOW))
    }
    val notification = NotificationCompat.Builder(applicationContext, channel)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle(title).setContentText(label).setGroup(channel)
        .setOngoing(true).setOnlyAlertOnce(true).build()
    val notificationId = id.hashCode()
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    } else ForegroundInfo(notificationId, notification)
    try {
        setForeground(info)
    } catch (error: IllegalStateException) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            error !is android.app.ForegroundServiceStartNotAllowedException) throw error
        // Android may resume the work after the app has already left the foreground.
    }
}
