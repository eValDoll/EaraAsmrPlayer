package com.asmr.player.util

internal fun encodeRemoteSubtitleSources(sources: List<RemoteSubtitleSource>): String? {
    val normalized = sources.mapNotNull { source ->
        val url = source.url.trim()
        if (url.isBlank()) return@mapNotNull null
        val language = source.language.trim().ifBlank { "default" }
        val ext = source.ext.trim().ifBlank { url.substringAfterLast('.', "vtt") }
        listOf(url, language, ext).joinToString("\t")
    }
    return normalized.takeIf { it.isNotEmpty() }?.joinToString("\n")
}

internal fun decodeRemoteSubtitleSources(raw: String?): List<RemoteSubtitleSource> {
    if (raw.isNullOrBlank()) return emptyList()
    return raw.lineSequence().mapNotNull { line ->
        val parts = line.split('\t')
        val url = parts.firstOrNull().orEmpty().trim()
        if (url.isBlank()) return@mapNotNull null
        RemoteSubtitleSource(
            url = url,
            language = parts.getOrNull(1).orEmpty().trim().ifBlank { "default" },
            ext = parts.getOrNull(2).orEmpty().trim().ifBlank { url.substringAfterLast('.', "vtt") }
        )
    }.toList()
}
