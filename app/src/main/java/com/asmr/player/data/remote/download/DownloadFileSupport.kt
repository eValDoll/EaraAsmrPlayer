package com.asmr.player.data.remote.download

import android.content.Context
import com.asmr.player.data.local.db.entities.DownloadItemEntity
import java.io.File

const val DOWNLOAD_STATE_FINALIZING = "FINALIZING"

private const val DLSITE_PLAY_SCRAMBLED_PART_SUFFIX = ".dlsite-scrambled.part"

internal fun dlsitePlayImagePartFile(outputFile: File): File {
    return File(outputFile.parentFile, outputFile.name + DLSITE_PLAY_SCRAMBLED_PART_SUFFIX)
}

internal fun downloadStagingFile(context: Context, item: DownloadItemEntity): File {
    val root = File(context.getExternalFilesDir(null), "download-staging")
    return File(root, "${item.id}_${item.fileName}")
}

internal fun downloadMimeType(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
    "mp3" -> "audio/mpeg"
    "flac" -> "audio/flac"
    "wav" -> "audio/wav"
    "m4a" -> "audio/mp4"
    "ogg", "opus" -> "audio/ogg"
    "mp4" -> "video/mp4"
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "webp" -> "image/webp"
    "lrc" -> "application/octet-stream"
    "srt" -> "application/x-subrip"
    "vtt" -> "text/vtt"
    "txt" -> "text/plain"
    "pdf" -> "application/pdf"
    "zip" -> "application/zip"
    else -> "application/octet-stream"
}

internal fun DownloadItemEntity.hasDlsitePlayImageTransform(): Boolean {
    return dlsitePlayImageSeed != null &&
        (dlsitePlayImageWidth ?: 0) > 0 &&
        (dlsitePlayImageHeight ?: 0) > 0
}
