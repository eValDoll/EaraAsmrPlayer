package com.asmr.player.playback

import com.asmr.player.util.ChapterMediaReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class ChapterMediaPlaybackTest {
    @Test fun chaptersHaveDistinctQueueIdentitiesButShareTheStreamAndClipCorrectly() {
        val first = ChapterMediaReference("https://audio.example/work.m3u8", 0L, 106000L,
            "https://ts.buzzheavier.com/d/one", "01_導入.m4a")
        val second = first.copy(startMs = 106000L, endMs = null, fileName = "02_本編.m4a")
        assertEquals(first, ChapterMediaReference.parse(first.encode()))
        val items = listOf(first, second).map {
            MediaItemFactory.fromDetails(it.encode(), it.encode(), it.fileName)
        }
        assertNotEquals(items[0].mediaId, items[1].mediaId)
        assertEquals(items[0].localConfiguration!!.uri, items[1].localConfiguration!!.uri)
        assertEquals("https://audio.example/work.m3u8", items[0].localConfiguration!!.uri.toString())
        assertEquals("application/x-mpegURL", items[0].localConfiguration!!.mimeType)
        assertEquals(0L, items[0].clippingConfiguration.startPositionMs)
        assertEquals(106000L, items[0].clippingConfiguration.endPositionMs)
        val restored = MediaItemFactory.chapterClipping(items[1].mediaId, items[1].localConfiguration!!.uri.toString())
        assertEquals(106000L, restored.startPositionMs)
        assertEquals(androidx.media3.common.C.TIME_END_OF_SOURCE, restored.endPositionMs)
    }

    @Test fun malformedBoundariesCannotBecomeClips() {
        assertNull(ChapterMediaReference.parse("https://audio.example/a.m3u8#eara-chapter=20,10"))
        assertNull(ChapterMediaReference.parse("https://audio.example/a.m3u8#eara-chapter=-1,"))
        assertNull(ChapterMediaReference.parse("https://audio.example/a.m3u8#eara-chapter=bad,"))
        assertNull(ChapterMediaReference.parse("https://audio.example/a.m3u8#ordinary-fragment"))
    }
}
