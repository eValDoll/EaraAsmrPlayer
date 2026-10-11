package com.asmr.player.ui.common

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private object TrackFileSizeCache {
    private const val MissingSize = Long.MIN_VALUE
    private val values = ConcurrentHashMap<String, Long>()

    fun getKnown(path: String): Long? = values[path]

    fun resolveKnownSize(cached: Long): Long? {
        return when (cached) {
            MissingSize -> null
            else -> cached
        }
    }

    fun put(path: String, size: Long?) {
        values[path] = size ?: MissingSize
    }
}

private fun cacheTrackFileSize(path: String, size: Long?) {
    val trimmed = path.trim()
    if (trimmed.isBlank()) return
    TrackFileSizeCache.put(trimmed, size)
}

internal fun queryCachedTrackFileSize(
    context: Context,
    path: String
): Long? {
    val trimmed = path.trim()
    if (trimmed.isBlank()) return null
    TrackFileSizeCache.getKnown(trimmed)?.let { cached ->
        return TrackFileSizeCache.resolveKnownSize(cached)
    }
    val size = queryTrackFileSize(context, trimmed)
    cacheTrackFileSize(trimmed, size)
    return size
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
