package com.asmr.player.util

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object JapaneseAsmrAntiHotlink {
    const val REFERER = "https://japaneseasmr.com/"

    fun headersForImageUrl(url: String): Map<String, String> =
        headersForImageHost(url.toHttpUrlOrNull()?.host.orEmpty())

    fun headersForImageHost(host: String): Map<String, String> {
        val normalizedHost = host.lowercase()
        return if (normalizedHost == "pic.weeabo0.xyz" || normalizedHost == "japaneseasmr.com") {
            mapOf("Referer" to REFERER)
        } else {
            emptyMap()
        }
    }
}
