package com.asmr.player.data.local.library

import android.content.Context
import com.asmr.player.data.local.db.dao.AlbumDao
import com.asmr.player.util.EmbeddedMediaExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun refreshScannedAlbumCover(
    context: Context,
    albumDao: AlbumDao,
    albumId: Long,
    firstAudioPath: String?,
    directoryCoverPath: String,
) = withContext(Dispatchers.IO) {
    val album = albumDao.getAlbumById(albumId) ?: return@withContext
    val resolvedPath = resolveScannedAlbumCoverPath(
        context = context,
        albumId = albumId,
        firstAudioPath = firstAudioPath,
        directoryCoverPath = directoryCoverPath,
        previousCoverPath = album.coverPath,
    )
    if (resolvedPath == album.coverPath) return@withContext
    albumDao.updateAutomaticCoverIfSourceMatches(
        albumId = albumId,
        expectedCoverPath = album.coverPath,
        expectedCoverUrl = album.coverUrl,
        coverPath = resolvedPath,
        coverThumbPath = "",
    )
    Unit
}

private fun resolveScannedAlbumCoverPath(
    context: Context,
    albumId: Long,
    firstAudioPath: String?,
    directoryCoverPath: String,
    previousCoverPath: String,
): String {
    if (!firstAudioPath.isNullOrBlank()) {
        val cached = EmbeddedMediaExtractor.getArtworkCacheFile(context, "album:$albumId")
        if (cached.isFile && cached.length() > 0L) return cached.absolutePath
        val artwork = EmbeddedMediaExtractor.extractArtwork(context, firstAudioPath)
        if (artwork != null) {
            val saved = try {
                EmbeddedMediaExtractor.saveArtworkToCache(context, albumId, artwork)
            } finally {
                artwork.recycle()
            }
            if (!saved.isNullOrBlank()) return saved
        }
    }
    return directoryCoverPath.ifBlank { previousCoverPath }
}
