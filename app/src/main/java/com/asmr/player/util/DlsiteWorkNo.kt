package com.asmr.player.util

object DlsiteWorkNo {
    private val rjRegex = Regex("""RJ\d+""", RegexOption.IGNORE_CASE)

    fun extractRjCode(input: String): String {
        return rjRegex.find(input)?.value?.uppercase() ?: ""
    }

    fun normalizeCandidates(values: List<String>): List<String> {
        return values.asSequence()
            .map { it.trim().uppercase() }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
    }
}
