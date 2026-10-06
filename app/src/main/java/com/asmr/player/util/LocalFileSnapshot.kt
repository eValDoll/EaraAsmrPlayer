package com.asmr.player.util

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
            check(reference.isNotBlank()) { "本地文件路径无效" }
            if (!reference.startsWith("content://")) {
                val path = if (reference.startsWith("file://")) Uri.parse(reference).path else reference
                val source = File(checkNotNull(path))
                check(source.exists() && source.canRead()) { "本地文件已移动、删除或无法访问" }
                val stat = try {
                    Os.stat(path)
                } catch (error: Exception) {
                    throw IllegalStateException("本地文件已移动、删除或无法访问", error)
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
                check(cursor.moveToFirst()) { "本地文件已移动或删除" }
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
            } ?: error("无法读取本地文件状态")
        }
    }

    fun requireUnchanged(context: Context, reference: String) {
        check(this == read(context, reference)) { "本地文件在任务期间已被修改，已停止写入" }
    }
}
