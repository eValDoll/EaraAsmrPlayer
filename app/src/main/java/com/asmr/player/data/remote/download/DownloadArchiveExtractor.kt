package com.asmr.player.data.remote.download

import android.content.Context
import androidx.work.*
import com.asmr.player.data.local.db.entities.DownloadItemEntity
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.util.zip.ZipFile

private fun File.copyFrom(input: InputStream) {
    parentFile?.mkdirs()
    FileOutputStream(this).use { output ->
        input.copyTo(output)
    }
}

private fun scoreZipEntryNames(names: List<String>): Int {
    if (names.isEmpty()) return Int.MIN_VALUE
    var score = 0
    names.forEach { name ->
        if (name.isBlank()) {
            score -= 100
            return@forEach
        }
        if ('\uFFFD' in name) score -= 500
        if (name.contains('?')) score -= 20
        if (name.any { it.code in 0xE000..0xF8FF }) score -= 50
        if (name.any { it.isLetterOrDigit() }) score += 10
        if (name.any { it.code in 0x3040..0x30FF }) score += 15
        if (name.any { it.code in 0x4E00..0x9FFF }) score += 15
        if (name.any { it in listOf('【', '】', '〜', '～', '・', '「', '」', '（', '）', '！') }) score += 8
        if (name.endsWith("/")) score += 1
    }
    return score
}

private data class ZipCharsetCandidate(
    val charset: Charset?,
    val score: Int
)

private inline fun <T> useBestEffortZipFile(zipFile: File, block: (ZipFile) -> T): T {
    val charsets = listOf(
        null,
        Charsets.UTF_8,
        Charset.forName("MS932"),
        Charset.forName("Shift_JIS"),
        Charset.forName("GB18030"),
        Charset.forName("Big5")
    )

    var best: ZipCharsetCandidate? = null
    var lastError: Throwable? = null

    charsets.forEach { charset ->
        val zip = runCatching {
            if (charset == null) ZipFile(zipFile) else ZipFile(zipFile, charset)
        }.getOrElse {
            lastError = it
            return@forEach
        }
        zip.use { archive ->
            val names = runCatching {
                archive.entries().asSequence().map { it.name }.take(200).toList()
            }.getOrElse {
                lastError = it
                return@use
            }
            val score = scoreZipEntryNames(names)
            if (best == null || score > best!!.score) {
                best = ZipCharsetCandidate(charset = charset, score = score)
            }
        }
    }

    val selectedCharset = best?.charset
    val selectedZip = runCatching {
        if (selectedCharset == null) ZipFile(zipFile) else ZipFile(zipFile, selectedCharset)
    }.getOrElse {
        throw (lastError ?: it)
    }
    selectedZip.use { return block(it) }
}

private fun unzipIntoRootDirectory(zipFile: File, rootDir: File) {
    useBestEffortZipFile(zipFile) { archive ->
        archive.entries().asSequence().forEach { entry ->
            val rawName = entry.name.replace('\\', '/').trimStart('/')
            if (rawName.isBlank()) return@forEach
            val outFile = File(rootDir, rawName)
            val canonicalRoot = rootDir.canonicalFile
            val canonicalOut = outFile.canonicalFile
            if (!canonicalOut.path.startsWith(canonicalRoot.path)) return@forEach
            if (entry.isDirectory) {
                canonicalOut.mkdirs()
            } else {
                archive.getInputStream(entry).use { input ->
                    canonicalOut.copyFrom(input)
                }
            }
        }
    }
}

internal fun finalizeDlsiteLosslessArchiveIfNeeded(rootDir: File, items: List<DownloadItemEntity>) {
    if (!rootDir.isDirectory) return
    val archive = items.asSequence()
        .filter { item -> item.fileName.equals("dlsite_lossless_archive.zip", ignoreCase = true) }
        .map { item -> File(item.filePath.ifBlank { File(item.targetDir, item.fileName).absolutePath }) }
        .firstOrNull { file ->
            file.exists() &&
                file.isFile &&
                file.parentFile?.absolutePath == rootDir.absolutePath &&
                file.extension.equals("zip", ignoreCase = true)
        }
        ?: return
    unzipIntoRootDirectory(archive, rootDir)
    archive.delete()
}

internal suspend fun finalizeDlsiteLosslessArchiveInStorageIfNeeded(
    context: Context,
    rootDir: String,
    items: List<DownloadItemEntity>,
    storage: DownloadStorageGateway,
) {
    val archiveItem = items.firstOrNull { item ->
        item.fileName.equals("dlsite_lossless_archive.zip", ignoreCase = true)
    } ?: return
    val stagingArchive = downloadStagingFile(context, archiveItem)
    if (!stagingArchive.isFile) return

    useBestEffortZipFile(stagingArchive) { archive ->
        archive.entries().asSequence().forEach { entry ->
            val normalized = entry.name.replace('\\', '/').trimStart('/')
            val segments = normalized.split('/').filter { it.isNotBlank() }
            if (segments.isEmpty() || segments.any { it == "." || it == ".." }) return@forEach
            val parentPath = segments.dropLast(1).joinToString("/")
            val parent = storage.resolveDirectory(rootDir, parentPath)
            if (!entry.isDirectory) {
                val outputReference = storage.ensureFile(
                    directory = parent,
                    name = segments.last(),
                    mimeType = downloadMimeType(segments.last()),
                )
                archive.getInputStream(entry).use { input ->
                    storage.openOutput(outputReference).use { output -> input.copyTo(output) }
                }
            }
        }
    }
    storage.delete(archiveItem.filePath)
    stagingArchive.delete()
}
