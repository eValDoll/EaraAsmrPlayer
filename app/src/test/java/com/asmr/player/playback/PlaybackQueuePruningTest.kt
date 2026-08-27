package com.asmr.player.playback

import android.app.Application
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class PlaybackQueuePruningTest {
    @Test
    fun shouldRemovePlaybackItem_matchesAlbumMetadataAndLegacyMediaId() {
        val albumItem = mediaItem(mediaId = "content://library/work/01.wav", albumId = 7L)
        val legacyItem = mediaItem(mediaId = "content://library/work/02.wav")
        val unrelated = mediaItem(mediaId = "content://library/other/01.wav", albumId = 8L)

        assertTrue(
            shouldRemovePlaybackItem(
                item = albumItem,
                removedAlbumIds = setOf(7L),
                removedMediaIds = emptySet(),
            )
        )
        assertTrue(
            shouldRemovePlaybackItem(
                item = legacyItem,
                removedAlbumIds = emptySet(),
                removedMediaIds = setOf("content://library/work/02.wav"),
            )
        )
        assertFalse(
            shouldRemovePlaybackItem(
                item = unrelated,
                removedAlbumIds = setOf(7L),
                removedMediaIds = setOf("content://library/work/02.wav"),
            )
        )
    }

    private fun mediaItem(mediaId: String, albumId: Long? = null): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setExtras(Bundle().apply { albumId?.let { putLong("album_id", it) } })
            .build()
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(mediaId)
            .setMediaMetadata(metadata)
            .build()
    }
}
