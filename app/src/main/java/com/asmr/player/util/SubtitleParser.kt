package com.asmr.player.util

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.nio.charset.Charset

data class SubtitleEntry(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

object SubtitleParser {

    fun parse(path: String): List<SubtitleEntry> {
        val file = File(path)
        if (!file.exists()) return emptyList()

        val content = try {
            file.readText(Charsets.UTF_8)
        } catch (e: Exception) {
            try {
                file.readText(Charset.forName("GBK"))
            } catch (e2: Exception) {
                return emptyList()
            }
        }.removePrefix("\uFEFF")

        val extension = file.extension.lowercase()
        return parseText(extension, content)
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

        val element = runCatching { JsonParser().parse(trimmed) }.getOrNull() ?: return content
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
        val tsRegex = Regex("""\[(\d{1,2}):(\d{1,2}(?:\.\d{1,3})?)\]""")

        var offsetMs = 0L
        val raw = mutableListOf<Pair<Long, String>>()

        content.lines().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isBlank()) return@forEach
            val offsetMatch = offsetRegex.matchEntire(line)
            if (offsetMatch != null) {
                offsetMs = offsetMatch.groupValues[1].toLongOrNull() ?: 0L
                return@forEach
            }
            if (line.startsWith("[ti:", true) ||
                line.startsWith("[ar:", true) ||
                line.startsWith("[al:", true) ||
                line.startsWith("[by:", true) ||
                line.startsWith("[re:", true) ||
                line.startsWith("[ve:", true)
            ) {
                return@forEach
            }

            val ts = tsRegex.findAll(line).toList()
            if (ts.isEmpty()) return@forEach
            val text = tsRegex.replace(line, "").trim()
            if (text.isBlank()) return@forEach

            ts.forEach timestamps@{ m ->
                val minutes = m.groupValues[1].toLongOrNull() ?: return@timestamps
                val seconds = m.groupValues[2].toDoubleOrNull() ?: return@timestamps
                val ms = ((minutes * 60.0 + seconds) * 1000.0).toLong() + offsetMs
                raw.add(ms to text)
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

    fun mergeLyrics(l1: List<SubtitleEntry>, l2: List<SubtitleEntry>): List<SubtitleEntry> {
        if (l1.isEmpty()) return l2
        if (l2.isEmpty()) return l1

        val merged = mutableListOf<SubtitleEntry>()
        val sortedL1 = l1.sortedBy { it.startMs }
        val sortedL2 = l2.sortedBy { it.startMs }

        var i = 0
        var j = 0

        fun normalizeTextForCompare(text: String): String {
            return text
                .replace('\uFEFF', ' ')
                .replace(Regex("""\s+"""), " ")
                .trim()
        }

        while (i < sortedL1.size && j < sortedL2.size) {
            val s1 = sortedL1[i]
            val s2 = sortedL2[j]

            val overlap = getOverlap(s1.startMs, s1.endMs, s2.startMs, s2.endMs)

            if (overlap > 0) {
                val dur1 = s1.endMs - s1.startMs
                val dur2 = s2.endMs - s2.startMs

                if (dur1 >= dur2) {
                    val baseS = s1.startMs
                    val baseE = s1.endMs
                    val txtBase = s1.text
                    val txtSecList = mutableListOf(s2.text)
                    i++
                    j++
                    while (j < sortedL2.size) {
                        val ns2 = sortedL2[j]
                        if (getOverlap(baseS, baseE, ns2.startMs, ns2.endMs) > 0 && (baseE - baseS) >= (ns2.endMs - ns2.startMs)) {
                            txtSecList.add(ns2.text)
                            j++
                        } else break
                    }
                    val baseNorm = normalizeTextForCompare(txtBase)
                    val secCleaned = txtSecList
                        .map { it to normalizeTextForCompare(it) }
                        .filter { (_, n) -> n.isNotBlank() && n != baseNorm }
                        .distinctBy { (_, n) -> n }
                        .map { (raw, _) -> raw.trim() }
                    val mergedText = if (secCleaned.isEmpty()) txtBase else "$txtBase\n${secCleaned.joinToString(" ")}"
                    merged.add(SubtitleEntry(baseS, baseE, mergedText))
                } else {
                    val baseS = s2.startMs
                    val baseE = s2.endMs
                    val txtBase = s2.text
                    val txtSecList = mutableListOf(s1.text)
                    j++
                    i++
                    while (i < sortedL1.size) {
                        val ns1 = sortedL1[i]
                        if (getOverlap(ns1.startMs, ns1.endMs, baseS, baseE) > 0 && (baseE - baseS) >= (ns1.endMs - ns1.startMs)) {
                            txtSecList.add(ns1.text)
                            i++
                        } else break
                    }
                    val baseNorm = normalizeTextForCompare(txtBase)
                    val secCleaned = txtSecList
                        .map { it to normalizeTextForCompare(it) }
                        .filter { (_, n) -> n.isNotBlank() && n != baseNorm }
                        .distinctBy { (_, n) -> n }
                        .map { (raw, _) -> raw.trim() }
                    val mergedText = if (secCleaned.isEmpty()) txtBase else "${secCleaned.joinToString(" ")}\n$txtBase"
                    merged.add(SubtitleEntry(baseS, baseE, mergedText))
                }
            } else if (s1.endMs < s2.startMs) {
                merged.add(s1)
                i++
            } else {
                merged.add(s2)
                j++
            }
        }
        while (i < sortedL1.size) merged.add(sortedL1[i++])
        while (j < sortedL2.size) merged.add(sortedL2[j++])

        return merged
    }

    private fun getOverlap(s1: Long, e1: Long, s2: Long, e2: Long): Long {
        return Math.max(0L, Math.min(e1, e2) - Math.max(s1, s2))
    }
}
