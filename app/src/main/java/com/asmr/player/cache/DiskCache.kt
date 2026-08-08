package com.asmr.player.cache

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

class DiskCache(
    private val directory: File,
    maxSizeBytes: Long,
    private val ttlMs: Long
) {
    private val lock = Any()
    private val magic = byteArrayOf('I'.code.toByte(), 'C'.code.toByte(), 'M'.code.toByte(), '1'.code.toByte())
    private var currentSizeBytes: Long? = null
    private var clearGeneration = 0L
    private val removeGenerations = mutableMapOf<String, Long>()
    private var directoryReady = false
    private var maxSizeBytes = maxSizeBytes.coerceAtLeast(0L)

    data class Entry(
        val bytes: ByteArray,
        val width: Int,
        val height: Int
    )

    fun get(key: String): Entry? = synchronized(lock) {
        if (!ensureDirectoryReady()) return@synchronized null
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
                    val length = f.length()
                    if (f.delete()) {
                        currentSizeBytes = currentSizeBytes?.let { current ->
                            (current - length).coerceAtLeast(0L)
                        }
                    } else if (currentSizeBytes != null) {
                        currentSizeBytes = calculateSizeBytes()
                    }
                    return@synchronized null
                }
                val bytes = input.readBytes()
                f.setLastModified(now)
                Entry(bytes = bytes, width = w, height = h)
            }
        }.getOrNull()
    }

    fun put(key: String, entry: Entry) {
        val (clearGenerationAtStart, removeGenerationAtStart) = synchronized(lock) {
            if (!ensureDirectoryReady()) return
            ensureSizeInitialized()
            clearGeneration to (removeGenerations[key] ?: 0L)
        }
        val f = fileForKey(key)
        val threadKey = System.identityHashCode(Thread.currentThread())
        val tmp = File(
            directory,
            "${f.name}.$threadKey.${System.nanoTime()}.tmp"
        )
        val now = System.currentTimeMillis()
        val writeSucceeded = runCatching {
            BufferedOutputStream(FileOutputStream(tmp)).use { out ->
                out.write(magic)
                writeLong(out, now)
                writeInt(out, entry.width)
                writeInt(out, entry.height)
                out.write(entry.bytes)
            }
        }.isSuccess
        if (!writeSucceeded) {
            tmp.delete()
            return
        }
        synchronized(lock) {
            val invalidated = clearGeneration != clearGenerationAtStart ||
                (removeGenerations[key] ?: 0L) != removeGenerationAtStart
            if (invalidated) {
                tmp.delete()
                return@synchronized
            }
            val previousSize = f.takeIf(File::exists)?.length() ?: 0L
            runCatching {
                if (f.exists() && !f.delete()) throw IOException("Unable to replace disk cache entry")
                if (!tmp.renameTo(f)) throw IOException("Unable to commit disk cache entry")
                f.setLastModified(now)
                currentSizeBytes = ((currentSizeBytes ?: 0L) - previousSize).coerceAtLeast(0L) + f.length()
                trimToSize()
            }.onFailure {
                tmp.delete()
                currentSizeBytes = calculateSizeBytes()
            }
        }
    }

    fun remove(key: String) = synchronized(lock) {
        removeGenerations[key] = (removeGenerations[key] ?: 0L) + 1L
        if (!ensureDirectoryReady()) return@synchronized
        val file = fileForKey(key)
        if (!file.exists()) return@synchronized
        val length = file.length()
        if (file.delete()) {
            currentSizeBytes = currentSizeBytes?.let { current ->
                (current - length).coerceAtLeast(0L)
            }
        } else if (currentSizeBytes != null) {
            currentSizeBytes = calculateSizeBytes()
        }
    }

    fun clear() = synchronized(lock) {
        clearGeneration += 1L
        removeGenerations.clear()
        if (!ensureDirectoryReady()) {
            currentSizeBytes = 0L
            return@synchronized
        }
        directory.listFiles()?.forEach { it.delete() }
        currentSizeBytes = calculateSizeBytes()
    }

    fun updateMaxSizeBytes(maxSizeBytes: Long) = synchronized(lock) {
        this.maxSizeBytes = maxSizeBytes.coerceAtLeast(0L)
        if (!ensureDirectoryReady()) return@synchronized
        ensureSizeInitialized()
        trimToSize()
    }

    fun sizeBytes(): Long = synchronized(lock) {
        if (!ensureDirectoryReady()) return@synchronized 0L
        ensureSizeInitialized()
        currentSizeBytes ?: 0L
    }

    private fun fileForKey(key: String): File {
        return File(directory, "$key.bin")
    }

    private fun trimToSize() {
        val currentSize = currentSizeBytes ?: return
        if (currentSize <= maxSizeBytes) return
        val targetSize = (maxSizeBytes - (maxSizeBytes / 10L)).coerceAtLeast(0L)
        val files = directory.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".bin") }
            ?: return
        val sorted = files.sortedBy { it.lastModified() }
        for (f in sorted) {
            val sizeBeforeDelete = currentSizeBytes ?: break
            if (sizeBeforeDelete <= targetSize) break
            val len = f.length()
            if (f.delete()) {
                currentSizeBytes = (sizeBeforeDelete - len).coerceAtLeast(0L)
            } else {
                currentSizeBytes = calculateSizeBytes()
            }
        }
    }

    private fun ensureSizeInitialized() {
        if (currentSizeBytes != null) return
        var totalSize = 0L
        directory.listFiles()?.forEach { file ->
            if (file.name.endsWith(".tmp")) {
                file.delete()
            } else if (file.isFile && file.name.endsWith(".bin")) {
                totalSize += file.length()
            }
        }
        currentSizeBytes = totalSize
    }

    private fun ensureDirectoryReady(): Boolean {
        if (directoryReady) return true
        directoryReady = directory.isDirectory || directory.mkdirs()
        return directoryReady
    }

    private fun calculateSizeBytes(): Long {
        return directory.listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.name.endsWith(".bin") }
            ?.sumOf(File::length)
            ?: 0L
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
