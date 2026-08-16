package com.asmr.player.ui.library

import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.data.local.db.entities.TrackEntity
import com.asmr.player.util.DlsiteWorkNo
import com.asmr.player.util.isOnlineTrackPath
import com.asmr.player.util.isVirtualAlbumPath
import java.io.File

internal fun shouldBackfillLegacyOnlineSavedAlbumRoot(
    entity: AlbumEntity,
    tracks: List<TrackEntity>
): Boolean {
    val hasNoLocalRoot = entity.localPath?.trim().orEmpty().isBlank() &&
        entity.downloadPath?.trim().orEmpty().isBlank()
    if (!hasNoLocalRoot) return false

    val hasOnlineIdentity = isVirtualAlbumPath(entity.path) || tracks.any { isOnlineTrackPath(it.path) }
    if (!hasOnlineIdentity) return false

    val workKey = legacyOnlineSavedAlbumWorkKey(entity)
    return workKey.isNotBlank()
}

internal fun legacyOnlineSavedAlbumFolderName(entity: AlbumEntity): String {
    return safeLegacyOnlineSavedAlbumFolderName(legacyOnlineSavedAlbumWorkKey(entity))
}

private fun legacyOnlineSavedAlbumWorkKey(entity: AlbumEntity): String {
    return DlsiteWorkNo.extractWorkNo(entity.rjCode)
        .ifBlank { DlsiteWorkNo.extractWorkNo(entity.workId) }
        .ifBlank { DlsiteWorkNo.extractWorkNo(entity.path) }
        .ifBlank { DlsiteWorkNo.extractWorkNo(entity.title) }
        .ifBlank { entity.workId.trim() }
        .ifBlank { entity.title.trim() }
}

internal fun safeLegacyOnlineSavedAlbumFolderName(input: String): String {
    return input.trim().ifEmpty { "album" }.replace(Regex("""[\\/:*?"<>|]"""), "_")
}

internal fun ensureLibraryAlbumDir(dir: File) {
    if (!dir.exists()) dir.mkdirs()
    val albumsRoot = dir.parentFile
    if (albumsRoot != null && !albumsRoot.exists()) albumsRoot.mkdirs()
    val rootMarker = albumsRoot?.let { File(it, ".nomedia") }
    if (rootMarker != null && !rootMarker.exists()) rootMarker.createNewFile()
    val marker = File(dir, ".nomedia")
    if (!marker.exists()) marker.createNewFile()
}
