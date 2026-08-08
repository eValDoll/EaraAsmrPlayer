package com.asmr.player.subtitle

internal data class GeneratedSubtitle(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

internal object SubtitleSegmentNormalizer {
    fun normalize(
        segments: List<GeneratedSubtitle>,
        totalDurationMs: Long
    ): List<GeneratedSubtitle> {
        val durationLimit = totalDurationMs.takeIf { it > 0L } ?: Long.MAX_VALUE
        return segments.asSequence()
            .mapNotNull { segment ->
                val text = segment.text.trim()
                if (text.isBlank()) return@mapNotNull null
                val start = segment.startMs.coerceAtLeast(0L).coerceAtMost(durationLimit)
                val end = segment.endMs
                    .coerceAtLeast(start + MIN_SUBTITLE_DURATION_MS)
                    .coerceAtMost(durationLimit)
                if (end <= start) null else GeneratedSubtitle(start, end, text)
            }
            .sortedWith(compareBy<GeneratedSubtitle> { it.startMs }.thenBy { it.endMs })
            .distinctBy { Triple(it.startMs, it.endMs, it.text) }
            .toList()
    }

    private const val MIN_SUBTITLE_DURATION_MS = 10L
}
