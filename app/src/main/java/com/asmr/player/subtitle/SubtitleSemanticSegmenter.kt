package com.asmr.player.subtitle

internal object SubtitleSemanticSegmenter {
    fun reflow(segments: List<GeneratedSubtitle>): List<GeneratedSubtitle> {
        val units = attachStandalonePunctuation(
            segments.asSequence()
                .mapNotNull { segment ->
                    val text = segment.text.trim()
                    if (text.isBlank()) return@mapNotNull null
                    GeneratedSubtitle(
                        startMs = segment.startMs,
                        endMs = segment.endMs,
                        text = text
                    ).takeIf { it.endMs > it.startMs }
                }
                .sortedWith(compareBy<GeneratedSubtitle> { it.startMs }.thenBy { it.endMs })
                .toList()
        )
        if (units.isEmpty()) return emptyList()

        val grouped = mutableListOf<GeneratedSubtitle>()
        var current: GeneratedSubtitle? = null
        units.forEach { unit ->
            val active = current
            if (active == null) {
                current = unit
                return@forEach
            }

            val merged = active.mergeWith(unit)
            val activeReady = active.text.hasSentenceEnding() &&
                (active.durationMs >= MIN_READABLE_DURATION_MS || active.meaningfulLength >= MIN_READABLE_TEXT_LENGTH)
            val mergedTooLong = merged.durationMs > MAX_SUBTITLE_DURATION_MS ||
                merged.meaningfulLength > MAX_SUBTITLE_TEXT_LENGTH

            if (activeReady || (mergedTooLong && active.isReadableStandalone)) {
                grouped += active
                current = unit
            } else {
                current = merged
            }
        }
        current?.let { grouped += it }

        return grouped.flatMap { segment -> splitIfTooLong(segment) }
            .mergeTrailingFragments()
            .mapNotNull { segment ->
                val text = segment.text.removeDisallowedTrailingPunctuation()
                if (text.isBlank()) null else segment.copy(text = text)
            }
    }

    private fun attachStandalonePunctuation(
        segments: List<GeneratedSubtitle>
    ): List<GeneratedSubtitle> {
        val result = mutableListOf<GeneratedSubtitle>()
        segments.forEach { segment ->
            if (segment.text.isStandalonePunctuation()) {
                val previous = result.lastOrNull()
                if (previous != null) {
                    result[result.lastIndex] = previous.mergeWith(segment)
                } else {
                    result += segment
                }
            } else {
                val previous = result.lastOrNull()
                if (previous != null && previous.text.isStandalonePunctuation()) {
                    result[result.lastIndex] = previous.mergeWith(segment)
                } else {
                    result += segment
                }
            }
        }
        return result
    }

    private fun splitIfTooLong(segment: GeneratedSubtitle): List<GeneratedSubtitle> {
        if (segment.durationMs <= MAX_SUBTITLE_DURATION_MS &&
            segment.meaningfulLength <= MAX_SUBTITLE_TEXT_LENGTH
        ) {
            return listOf(segment)
        }

        val parts = splitTextBySemanticBreaks(segment.text)
        if (parts.size <= 1) return listOf(segment)

        val totalWeight = parts.sumOf { it.meaningfulTextLength().coerceAtLeast(1) }
        var cursor = segment.startMs
        return parts.mapIndexed { index, part ->
            val end = if (index == parts.lastIndex) {
                segment.endMs
            } else {
                val weight = part.meaningfulTextLength().coerceAtLeast(1)
                (cursor + segment.durationMs * weight / totalWeight)
                    .coerceIn(cursor + MIN_SUBTITLE_DURATION_MS, segment.endMs)
            }
            GeneratedSubtitle(
                startMs = cursor,
                endMs = end,
                text = part
            ).also {
                cursor = end
            }
        }.filter { it.endMs > it.startMs && it.text.isNotBlank() }
    }

    private fun splitTextBySemanticBreaks(text: String): List<String> {
        val chunks = mutableListOf<String>()
        var start = 0
        text.forEachIndexed { index, char ->
            if (char.isSentenceEnding() || char in SOFT_BREAK_CHARS) {
                val next = index + 1
                if (next > start) {
                    chunks += text.substring(start, next).trim()
                    start = next
                }
            }
        }
        if (start < text.length) chunks += text.substring(start).trim()

        return chunks.filter { it.isNotBlank() }
            .fold(mutableListOf<String>()) { acc, chunk ->
                val previous = acc.lastOrNull()
                if (previous == null || previous.meaningfulTextLength() >= MIN_READABLE_TEXT_LENGTH) {
                    acc += chunk
                } else {
                    acc[acc.lastIndex] = previous.joinSubtitleText(chunk)
                }
                acc
            }
    }

    private fun List<GeneratedSubtitle>.mergeTrailingFragments(): List<GeneratedSubtitle> {
        if (isEmpty()) return emptyList()
        val result = mutableListOf<GeneratedSubtitle>()
        forEach { segment ->
            val previous = result.lastOrNull()
            if (previous != null && !segment.isReadableStandalone) {
                result[result.lastIndex] = previous.mergeWith(segment)
            } else {
                result += segment
            }
        }
        if (result.size >= 2 && !result.first().isReadableStandalone) {
            val first = result.removeAt(0)
            result[0] = first.mergeWith(result[0])
        }
        return result
    }

    private fun GeneratedSubtitle.mergeWith(other: GeneratedSubtitle): GeneratedSubtitle =
        GeneratedSubtitle(
            startMs = minOf(startMs, other.startMs),
            endMs = maxOf(endMs, other.endMs),
            text = text.joinSubtitleText(other.text)
        )

    private val GeneratedSubtitle.durationMs: Long
        get() = endMs - startMs

    private val GeneratedSubtitle.meaningfulLength: Int
        get() = text.meaningfulTextLength()

    private val GeneratedSubtitle.isReadableStandalone: Boolean
        get() = durationMs >= MIN_READABLE_DURATION_MS &&
            meaningfulLength >= MIN_READABLE_TEXT_LENGTH &&
            !text.isStandalonePunctuation()

    private fun String.hasSentenceEnding(): Boolean =
        lastOrNull { !it.isWhitespace() }?.isSentenceEnding() == true

    private fun String.isStandalonePunctuation(): Boolean =
        isNotBlank() && all { it.isWhitespace() || it in PUNCTUATION_CHARS }

    private fun Char.isSentenceEnding(): Boolean = this in SENTENCE_ENDING_CHARS

    private fun String.meaningfulTextLength(): Int =
        count { !it.isWhitespace() && it !in PUNCTUATION_CHARS }

    private fun String.joinSubtitleText(other: String): String {
        val left = trim()
        val right = other.trim()
        if (left.isEmpty()) return right
        if (right.isEmpty()) return left
        return if (left.last().isAsciiWord() && right.first().isAsciiWord()) {
            "$left $right"
        } else {
            left + right
        }
    }

    private fun String.removeDisallowedTrailingPunctuation(): String =
        trimEnd()
            .dropLastWhile { it in PUNCTUATION_CHARS && it !in ALLOWED_TRAILING_PUNCTUATION_CHARS }
            .trimEnd()

    private fun Char.isAsciiWord(): Boolean =
        this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9'

    private const val MIN_READABLE_DURATION_MS = 800L
    private const val MIN_SUBTITLE_DURATION_MS = 10L
    private const val MAX_SUBTITLE_DURATION_MS = 8_000L
    private const val MIN_READABLE_TEXT_LENGTH = 4
    private const val MAX_SUBTITLE_TEXT_LENGTH = 36

    private val SENTENCE_ENDING_CHARS = setOf('。', '！', '？', '!', '?', '…')
    private val SOFT_BREAK_CHARS = setOf('、', '，', ',', ';', '；')
    private val ALLOWED_TRAILING_PUNCTUATION_CHARS = setOf('！', '？', '!', '?')
    private val PUNCTUATION_CHARS = SENTENCE_ENDING_CHARS + SOFT_BREAK_CHARS +
        setOf('.', '．', '「', '」', '『', '』', '（', '）', '(', ')', '[', ']', '【', '】', '〜', '~', '♪')
}
