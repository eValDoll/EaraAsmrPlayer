package com.asmr.player.data.remote.auth

fun buildDlsiteCookieHeader(baseCookie: String): String {
    val base = baseCookie.trim().trimEnd(';')
    val extras = listOf("locale=ja_JP", "adultchecked=1")
    return buildString {
        if (base.isNotBlank()) append(base)
        extras.forEach { kv ->
            if (contains(kv)) return@forEach
            if (isNotEmpty()) append("; ")
            append(kv)
        }
    }
}
