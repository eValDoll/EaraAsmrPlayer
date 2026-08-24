package com.asmr.player.util

private val SYSTEM_STORAGE_DIRECTORY_NAMES = setOf(
    "\$recycle.bin",
    "lost.dir",
    "system volume information",
)

internal fun isScannableLocalDirectoryName(name: String): Boolean {
    val normalized = name.trim()
    if (normalized.isEmpty() || normalized.startsWith('.')) return false
    return normalized.lowercase() !in SYSTEM_STORAGE_DIRECTORY_NAMES
}

internal fun isScannableLocalStorageEntry(relativePath: String, isDirectory: Boolean): Boolean {
    val segments = relativePath.replace('\\', '/').split('/').filter(String::isNotBlank)
    val directorySegments = if (isDirectory) segments else segments.dropLast(1)
    return directorySegments.all(::isScannableLocalDirectoryName)
}
