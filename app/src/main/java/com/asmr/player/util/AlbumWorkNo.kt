package com.asmr.player.util

/** Album identities include Japanese ASMR numeric works; DLsite requests use DlsiteWorkNo. */
object AlbumWorkNo {
    private val numericWorkNo = Regex("""UN\d{6,}""", RegexOption.IGNORE_CASE)
    private val embeddedNumericWorkNo = Regex("""(?<![A-Z0-9])UN\d{6,}(?![A-Z0-9])""", RegexOption.IGNORE_CASE)

    fun isNumericWork(input: String): Boolean = numericWorkNo.matches(input.trim())

    fun normalizeWorkNo(input: String, minimumDigits: Int = 1): String =
        DlsiteWorkNo.normalizeWorkNo(input, minimumDigits).ifBlank {
            input.trim().takeIf { isNumericWork(it) && it.length - 2 >= minimumDigits }
                ?.uppercase().orEmpty()
        }

    fun extractWorkNo(input: String): String =
        DlsiteWorkNo.extractWorkNo(input).ifBlank {
            embeddedNumericWorkNo.find(input)?.value?.uppercase().orEmpty()
        }
}
