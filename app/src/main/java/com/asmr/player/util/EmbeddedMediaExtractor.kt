package com.asmr.player.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset
import java.security.MessageDigest

object EmbeddedMediaExtractor {
    fun extractArtwork(context: Context, pathOrUri: String): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            val p = pathOrUri.trim()
            if (p.startsWith("content://", true)) {
                retriever.setDataSource(context, Uri.parse(p))
            } else {
                retriever.setDataSource(p)
            }
            val bytes = retriever.embeddedPicture ?: return null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    fun saveArtworkToCache(context: Context, albumId: Long, bitmap: Bitmap): String? {
        return saveArtworkToCache(context, "album:$albumId", bitmap)
    }

    fun saveArtworkToCache(context: Context, cacheKey: String, bitmap: Bitmap): String? {
        return runCatching {
            val file = getArtworkCacheFile(context, cacheKey)
            file.parentFile?.mkdirs()
            file.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
            }
            file.absolutePath
        }.getOrNull()
    }

    fun getArtworkCacheFile(context: Context, cacheKey: String): File {
        val dir = File(context.filesDir, "embedded_covers")
        val name = sha256Hex(cacheKey).take(32)
        return File(dir, "k_${name}.jpg")
    }

    fun extractEmbeddedLyricsEntries(context: Context, pathOrUri: String): List<SubtitleEntry> {
        val text = readEmbeddedLyricsText(context, pathOrUri) ?: return emptyList()
        val hasLrcTs = text.contains('[') && text.contains(']')
        return if (hasLrcTs) {
            SubtitleParser.parseText("lrc", text)
        } else {
            val durationMs = readDurationMs(context, pathOrUri)
            val end = if (durationMs > 0L) durationMs else 60_000L
            listOf(SubtitleEntry(0, end, text))
        }
    }

    private fun readDurationMs(context: Context, pathOrUri: String): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            val p = pathOrUri.trim()
            if (p.startsWith("content://", true)) {
                retriever.setDataSource(context, Uri.parse(p))
            } else {
                retriever.setDataSource(p)
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun readUslt(context: Context, pathOrUri: String): String? {
        return runCatching {
            val p = pathOrUri.trim()
            val input: InputStream? = if (p.startsWith("content://", true)) {
                context.contentResolver.openInputStream(Uri.parse(p))
            } else {
                File(p).inputStream()
            }
            input?.use { parseId3Uslt(it) }
        }.getOrNull()
    }

    private fun readEmbeddedLyricsText(context: Context, pathOrUri: String): String? {
        val p = pathOrUri.trim()
        val inputRaw: InputStream = if (p.startsWith("content://", true)) {
            context.contentResolver.openInputStream(Uri.parse(p)) ?: return null
        } else {
            val f = File(p)
            if (!f.exists() || !f.isFile) return null
            f.inputStream()
        }
        return inputRaw.use { raw ->
            val input = if (raw.markSupported()) raw else BufferedInputStream(raw)
            input.mark(64 * 1024)
            val header = ByteArray(12)
            val n = input.read(header)
            input.reset()
            val isId3 = n >= 3 && header[0].toInt() == 'I'.code && header[1].toInt() == 'D'.code && header[2].toInt() == '3'.code
            val isFlac = n >= 4 && header[0].toInt() == 'f'.code && header[1].toInt() == 'L'.code && header[2].toInt() == 'a'.code && header[3].toInt() == 'C'.code
            val isMp4 = n >= 8 && header[4].toInt() == 'f'.code && header[5].toInt() == 't'.code && header[6].toInt() == 'y'.code && header[7].toInt() == 'p'.code
            when {
                isId3 -> parseId3Uslt(input)
                isFlac -> parseFlacVorbisLyrics(input)
                isMp4 -> parseMp4Lyrics(input)
                else -> null
            }?.trim()?.takeIf { it.isNotBlank() }
        }
    }

    private fun parseId3Uslt(input: InputStream): String? {
        val header = ByteArray(10)
        if (input.read(header) != 10) return null
        if (!(header[0].toInt() == 'I'.code && header[1].toInt() == 'D'.code && header[2].toInt() == '3'.code)) return null
        val verMajor = header[3].toInt() and 0xFF
        val tagSize = synchsafeToInt(header, 6)
        var remaining = tagSize
        while (remaining >= 10) {
            val fh = ByteArray(10)
            val r = input.read(fh)
            if (r != 10) break
            val id = String(fh, 0, 4, Charset.forName("ISO-8859-1"))
            val size = if (verMajor >= 4) synchsafeToInt(fh, 4) else normalInt(fh, 4)
            remaining -= 10
            if (size <= 0 || size > remaining) {
                input.skip(remaining.toLong())
                remaining = 0
                break
            }
            val payload = ByteArray(size)
            val pr = input.read(payload)
            if (pr != size) break
            remaining -= size
            if (id == "USLT") {
                return decodeUsltPayload(payload)
            }
        }
        return null
    }

    private fun parseFlacVorbisLyrics(input: InputStream): String? {
        val sig = ByteArray(4)
        if (input.read(sig) != 4) return null
        if (!(sig[0].toInt() == 'f'.code && sig[1].toInt() == 'L'.code && sig[2].toInt() == 'a'.code && sig[3].toInt() == 'C'.code)) return null

        var isLast = false
        while (!isLast) {
            val h = input.read()
            if (h < 0) return null
            isLast = (h and 0x80) != 0
            val type = h and 0x7F
            val len = readU24be(input)
            if (len <= 0) {
                if (isLast) break
                continue
            }
            if (type != 4) {
                input.skip(len.toLong())
                continue
            }

            val payload = ByteArray(len)
            if (input.read(payload) != len) return null
            return extractLyricsFromVorbisComment(payload)
        }
        return null
    }

    private fun extractLyricsFromVorbisComment(payload: ByteArray): String? {
        var off = 0
        fun readI32le(): Int {
            if (off + 4 > payload.size) return -1
            val b0 = payload[off].toInt() and 0xFF
            val b1 = payload[off + 1].toInt() and 0xFF
            val b2 = payload[off + 2].toInt() and 0xFF
            val b3 = payload[off + 3].toInt() and 0xFF
            off += 4
            return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
        }

        val vendorLen = readI32le()
        if (vendorLen < 0 || off + vendorLen > payload.size) return null
        off += vendorLen
        val count = readI32le()
        if (count < 0) return null

        val keys = setOf("LYRICS", "UNSYNCEDLYRICS", "LYRIC", "UNSYNCED LYRICS")
        repeat(count) {
            val clen = readI32le()
            if (clen < 0 || off + clen > payload.size) return@repeat
            val s = runCatching { String(payload, off, clen, Charsets.UTF_8) }.getOrNull().orEmpty()
            off += clen
            val idx = s.indexOf('=')
            if (idx <= 0) return@repeat
            val k = s.substring(0, idx).trim().uppercase()
            if (k !in keys) return@repeat
            val v = s.substring(idx + 1).trim()
            if (v.isNotBlank()) return v
        }
        return null
    }

    private fun parseMp4Lyrics(input: InputStream): String? {
        val c = CountingInputStream(input)
        return parseMp4Boxes(c, Long.MAX_VALUE, 0)
    }

    private fun parseMp4Boxes(input: CountingInputStream, endPos: Long, stage: Int): String? {
        val path = arrayOf("moov", "udta", "meta", "ilst", "©lyr")
        while (input.position < endPos) {
            val start = input.position
            val size32 = readU32be(input)
            if (size32 == null) return null
            val typeBytes = ByteArray(4)
            if (input.read(typeBytes) != 4) return null
            val type = String(typeBytes, Charsets.ISO_8859_1)
            val boxSize = when (size32) {
                0L -> (endPos - start)
                1L -> readU64be(input) ?: return null
                else -> size32
            }
            val headerSize = if (size32 == 1L) 16L else 8L
            val contentSize = (boxSize - headerSize).coerceAtLeast(0L)
            val contentEnd = (input.position + contentSize)

            val hit = stage < path.size && type == path[stage]
            if (!hit) {
                input.skipFully(contentSize)
                continue
            }

            if (stage == path.lastIndex) {
                val text = parseMp4IlstItemForText(input, contentEnd)
                if (!text.isNullOrBlank()) return text
                input.skipFully((contentEnd - input.position).coerceAtLeast(0L))
                continue
            }

            if (type == "meta") {
                input.skipFully(4L)
                val out = parseMp4Boxes(input, contentEnd, stage + 1)
                if (!out.isNullOrBlank()) return out
                input.skipFully((contentEnd - input.position).coerceAtLeast(0L))
                continue
            }

            val out = parseMp4Boxes(input, contentEnd, stage + 1)
            if (!out.isNullOrBlank()) return out
            input.skipFully((contentEnd - input.position).coerceAtLeast(0L))
        }
        return null
    }

    private fun parseMp4IlstItemForText(input: CountingInputStream, endPos: Long): String? {
        while (input.position < endPos) {
            val start = input.position
            val size32 = readU32be(input) ?: return null
            val typeBytes = ByteArray(4)
            if (input.read(typeBytes) != 4) return null
            val type = String(typeBytes, Charsets.ISO_8859_1)
            val boxSize = when (size32) {
                0L -> (endPos - start)
                1L -> readU64be(input) ?: return null
                else -> size32
            }
            val headerSize = if (size32 == 1L) 16L else 8L
            val contentSize = (boxSize - headerSize).coerceAtLeast(0L)
            val contentEnd = (input.position + contentSize)
            if (type != "data") {
                input.skipFully(contentSize)
                continue
            }

            input.skipFully(8L)
            val remaining = (contentEnd - input.position).coerceAtLeast(0L)
            if (remaining <= 0L) return null
            val buf = ByteArray(remaining.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            val r = input.read(buf)
            if (r <= 0) return null
            val raw = buf.copyOf(r)
            val s1 = runCatching { String(raw, Charsets.UTF_8) }.getOrNull()?.trim().orEmpty()
            if (s1.isNotBlank()) return s1
            val s2 = runCatching { String(raw, Charsets.UTF_16) }.getOrNull()?.trim().orEmpty()
            return s2.takeIf { it.isNotBlank() }
        }
        return null
    }

    private fun readU24be(input: InputStream): Int {
        val b1 = input.read()
        val b2 = input.read()
        val b3 = input.read()
        if (b1 < 0 || b2 < 0 || b3 < 0) return -1
        return (b1 shl 16) or (b2 shl 8) or b3
    }

    private fun readU32be(input: InputStream): Long? {
        val b1 = input.read()
        val b2 = input.read()
        val b3 = input.read()
        val b4 = input.read()
        if (b1 < 0 || b2 < 0 || b3 < 0 || b4 < 0) return null
        return ((b1.toLong() shl 24) or (b2.toLong() shl 16) or (b3.toLong() shl 8) or b4.toLong()) and 0xFFFFFFFFL
    }

    private fun readU64be(input: InputStream): Long? {
        var out = 0L
        repeat(8) {
            val b = input.read()
            if (b < 0) return null
            out = (out shl 8) or (b.toLong() and 0xFF)
        }
        return out
    }

    private fun sha256Hex(s: String): String {
        val dig = MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))
        return buildString(dig.size * 2) {
            dig.forEach { b ->
                append(((b.toInt() shr 4) and 0xF).toString(16))
                append((b.toInt() and 0xF).toString(16))
            }
        }
    }

    private class CountingInputStream(private val base: InputStream) : InputStream() {
        var position: Long = 0L
            private set

        override fun read(): Int {
            val r = base.read()
            if (r >= 0) position += 1
            return r
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val r = base.read(b, off, len)
            if (r > 0) position += r.toLong()
            return r
        }

        override fun skip(n: Long): Long {
            val r = base.skip(n)
            if (r > 0) position += r
            return r
        }

        override fun available(): Int = base.available()

        override fun close() = base.close()

        override fun mark(readlimit: Int) = base.mark(readlimit)

        override fun reset() = base.reset()

        override fun markSupported(): Boolean = base.markSupported()

        fun skipFully(n: Long) {
            var remaining = n
            while (remaining > 0L) {
                val skipped = skip(remaining)
                if (skipped <= 0L) {
                    val r = read()
                    if (r < 0) break
                    remaining -= 1L
                } else {
                    remaining -= skipped
                }
            }
        }
    }

    private fun decodeUsltPayload(payload: ByteArray): String? {
        if (payload.isEmpty()) return null
        val enc = payload[0].toInt() and 0xFF
        val charset = when (enc) {
            0 -> Charset.forName("ISO-8859-1")
            1 -> Charset.forName("UTF-16")
            2 -> Charset.forName("UTF-16BE")
            3 -> Charsets.UTF_8
            else -> Charsets.UTF_8
        }
        var idx = 1 + 3
        if (idx > payload.size) return null
        val descEnd = when (enc) {
            1, 2 -> findDoubleZero(payload, idx)
            else -> findSingleZero(payload, idx)
        }
        val textStart = if (descEnd < 0) idx else (if (enc == 1 || enc == 2) descEnd + 2 else descEnd + 1)
        if (textStart >= payload.size) return null
        return try {
            String(payload, textStart, payload.size - textStart, charset).trim()
        } catch (_: Exception) {
            null
        }
    }

    private fun findSingleZero(arr: ByteArray, start: Int): Int {
        var i = start
        while (i < arr.size) {
            if (arr[i].toInt() == 0) return i
            i++
        }
        return -1
    }

    private fun findDoubleZero(arr: ByteArray, start: Int): Int {
        var i = start
        while (i + 1 < arr.size) {
            if (arr[i].toInt() == 0 && arr[i + 1].toInt() == 0) return i
            i += 2
        }
        return -1
    }

    private fun synchsafeToInt(arr: ByteArray, off: Int): Int {
        val b1 = arr[off].toInt() and 0x7F
        val b2 = arr[off + 1].toInt() and 0x7F
        val b3 = arr[off + 2].toInt() and 0x7F
        val b4 = arr[off + 3].toInt() and 0x7F
        return (b1 shl 21) or (b2 shl 14) or (b3 shl 7) or b4
    }

    private fun normalInt(arr: ByteArray, off: Int): Int {
        val b1 = arr[off].toInt() and 0xFF
        val b2 = arr[off + 1].toInt() and 0xFF
        val b3 = arr[off + 2].toInt() and 0xFF
        val b4 = arr[off + 3].toInt() and 0xFF
        return (b1 shl 24) or (b2 shl 16) or (b3 shl 8) or b4
    }
}
