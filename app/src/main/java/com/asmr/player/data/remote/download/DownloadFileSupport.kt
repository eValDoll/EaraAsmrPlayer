package com.asmr.player.data.remote.download

import android.content.Context
import com.asmr.player.data.local.db.entities.DownloadItemEntity
import java.io.File
import com.asmr.player.nas.LibraryWorkspace

const val DOWNLOAD_STATE_FINALIZING = "FINALIZING"
internal const val DOWNLOAD_STATE_IMPORT_FAILED = "IMPORT_FAILED"

private const val DLSITE_PLAY_SCRAMBLED_PART_SUFFIX = ".dlsite-scrambled.part"

internal fun dlsitePlayImagePartFile(outputFile: File): File {
    return File(outputFile.parentFile, outputFile.name + DLSITE_PLAY_SCRAMBLED_PART_SUFFIX)
}

internal fun downloadStagingFile(context: Context, item: DownloadItemEntity): File {
    val root = File(context.getExternalFilesDir(null), LibraryWorkspace.name("download-staging"))
    val partial = File(root, "${item.id}_${item.fileName}")
    return if (LibraryWorkspace.nas) readyNasStagingFile(partial).takeIf { it.isFile } ?: partial else partial
}

internal fun readyNasStagingFile(file: File): File =
    if (file.parentFile?.name == "ready") file else File(File(file.parentFile, "ready"), file.name)

internal fun sealNasStagingFile(file: File): File {
    val ready = readyNasStagingFile(file)
    if (ready != file) {
        ready.parentFile?.mkdirs()
        check(file.renameTo(ready)) { "Unable to checkpoint downloaded file" }
    }
    return ready
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
