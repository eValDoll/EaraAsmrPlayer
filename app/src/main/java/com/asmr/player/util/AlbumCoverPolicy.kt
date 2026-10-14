package com.asmr.player.util

fun isLikelyPlaceholderCover(url: String): Boolean {
    val s = url.trim().lowercase()
    if (!s.startsWith("http")) return true
    return s.contains("noimage") ||
        s.contains("no_image") ||
        s.contains("no-image") ||
        s.contains("placeholder") ||
        s.endsWith("/0.jpg") ||
        s.endsWith("/0.png")
}
