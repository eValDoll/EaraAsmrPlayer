package com.asmr.player.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import com.asmr.player.domain.model.Album
import com.asmr.player.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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

    @Test
    fun exactTrackTitleUpdatesOnlyTitleAndPreservesPlaybackConfiguration() {
        val item = MediaItemFactory.fromDetails(
            mediaId = "/music/audio.mp3",
            uri = "https://example.com/audio.mp3",
            title = "音轨原名",
            artist = "社团 / 声优",
            albumTitle = "专辑",
            artworkUri = "https://example.com/cover.jpg",
            albumId = 3L,
            trackId = 7L,
            rjCode = "RJ01579615",
            durationSeconds = 65.5,
        ).buildUpon()
            .setCustomCacheKey("audio-cache")
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder().setStartPositionMs(1_000L).setEndPositionMs(4_000L).build(),
            )
            .setRequestMetadata(
                MediaItem.RequestMetadata.Builder().setMediaUri(Uri.parse("https://example.com/request")).build(),
            )
            .build()

        val updated = item.withExactTrackTitle("翻译后的音轨")

        assertEquals("翻译后的音轨", updated.mediaMetadata.title)
        assertEquals(item.mediaId, updated.mediaId)
        assertEquals(item.localConfiguration, updated.localConfiguration)
        assertEquals(item.clippingConfiguration, updated.clippingConfiguration)
        assertEquals(item.requestMetadata, updated.requestMetadata)
        assertEquals(item.mediaMetadata.buildUpon().setTitle("翻译后的音轨").build(), updated.mediaMetadata)
        assertEquals(7L, updated.mediaMetadata.extras?.getLong("track_id"))
        assertEquals("RJ01579615", updated.mediaMetadata.extras?.getString("rj_code"))
    }

    @Test
    fun missingOrUnchangedExactTrackTitleKeepsTheExistingQueueItem() {
        val item = MediaItemFactory.fromDetails(
            mediaId = "https://example.com/audio.mp3#eara-chapter=0,4000&file=01.mp3",
            uri = "https://example.com/audio.mp3",
            title = "章节原名",
        )

        listOf(null, "", "  ", "章节原名").forEach { title ->
            assertSame(item, item.withExactTrackTitle(title))
        }
    }

    @Test
    fun revertedTrackTitleRestoresTheOriginalQueueTitle() {
        val item = MediaItemFactory.fromDetails(
            mediaId = "/music/audio.mp3", uri = "/music/audio.mp3", title = "音轨原名",
        )

        val translated = item.withExactTrackTitle("翻译后的音轨")
        val restored = translated.withExactTrackTitle("音轨原名")

        assertEquals(item, restored)
    }
}
