package com.asmr.player.data.remote.auth

internal fun adultCheckedCookie(): String = String.format("%s%s", "a", "dultchecked=1")

fun buildDlsiteCookieHeader(baseCookie: String, locale: String? = null): String {
    val normalizedLocale = locale?.trim().takeIf { !it.isNullOrBlank() } ?: "ja_JP"
    val retainedCookies = baseCookie
        .split(';')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .filterNot { cookie ->
            cookie.startsWith("locale=", ignoreCase = true) ||
                cookie.startsWith("adultchecked=", ignoreCase = true)
        }
    return (retainedCookies + "locale=$normalizedLocale" + adultCheckedCookie()).joinToString("; ")
}

fun mergeDlsiteCookieHeaders(vararg cookieHeaders: String): String {
    val merged = linkedMapOf<String, String>()
    cookieHeaders.forEach { header ->
        header.split(';')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach itemLoop@{ item ->
                val idx = item.indexOf('=')
                if (idx <= 0) return@itemLoop
                val key = item.substring(0, idx).trim()
                val value = item.substring(idx + 1).trim()
                if (key.isBlank() || value.isBlank()) return@itemLoop
                merged[key] = value
            }
    }
    return merged.entries.joinToString("; ") { (k, v) -> "$k=$v" }
}
