package com.asmr.player.cache

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class DiskCache(
    private val directory: File,
    private val maxSizeBytes: Long,
    private val ttlMs: Long
) {
    private val lock = Any()
    private val magic = byteArrayOf('I'.code.toByte(), 'C'.code.toByte(), 'M'.code.toByte(), '1'.code.toByte())

    data class Entry(
        val bytes: ByteArray,
        val width: Int,
        val height: Int
    )

    init {
        directory.mkdirs()
    }

    fun get(key: String): Entry? = synchronized(lock) {
        val f = fileForKey(key)
        if (!f.exists()) return@synchronized null
        val now = System.currentTimeMillis()
        runCatching {
            BufferedInputStream(FileInputStream(f)).use { input ->
                val m = ByteArray(4)
                if (input.read(m) != 4) return@synchronized null
                if (!m.contentEquals(magic)) return@synchronized null
                val createdAt = readLong(input)
                val w = readInt(input)
                val h = readInt(input)
                if (ttlMs > 0 && now - createdAt > ttlMs) {
                    f.delete()
                    return@synchronized null
                }
                val bytes = input.readBytes()
                f.setLastModified(now)
                Entry(bytes = bytes, width = w, height = h)
            }
        }.getOrNull()
    }

    fun put(key: String, entry: Entry) = synchronized(lock) {
        val f = fileForKey(key)
        val tmp = File(directory, "${f.name}.tmp")
        val now = System.currentTimeMillis()
        runCatching {
            BufferedOutputStream(FileOutputStream(tmp)).use { out ->
                out.write(magic)
                writeLong(out, now)
                writeInt(out, entry.width)
                writeInt(out, entry.height)
                out.write(entry.bytes)
            }
            if (f.exists()) f.delete()
            tmp.renameTo(f)
            f.setLastModified(now)
            trimToSize()
        }.onFailure {
            tmp.delete()
        }
    }

    fun remove(key: String) = synchronized(lock) {
        fileForKey(key).delete()
    }

    fun clear() = synchronized(lock) {
        directory.listFiles()?.forEach { it.delete() }
    }

    private fun fileForKey(key: String): File {
        return File(directory, "$key.bin")
    }

    private fun trimToSize() {
        val files = directory.listFiles()?.filter { it.isFile } ?: return
        var total = files.sumOf { it.length() }
        if (total <= maxSizeBytes) return
        val sorted = files.sortedBy { it.lastModified() }
        for (f in sorted) {
            if (total <= maxSizeBytes) break
            val len = f.length()
            if (f.delete()) total -= len
        }
    }

    private fun readInt(input: BufferedInputStream): Int {
        val b1 = input.read()
        val b2 = input.read()
        val b3 = input.read()
        val b4 = input.read()
        if (b4 == -1) return 0
        return (b1 shl 24) or (b2 shl 16) or (b3 shl 8) or b4
    }

    private fun readLong(input: BufferedInputStream): Long {
        var v = 0L
        for (i in 0 until 8) {
            val b = input.read()
            if (b == -1) return 0L
            v = (v shl 8) or (b.toLong() and 0xFFL)
        }
        return v
    }

    private fun writeInt(out: BufferedOutputStream, v: Int) {
        out.write((v ushr 24) and 0xFF)
        out.write((v ushr 16) and 0xFF)
        out.write((v ushr 8) and 0xFF)
        out.write(v and 0xFF)
    }

    private fun writeLong(out: BufferedOutputStream, v: Long) {
        for (i in 7 downTo 0) {
            out.write(((v ushr (8 * i)) and 0xFF).toInt())
        }
    }
}
