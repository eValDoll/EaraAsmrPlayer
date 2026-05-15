package com.asmr.player.ui.common

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import com.asmr.player.util.Formatting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal data class AudioMetaText(
    val leadingText: String,
    val trailingText: String
)

@Composable
internal fun rememberAudioMetaText(
    sourcePath: String,
    durationSeconds: Double?,
    prefixSegments: List<String> = emptyList(),
    suffixSegments: List<String> = emptyList()
): String {
    val context = LocalContext.current
    val sizeText by produceState<String?>(initialValue = null, sourcePath) {
        value = withContext(Dispatchers.IO) {
            queryTrackFileSize(context, sourcePath)
        }?.let(Formatting::formatFileSize)
    }
    return buildAudioMetaText(
        durationSeconds = durationSeconds,
        sizeText = sizeText,
        prefixSegments = prefixSegments,
        suffixSegments = suffixSegments
    )
}

@Composable
internal fun rememberTrackMetaLine(
    path: String,
    durationSeconds: Double?
): String {
    return rememberAudioMetaText(
        sourcePath = path,
        durationSeconds = durationSeconds
    )
}

internal fun buildAudioMetaText(
    durationSeconds: Double?,
    sizeText: String?,
    prefixSegments: List<String> = emptyList(),
    suffixSegments: List<String> = emptyList()
): String {
    val meta = buildAudioMeta(durationSeconds, sizeText, prefixSegments, suffixSegments)
    return listOf(meta.leadingText, meta.trailingText)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
}

internal fun buildAudioMeta(
    durationSeconds: Double?,
    sizeText: String?,
    prefixSegments: List<String> = emptyList(),
    suffixSegments: List<String> = emptyList()
): AudioMetaText {
    val leadingText = (prefixSegments + suffixSegments)
        .mapNotNull { it.trim().takeIf(String::isNotBlank) }
        .joinToString(" · ")
    val trailingText = listOf(
        Formatting.formatTrackSeconds(durationSeconds).takeIf { it.isNotBlank() },
        sizeText?.trim()?.takeIf { it.isNotBlank() }
    ).filterNotNull().joinToString(" · ")
    return AudioMetaText(
        leadingText = leadingText,
        trailingText = trailingText
    )
}

@Composable
internal fun rememberAudioMeta(
    sourcePath: String,
    durationSeconds: Double?,
    prefixSegments: List<String> = emptyList(),
    suffixSegments: List<String> = emptyList()
): AudioMetaText {
    val context = LocalContext.current
    val sizeText by produceState<String?>(initialValue = null, sourcePath) {
        value = withContext(Dispatchers.IO) {
            queryTrackFileSize(context, sourcePath)
        }?.let(Formatting::formatFileSize)
    }
    return buildAudioMeta(
        durationSeconds = durationSeconds,
        sizeText = sizeText,
        prefixSegments = prefixSegments,
        suffixSegments = suffixSegments
    )
}

@Composable
internal fun rememberTrackMeta(
    path: String,
    durationSeconds: Double?
): AudioMetaText {
    return rememberAudioMeta(
        sourcePath = path,
        durationSeconds = durationSeconds
    )
}

internal fun buildTrackMetaLine(
    durationSeconds: Double?,
    sizeText: String?
): String {
    return buildAudioMetaText(durationSeconds = durationSeconds, sizeText = sizeText)
}

internal fun queryTrackFileSize(
    context: Context,
    path: String
): Long? {
    val trimmed = path.trim()
    if (trimmed.isBlank() || trimmed.startsWith("http", ignoreCase = true)) return null
    return when {
        trimmed.startsWith("content://", ignoreCase = true) -> {
            runCatching {
                context.contentResolver.query(
                    Uri.parse(trimmed),
                    arrayOf(DocumentsContract.Document.COLUMN_SIZE, OpenableColumns.SIZE),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (!cursor.moveToFirst()) {
                        null
                    } else {
                        val documentIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                        val openableIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        when {
                            documentIndex >= 0 && !cursor.isNull(documentIndex) -> cursor.getLong(documentIndex)
                            openableIndex >= 0 && !cursor.isNull(openableIndex) -> cursor.getLong(openableIndex)
                            else -> null
                        }
                    }
                }
            }.getOrNull()
        }

        trimmed.startsWith("file://", ignoreCase = true) -> {
            runCatching {
                Uri.parse(trimmed).path
                    ?.let(::File)
                    ?.takeIf { it.exists() }
                    ?.length()
            }.getOrNull()
        }

        else -> runCatching {
            File(trimmed)
                .takeIf { it.exists() }
                ?.length()
        }.getOrNull()
    }?.takeIf { it > 0L }
}
