package com.asmr.player.util

object DlsiteWorkNo {
    private val workNoRegex = Regex(
        """(?<![A-Z0-9])(RJ|BJ|VJ)[ \t]*(\d+)(?![A-Z0-9])""",
        RegexOption.IGNORE_CASE
    )
    private val exactWorkNoRegex = Regex(
        """(RJ|BJ|VJ)[ \t]*(\d+)""",
        RegexOption.IGNORE_CASE
    )

    fun extractWorkNo(input: String, minimumDigits: Int = 1): String {
        val safeMinimumDigits = minimumDigits.coerceAtLeast(1)
        val match = workNoRegex.findAll(input)
            .firstOrNull { it.groupValues[2].length >= safeMinimumDigits }
            ?: return ""
        val digits = match.groupValues[2]
        return match.groupValues[1].uppercase() + digits
    }

    fun normalizeWorkNo(input: String, minimumDigits: Int = 1): String {
        val match = exactWorkNoRegex.matchEntire(input.trim()) ?: return ""
        val digits = match.groupValues[2]
        if (digits.length < minimumDigits.coerceAtLeast(1)) return ""
        return match.groupValues[1].uppercase() + digits
    }

    fun normalizeCandidates(values: List<String>): List<String> {
        return values.asSequence()
            .map { normalizeWorkNo(it) }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
    }
}
