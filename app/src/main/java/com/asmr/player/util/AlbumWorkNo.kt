package com.asmr.player.util

/** Album identities include Japanese ASMR site codes; DLsite requests use DlsiteWorkNo. */
object AlbumWorkNo {
    private val siteWorkNo = Regex("""UND?\d{6,}""", RegexOption.IGNORE_CASE)
    private val embeddedSiteWorkNo = Regex("""(?<![A-Z0-9])UND?\d{6,}(?![A-Z0-9])""", RegexOption.IGNORE_CASE)

    fun isJapaneseAsmrOnlyWork(input: String): Boolean = siteWorkNo.matches(input.trim())

    fun normalizeWorkNo(input: String, minimumDigits: Int = 1): String =
        DlsiteWorkNo.normalizeWorkNo(input, minimumDigits).ifBlank {
            input.trim().takeIf { isJapaneseAsmrOnlyWork(it) && it.count(Char::isDigit) >= minimumDigits }
                ?.uppercase().orEmpty()
        }

    fun extractWorkNo(input: String): String =
        DlsiteWorkNo.extractWorkNo(input).ifBlank {
            embeddedSiteWorkNo.find(input)?.value?.uppercase().orEmpty()
        }
}
