package com.asmr.player.util

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

data class SubtitleEntry(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

object SubtitleParser {

    fun parse(path: String): List<SubtitleEntry> {
        val file = File(path)
        if (!file.exists()) return emptyList()

        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return emptyList()
        return parseBytes(file.extension, bytes)
    }

    internal fun parseBytes(extension: String, bytes: ByteArray): List<SubtitleEntry> {
        return parseText(extension, decodeText(bytes))
    }

    private fun decodeText(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""

        val bomCharset = when {
            bytes.hasPrefix(0xEF, 0xBB, 0xBF) -> Charsets.UTF_8
            bytes.hasPrefix(0xFF, 0xFE) -> Charsets.UTF_16LE
            bytes.hasPrefix(0xFE, 0xFF) -> Charsets.UTF_16BE
            else -> null
        }
        if (bomCharset != null) {
            return decodeStrict(bytes, bomCharset).orEmpty().removePrefix("\uFEFF")
        }

        return decodeStrict(bytes, Charsets.UTF_8)
            ?: decodeStrict(bytes, Charset.forName("GB18030"))
            ?: bytes.toString(Charset.forName("GB18030"))
    }

    fun parseText(extension: String, content: String): List<SubtitleEntry> {
        val normalized = content.removePrefix("\uFEFF")
        return when (extension.trim().trimStart('.').lowercase()) {
            "lrc" -> parseLrc(normalized)
            "vtt" -> parseVtt(maybeConvertDlsiteJsonVttToText(normalized))
            "srt" -> parseSrt(normalized)
            else -> emptyList()
        }
    }

    private fun maybeConvertDlsiteJsonVttToText(content: String): String {
        val trimmed = content.trimStart()
        if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) return content

        val element = runCatching { JsonParser.parseString(trimmed) }.getOrNull() ?: return content
        val items = extractWebvttItems(element) ?: return content

        val lines = mutableListOf("WEBVTT", "")
        var added = 0
        items.forEach { it ->
            val obj = it as? JsonObject ?: return@forEach
            val st = obj.get("start_time")?.asString?.trim().orEmpty()
            val et = obj.get("end_time")?.asString?.trim().orEmpty()
            val subs = obj.get("subtitles")?.takeIf { e -> e.isJsonArray }?.asJsonArray
            if (st.isBlank() || et.isBlank() || subs == null) return@forEach
            val text = jsonArrayToList(subs)
                .mapNotNull { e -> runCatching { e.asString.trim() }.getOrNull() }
                .filter { it.isNotBlank() }
                .joinToString("\n")
            if (text.isBlank()) return@forEach

            lines.add("$st --> $et")
            lines.add(text)
            lines.add("")
            added += 1
        }

        return if (added > 0) lines.joinToString("\n") else content
    }

    private fun extractWebvttItems(element: JsonElement): List<JsonElement>? {
        return when {
            element.isJsonObject -> {
                val obj = element.asJsonObject
                val webvtt = obj.get("webvtt")
                if (webvtt != null && webvtt.isJsonArray) jsonArrayToList(webvtt.asJsonArray) else null
            }
            element.isJsonArray -> jsonArrayToList(element.asJsonArray)
            else -> null
        }
    }

    private fun jsonArrayToList(arr: JsonArray): List<JsonElement> {
        val out = ArrayList<JsonElement>(arr.size())
        arr.forEach { out.add(it) }
        return out
    }

    private fun parseLrc(content: String): List<SubtitleEntry> {
        val offsetRegex = Regex("""^\s*\[offset:([+-]?\d+)\]\s*$""", RegexOption.IGNORE_CASE)
        val timestampRegex = Regex("""\[(?:(\d+):)?(\d+):(\d{1,2})(?:[.,](\d{1,3}))?]""")

        val offsetMs = content.lineSequence()
            .mapNotNull { line ->
                offsetRegex.matchEntire(line.trim())?.groupValues?.get(1)?.toLongOrNull()
            }
            .lastOrNull()
            ?: 0L
        val raw = mutableListOf<Pair<Long, String>>()

        content.lines().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isBlank()) return@forEach

            val timestamps = timestampRegex.findAll(line).toList()
            if (timestamps.isEmpty()) return@forEach
            val text = timestampRegex.replace(line, "").trim()
            if (text.isBlank()) return@forEach

            timestamps.forEach timestampLoop@{ match ->
                val timestampMs = parseLrcTimestampMs(match) ?: return@timestampLoop
                val adjustedMs = runCatching { Math.addExact(timestampMs, offsetMs) }.getOrNull()
                    ?: return@timestampLoop
                raw.add(adjustedMs to text)
            }
        }

        val sorted = raw
            .distinctBy { it.first to it.second }
            .sortedBy { it.first }
            .filter { it.first >= 0L }
        if (sorted.isEmpty()) return emptyList()

        return sorted.mapIndexed { idx, (start, text) ->
            val end = if (idx < sorted.lastIndex) sorted[idx + 1].first else start + 5000L
            SubtitleEntry(start, end.coerceAtLeast(start + 200L), text)
        }
    }

    private fun parseLrcTimestampMs(match: MatchResult): Long? {
        val rawHours = match.groupValues[1]
        val hours = if (rawHours.isEmpty()) 0L else rawHours.toLongOrNull() ?: return null
        val minutes = match.groupValues[2].toLongOrNull() ?: return null
        val seconds = match.groupValues[3].toLongOrNull() ?: return null
        val fractionMs = match.groupValues[4]
            .takeIf { it.isNotEmpty() }
            ?.padEnd(3, '0')
            ?.take(3)
            ?.toLongOrNull()
            ?: 0L

        return runCatching {
            val hourMs = Math.multiplyExact(hours, 3_600_000L)
            val minuteMs = Math.multiplyExact(minutes, 60_000L)
            val secondMs = Math.multiplyExact(seconds, 1_000L)
            Math.addExact(Math.addExact(hourMs, minuteMs), Math.addExact(secondMs, fractionMs))
        }.getOrNull()
    }

    private fun parseVtt(content: String): List<SubtitleEntry> {
        val entries = mutableListOf<SubtitleEntry>()
        val lines = content.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.contains("-->")) {
                val parts = line.split("-->")
                if (parts.size >= 2) {
                    val startMs = parseTimestampMs(parts[0].trim())
                    val endMs = parseTimestampMs(parts[1].trim())
                    
                    val textLines = mutableListOf<String>()
                    i++
                    while (i < lines.size && lines[i].trim().isNotEmpty()) {
                        textLines.add(lines[i].trim())
                        i++
                    }
                    entries.add(SubtitleEntry(startMs, endMs, textLines.joinToString("\n")))
                }
            }
            i++
        }
        return entries
    }

    private fun parseSrt(content: String): List<SubtitleEntry> {
        val entries = mutableListOf<SubtitleEntry>()
        val lines = content.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.contains("-->")) {
                val parts = line.split("-->")
                if (parts.size >= 2) {
                    val startMs = parseTimestampMs(parts[0].trim().replace(",", "."))
                    val endMs = parseTimestampMs(parts[1].trim().replace(",", "."))
                    
                    val textLines = mutableListOf<String>()
                    i++
                    while (i < lines.size && lines[i].trim().isNotEmpty()) {
                        textLines.add(lines[i].trim())
                        i++
                    }
                    entries.add(SubtitleEntry(startMs, endMs, textLines.joinToString("\n")))
                }
            }
            i++
        }
        return entries
    }

    private fun parseTimestampMs(ts: String): Long {
        val parts = ts.split(":")
        var seconds = 0.0
        try {
            when (parts.size) {
                3 -> {
                    seconds += parts[0].toInt() * 3600
                    seconds += parts[1].toInt() * 60
                    seconds += parts[2].toDouble()
                }
                2 -> {
                    seconds += parts[0].toInt() * 60
                    seconds += parts[1].toDouble()
                }
            }
        } catch (e: Exception) {
            return 0
        }
        return (seconds * 1000).toLong()
    }

    private fun decodeStrict(bytes: ByteArray, charset: Charset): String? {
        return runCatching {
            charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        }.getOrNull()
    }

    private fun ByteArray.hasPrefix(vararg prefix: Int): Boolean {
        if (size < prefix.size) return false
        return prefix.indices.all { index -> (this[index].toInt() and 0xFF) == prefix[index] }
    }
}
