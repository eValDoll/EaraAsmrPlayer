package com.asmr.player.playback

import com.asmr.player.domain.model.Album
import com.asmr.player.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaItemMetadataTest {
    @Test
    fun trackQueueCarriesCvSeparatelyFromCircleAndKeepsDuration() {
        val album = Album(id = 1, title = "专辑", path = "", circle = "社团", cv = "声优甲, 声优乙")
        val track = Track(albumId = 1, title = "音频", path = "https://example.com/audio.mp3", duration = 65.5)
        val metadata = MediaItemFactory.fromTrack(album, track).mediaMetadata
        assertEquals("声优甲, 声优乙", metadata.extras?.getString(EXTRA_ALBUM_CV))
        assertEquals(65_500L, metadata.durationMs)
        assertEquals("社团 / 声优甲, 声优乙", metadata.artist)
    }

    @Test
    fun unknownDurationAndExplicitlyEmptyCvStayUnknownAndEmpty() {
        val metadata = MediaItemFactory.fromDetails(
            mediaId = "audio", uri = "https://example.com/audio.mp3", title = "音频",
            artist = "社团", albumCv = "", durationSeconds = 0.0,
        ).mediaMetadata
        assertNull(metadata.durationMs)
        assertEquals("", metadata.extras?.getString(EXTRA_ALBUM_CV))
    }
}
