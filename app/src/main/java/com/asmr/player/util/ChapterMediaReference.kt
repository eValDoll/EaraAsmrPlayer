package com.asmr.player.util

import java.net.URLDecoder
import java.net.URLEncoder

fun isJapaneseAsmrResource(path: String, group: String = ""): Boolean =
    path.contains("#eara-chapter=") || group == "japaneseasmr.com" || group.startsWith("japaneseasmr.com/") ||
        path.replace('\\', '/').contains("/japaneseasmr.com/")

/** Keeps chapter identity and download metadata across library and queue persistence. */
data class ChapterMediaReference(
    val streamUrl: String,
    val startMs: Long,
    val endMs: Long?,
    val downloadUrl: String?,
    val fileName: String
) {
    fun encode(): String = buildString {
        append(streamUrl.substringBefore('#'))
        append("#eara-chapter=").append(startMs).append(',').append(endMs ?: "")
        append("&file=").append(URLEncoder.encode(fileName, "UTF-8"))
        downloadUrl?.let { append("&download=").append(URLEncoder.encode(it, "UTF-8")) }
    }

    companion object {
        fun parse(value: String): ChapterMediaReference? {
            val fragment = value.substringAfter('#', "")
            if (!fragment.startsWith("eara-chapter=")) return null
            return runCatching {
                val fields = fragment.split('&').associate { it.substringBefore('=') to it.substringAfter('=', "") }
                val range = fields.getValue("eara-chapter").split(',')
                val start = range[0].toLong()
                val end = range.getOrNull(1)?.takeIf { it.isNotBlank() }?.toLong()
                require(start >= 0 && (end == null || end > start))
                val stream = value.substringBefore('#')
                require(stream.startsWith("https://"))
                ChapterMediaReference(stream, start, end,
                    fields["download"]?.let { URLDecoder.decode(it, "UTF-8") },
                    fields["file"]?.let { URLDecoder.decode(it, "UTF-8") }.orEmpty())
            }.getOrNull()
        }
    }
}
