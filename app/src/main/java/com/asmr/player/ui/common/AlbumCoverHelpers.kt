package com.asmr.player.ui.common

import com.asmr.player.cache.CacheImageModel
import com.asmr.player.domain.model.Album
import com.asmr.player.util.DlsiteAntiHotlink
import kotlin.math.absoluteValue

fun albumStableKey(album: Album): String {
    val id = album.rjCode.ifBlank { album.workId }.trim()
    if (id.isNotEmpty()) return id
    val seed = "${album.coverUrl}|${album.title}|${album.circle}|${album.cv}"
    return "h${seed.hashCode().absoluteValue}"
}

fun shouldFadeInCover(isScrollInProgress: Boolean, baseEnabled: Boolean = true): Boolean {
    return baseEnabled && !isScrollInProgress
}

fun albumCoverImageModel(album: Album): Any? {
    return albumCoverImageModel(
        coverThumbPath = album.coverThumbPath,
        coverPath = album.coverPath,
        coverUrl = album.coverUrl
    )
}

fun albumCoverImageModel(
    coverThumbPath: String,
    coverPath: String,
    coverUrl: String
): Any? {
    val data = albumCoverData(
        coverThumbPath = coverThumbPath,
        coverPath = coverPath,
        coverUrl = coverUrl
    ) ?: return null
    val headers = if (data.startsWith("http", ignoreCase = true)) {
        DlsiteAntiHotlink.headersForImageUrl(data)
    } else {
        emptyMap()
    }
    return if (headers.isEmpty()) data else CacheImageModel(data = data, headers = headers, keyTag = "dlsite")
}

private fun albumCoverData(
    coverThumbPath: String,
    coverPath: String,
    coverUrl: String
): String? {
    return coverThumbPath.takeIf { it.isNotBlank() && it.contains("_v2") }
        ?: coverPath.takeIf { it.isNotBlank() }
        ?: coverUrl.takeIf { it.isNotBlank() }
}
