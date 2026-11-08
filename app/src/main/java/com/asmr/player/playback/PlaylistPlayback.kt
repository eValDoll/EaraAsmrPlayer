package com.asmr.player.playback

import android.os.Bundle
import androidx.media3.common.MediaItem

const val EXTRA_SOURCE_PLAYLIST_ID = "source_playlist_id"

fun MediaItem.sourcePlaylistId(): Long? = mediaMetadata.extras
    ?.getLong(EXTRA_SOURCE_PLAYLIST_ID)
    ?.takeIf { it > 0L }

fun MediaItem.withSourcePlaylist(playlistId: Long): MediaItem {
    if (playlistId <= 0L) return this
    val extras = mediaMetadata.extras?.let(::Bundle) ?: Bundle()
    extras.putLong(EXTRA_SOURCE_PLAYLIST_ID, playlistId)
    return buildUpon()
        .setMediaMetadata(mediaMetadata.buildUpon().setExtras(extras).build())
        .build()
}
