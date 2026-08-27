package com.asmr.player.ui.library

import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.data.local.db.entities.TrackEntity
import com.asmr.player.util.isOnlineTrackPath
import com.asmr.player.util.isVirtualAlbumPath
import java.io.FileNotFoundException

internal enum class LocalSourceAvailability {
    Available,
    Missing,
    Unknown,
}

internal fun localAlbumPhysicalSources(
    album: AlbumEntity,
    tracks: List<TrackEntity>,
): List<String> {
    val albumSources = buildList {
        album.localPath?.trim()?.takeIf(String::isNotBlank)?.let(::add)
        album.downloadPath?.trim()?.takeIf(String::isNotBlank)?.let(::add)
        album.path.trim()
            .takeIf(String::isNotBlank)
            ?.takeUnless(::isVirtualAlbumPath)
            ?.takeUnless(::isOnlineTrackPath)
            ?.let(::add)
    }.distinct()
    if (albumSources.isNotEmpty()) return albumSources

    return tracks.asSequence()
        .map { it.path.trim() }
        .filter(String::isNotBlank)
        .filterNot(::isOnlineTrackPath)
        .distinct()
        .toList()
}

internal fun shouldRemoveMissingLocalAlbum(
    album: AlbumEntity,
    tracks: List<TrackEntity>,
    availability: (String) -> LocalSourceAvailability,
): Boolean {
    if (isVirtualAlbumPath(album.path) || tracks.any { isOnlineTrackPath(it.path) }) return false
    val sources = localAlbumPhysicalSources(album, tracks)
    if (sources.isEmpty()) return false
    return sources.all { availability(it) == LocalSourceAvailability.Missing }
}

internal fun isMissingLocalDocumentFailure(error: Throwable): Boolean {
    return generateSequence(error) { it.cause }
        .any { cause ->
            cause is FileNotFoundException ||
                cause.message.orEmpty().contains("FileNotFoundException", ignoreCase = true) ||
                cause.message.orEmpty().contains("Missing file for", ignoreCase = true)
        }
}
