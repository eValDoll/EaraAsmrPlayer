package com.asmr.player.util

import com.asmr.player.i18n.appString
import com.asmr.player.R

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.system.Os
import java.io.File

/** 外部文件管理器不参与进程锁；在昂贵处理前后核对源文件，失效时停止提交。 */
internal data class LocalFileSnapshot(
    val identity: String,
    val size: Long,
    val modified: Long,
) {
    companion object {
        fun read(context: Context, reference: String): LocalFileSnapshot {
            check(reference.isNotBlank()) { appString(R.string.ui_invalid_local_file_path) }
            if (!reference.startsWith("content://")) {
                val path = if (reference.startsWith("file://")) Uri.parse(reference).path else reference
                val source = File(checkNotNull(path))
                check(source.exists() && source.canRead()) { appString(R.string.ui_the_local_file_was_moved_deleted_or_is) }
                val stat = try {
                    Os.stat(path)
                } catch (error: Exception) {
                    throw IllegalStateException(appString(R.string.ui_the_local_file_was_moved_deleted_or_is), error)
                }
                return LocalFileSnapshot("${stat.st_dev}:${stat.st_ino}", stat.st_size, source.lastModified())
            }
            val columns = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            )
            return context.contentResolver.query(Uri.parse(reference), columns, null, null, null)?.use { cursor ->
                check(cursor.moveToFirst()) { appString(R.string.ui_the_local_file_was_moved_or_deleted) }
                fun string(column: String): String = cursor.getColumnIndex(column).let {
                    if (it >= 0 && !cursor.isNull(it)) cursor.getString(it).orEmpty() else ""
                }
                fun number(column: String): Long = cursor.getColumnIndex(column).let {
                    if (it >= 0 && !cursor.isNull(it)) cursor.getLong(it) else -1L
                }
                LocalFileSnapshot(
                    string(columns[0]) + ":" + string(columns[1]),
                    number(columns[2]), number(columns[3]),
                )
            } ?: error(appString(R.string.ui_unable_to_check_local_file))
        }
    }

    fun requireUnchanged(context: Context, reference: String) {
        check(this == read(context, reference)) { appString(R.string.ui_the_local_file_changed_during_this_task_writing) }
    }
}
