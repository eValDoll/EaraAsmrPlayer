package com.asmr.player.listentogether

import android.app.Application
import com.asmr.player.data.remote.NetworkHeaders
import com.asmr.player.playback.MediaItemFactory
import com.asmr.player.util.ChapterMediaReference
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ChapterListenTogetherIdentityTest {
    private val chapter = ChapterMediaReference(
        "https://v.weeab0o.xyz/RJ01707048.m3u8", 0L, 106000L,
        "https://ts.buzzheavier.com/d/one", "01_導入.m4a"
    )
    private val resolver = ListenTogetherIdentityResolver(
        OkHttpClient.Builder().addInterceptor { error("Chapter identity must not request the playlist") }.build()
    )

    private suspend fun resolve(reference: ChapterMediaReference): ListenTogetherTrackIdentity? {
        val item = MediaItemFactory.fromDetails(
            reference.encode(), reference.encode(), reference.fileName, rjCode = "RJ01707048"
        )
        return resolver.resolve(RuntimeEnvironment.getApplication(), item)
    }

    @Test fun chaptersOfTheSameStreamHaveIndependentSessions() = runBlocking {
        val first = resolve(chapter)!!
        val second = resolve(chapter.copy(startMs = 106000L, endMs = 1018000L))!!
        assertNotEquals(first.sessionKey, second.sessionKey)
        assertEquals("hls-chapter+xxh64", first.fingerprintAlgorithm)
        assertEquals(0L, first.fileSizeBytes)
        assertEquals(ListenTogetherMediaKind.AUDIO, first.mediaKind)
    }

    @Test fun renamedFilesAndRefreshedDownloadLinksKeepTheSameSession() = runBlocking {
        assertEquals(resolve(chapter)!!.sessionKey, resolve(chapter.copy(
            fileName = "導入.m4a", downloadUrl = "https://ts.buzzheavier.com/d/new"
        ))!!.sessionKey)
    }

    @Test fun differentStreamsDoNotShareAChapterSession() = runBlocking {
        assertNotEquals(resolve(chapter)!!.sessionKey,
            resolve(chapter.copy(streamUrl = "https://v.weeab0o.xyz/RJ01707048-part2.m3u8"))!!.sessionKey)
    }

    @Test fun failedOptionalFileProbeUsesSilentErrors() = runBlocking {
        var requests = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests++
            assertEquals(NetworkHeaders.SILENT_IO_ERROR_ON,
                chain.request().header(NetworkHeaders.HEADER_SILENT_IO_ERROR))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(403).message("Forbidden").body("".toResponseBody()).build()
        }.build()
        val item = MediaItemFactory.fromDetails(
            "https://audio.example/track.mp3", "https://audio.example/track.mp3", "track", rjCode = "RJ01707048"
        )
        assertNull(ListenTogetherIdentityResolver(client).resolve(RuntimeEnvironment.getApplication(), item))
        assertEquals(1, requests)
    }
}
