package com.asmr.player.subtitle

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.datastore.preferences.core.edit
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.entities.SubtitleEntity
import com.asmr.player.data.remote.download.DownloadStorageGateway
import com.asmr.player.data.settings.SettingsKeys
import com.asmr.player.data.settings.settingsDataStore
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

internal sealed interface GeneratedSubtitleExportResult {
    data class Exported(val reference: String) : GeneratedSubtitleExportResult
    data class ExistingFilePreserved(val reference: String) : GeneratedSubtitleExportResult
    data object UnsupportedTrackLocation : GeneratedSubtitleExportResult
}

/**
 * 把自动生成的中文字幕导出为音频同目录、同名的 LRC 文件。
 *
 * 数据库仍是应用内播放的权威数据源；此文件用于用户管理以及其他播放器识别。
 */
internal class GeneratedSubtitleFileExporter(
    private val context: Context,
    private val database: AppDatabase,
    private val storage: DownloadStorageGateway = DownloadStorageGateway(context)
) {
    suspend fun export(
        trackId: Long,
        overwriteExisting: Boolean = true
    ): GeneratedSubtitleExportResult = withContext(Dispatchers.IO) {
        val track = database.trackDao().getTrackByIdOnce(trackId)
            ?: return@withContext GeneratedSubtitleExportResult.UnsupportedTrackLocation
        val target = resolveTarget(track.path)
            ?: return@withContext GeneratedSubtitleExportResult.UnsupportedTrackLocation
        val subtitles = database.trackDao().getSubtitlesForTrack(trackId).sortedWith(SUBTITLE_ORDER)
        check(subtitles.isNotEmpty()) { "没有可导出的字幕" }
        val existing = storage.findFile(target.directory, target.fileName)
        if (existing != null && !overwriteExisting) {
            return@withContext GeneratedSubtitleExportResult.ExistingFilePreserved(existing)
        }
        val reference = storage.ensureFile(
            directory = target.directory,
            name = target.fileName,
            mimeType = LRC_MIME_TYPE
        )
        storage.openOutput(reference).buffered().use { output ->
            output.write(renderChineseLrc(subtitles).toByteArray(Charsets.UTF_8))
        }
        GeneratedSubtitleExportResult.Exported(reference)
    }

    private fun resolveTarget(trackPath: String): SubtitleExportTarget? {
        val normalizedPath = trackPath.trim()
        if (
            normalizedPath.isBlank() ||
            normalizedPath.startsWith("http://", ignoreCase = true) ||
            normalizedPath.startsWith("https://", ignoreCase = true) ||
            normalizedPath.startsWith("web://", ignoreCase = true)
        ) {
            return null
        }
        if (normalizedPath.startsWith("content://", ignoreCase = true)) {
            return resolveDocumentTarget(Uri.parse(normalizedPath))
        }
        val audioFile = if (normalizedPath.startsWith("file://", ignoreCase = true)) {
            Uri.parse(normalizedPath).path?.let(::File)
        } else {
            File(normalizedPath)
        } ?: return null
        val parent = audioFile.parentFile ?: return null
        return SubtitleExportTarget(
            directory = parent.absolutePath,
            fileName = subtitleFileName(audioFile.name)
        )
    }

    private fun resolveDocumentTarget(audioUri: Uri): SubtitleExportTarget? {
        if (!DocumentsContract.isDocumentUri(context, audioUri)) return null
        val treeId = runCatching { DocumentsContract.getTreeDocumentId(audioUri) }.getOrNull().orEmpty()
        val documentId = runCatching { DocumentsContract.getDocumentId(audioUri) }.getOrNull().orEmpty()
        if (treeId.isBlank() || documentId.isBlank() || documentId == treeId) return null
        val parentId = documentId.substringBeforeLast('/', missingDelimiterValue = treeId)
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(audioUri, parentId)
        return SubtitleExportTarget(
            directory = parentUri.toString(),
            fileName = subtitleFileName(storage.displayName(audioUri))
        )
    }

    private data class SubtitleExportTarget(
        val directory: String,
        val fileName: String
    )

    private companion object {
        const val LRC_MIME_TYPE = "text/plain"
    }
}

internal data class GeneratedSubtitleBackfillSummary(
    val exportedCount: Int,
    val preservedCount: Int,
    val unavailableCount: Int
)

/** 升级后只执行一次，把旧版本已存入数据库的生成字幕补写到音频目录。 */
internal class GeneratedSubtitleFileBackfill(
    private val context: Context,
    private val database: AppDatabase
) {
    suspend fun runOnce(): GeneratedSubtitleBackfillSummary? = withContext(Dispatchers.IO) {
        val completedVersion = context.settingsDataStore.data.first()[
            SettingsKeys.GENERATED_SUBTITLE_FILE_BACKFILL_VERSION
        ] ?: 0
        if (completedVersion >= BACKFILL_VERSION) return@withContext null

        val activeTrackIds = database.subtitleTaskDao().getAllItems()
            .mapTo(mutableSetOf()) { item -> item.trackId }
        val trackIds = database.trackDao().getTrackIdsWithGeneratedSubtitles()
            .filterNot(activeTrackIds::contains)
        val exporter = GeneratedSubtitleFileExporter(context, database)
        val overwritePreviousExport = completedVersion == PREVIOUS_EXPORT_VERSION
        var exportedCount = 0
        var preservedCount = 0
        var unavailableCount = 0
        trackIds.forEach { trackId ->
            when (
                runCatching {
                    exporter.export(trackId, overwriteExisting = overwritePreviousExport)
                }.getOrNull()
            ) {
                is GeneratedSubtitleExportResult.Exported -> exportedCount += 1
                is GeneratedSubtitleExportResult.ExistingFilePreserved -> preservedCount += 1
                GeneratedSubtitleExportResult.UnsupportedTrackLocation,
                null -> unavailableCount += 1
            }
            yield()
        }
        context.settingsDataStore.edit { preferences ->
            preferences[SettingsKeys.GENERATED_SUBTITLE_FILE_BACKFILL_VERSION] = BACKFILL_VERSION
        }
        GeneratedSubtitleBackfillSummary(
            exportedCount = exportedCount,
            preservedCount = preservedCount,
            unavailableCount = unavailableCount
        )
    }

    private companion object {
        const val PREVIOUS_EXPORT_VERSION = 1
        const val BACKFILL_VERSION = 2
    }
}

internal fun subtitleFileName(audioFileName: String): String {
    val normalized = audioFileName.trim().ifBlank { "subtitle" }
    val baseName = normalized.substringBeforeLast('.', missingDelimiterValue = normalized)
        .ifBlank { normalized }
    return "$baseName.lrc"
}

internal fun renderChineseLrc(subtitles: List<SubtitleEntity>): String {
    val lines = subtitles.flatMap { subtitle ->
        normalizeLrcText(subtitle.text)
            .map { text -> "[${formatLrcTime(subtitle.startMs)}]$text" }
    }
    return if (lines.isEmpty()) "" else lines.joinToString(separator = "\r\n", postfix = "\r\n")
}

private fun normalizeLrcText(value: String): List<String> {
    return value.replace("\r\n", "\n")
        .replace('\r', '\n')
        .lineSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .toList()
}

private fun formatLrcTime(timeMs: Long): String {
    val safeTimeMs = timeMs.coerceAtLeast(0L)
    val minutes = safeTimeMs / 60_000L
    val seconds = safeTimeMs / 1_000L % 60L
    val milliseconds = safeTimeMs % 1_000L
    return String.format(Locale.ROOT, "%02d:%02d.%03d", minutes, seconds, milliseconds)
}
