package com.asmr.player.playback

import androidx.media3.common.MediaItem

internal fun shouldRemovePlaybackItem(
    item: MediaItem,
    removedAlbumIds: Set<Long>,
    removedMediaIds: Set<String>,
): Boolean {
    if (item.mediaId in removedMediaIds) return true
    val extras = item.mediaMetadata.extras ?: return false
    if (!extras.containsKey("album_id")) return false
    return extras.getLong("album_id") in removedAlbumIds
}
