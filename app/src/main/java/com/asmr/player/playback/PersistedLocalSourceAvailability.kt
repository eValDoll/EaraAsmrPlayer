package com.asmr.player.playback

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.asmr.player.util.isMissingLocalDocumentFailure
import kotlinx.coroutines.CancellationException
import java.io.File

/** 仅过滤已确认失效的本地来源；授权或提供者暂时不可用时保留队列。调用方须在 IO 线程执行。 */
internal fun isPersistedLocalSourceAvailable(context: Context, source: String): Boolean {
    val value = source.trim()
    if (value.isEmpty()) return false
    val uri = Uri.parse(value)
    return try {
        when (uri.scheme?.lowercase()) {
            "http", "https" -> true
            "content" -> {
                // 直接查询持久化的文档 URI，同时支持 tree/document、单文档和 MediaStore URI。
                context.contentResolver.query(
                    uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
                )?.use { it.moveToFirst() } ?: true
            }
            "file" -> uri.path?.takeIf(String::isNotBlank)?.let { File(it).isFile } ?: true
            null -> File(value).isFile
            else -> true
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: SecurityException) {
        true
    } catch (error: Exception) {
        !isMissingLocalDocumentFailure(error)
    }
}
