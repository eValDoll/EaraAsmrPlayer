package com.asmr.player.util

fun isOnlineTrackPath(path: String): Boolean {
    val v = path.trim()
    return v.startsWith("http://", ignoreCase = true) || v.startsWith("https://", ignoreCase = true)
}

fun isVirtualAlbumPath(path: String): Boolean {
    return path.trim().startsWith("web://", ignoreCase = true)
}

