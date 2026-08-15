package com.asmr.player.ui.common

import com.asmr.player.cache.CacheImageModel
import com.asmr.player.domain.model.Album
import com.asmr.player.util.DlsiteAntiHotlink
import java.util.LinkedHashMap
import kotlin.math.absoluteValue

private const val MaxRememberedAlbumCoverModels = 512
private val albumCoverModelCacheLock = Any()
private val albumCoverModelCache = object : LinkedHashMap<String, Any>(128, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Any>?): Boolean {
        return size > MaxRememberedAlbumCoverModels
    }
}

fun albumStableKey(album: Album): String {
    album.asmrOneWorkId?.takeIf { it > 0 }?.let { return "asmr-one:$it" }
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
    synchronized(albumCoverModelCacheLock) {
        albumCoverModelCache[data]?.let { return it }
    }
    val headers = if (data.startsWith("http", ignoreCase = true)) {
        DlsiteAntiHotlink.headersForImageUrl(data)
    } else {
        emptyMap()
    }
    val model: Any = if (headers.isEmpty()) {
        data
    } else {
        CacheImageModel(data = data, headers = headers, keyTag = "dlsite")
    }
    return synchronized(albumCoverModelCacheLock) {
        albumCoverModelCache[data] ?: model.also { albumCoverModelCache[data] = it }
    }
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
