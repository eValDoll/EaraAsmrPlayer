package com.asmr.player.data.remote.auth

internal fun adultCheckedCookie(): String = String.format("%s%s", "a", "dultchecked=1")

fun buildDlsiteCookieHeader(baseCookie: String): String {
    val base = baseCookie.trim().trimEnd(';')
    val extras = listOf("locale=ja_JP", adultCheckedCookie())
    return buildString {
        if (base.isNotBlank()) append(base)
        extras.forEach { kv ->
            if (contains(kv)) return@forEach
            if (isNotEmpty()) append("; ")
            append(kv)
        }
    }
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
