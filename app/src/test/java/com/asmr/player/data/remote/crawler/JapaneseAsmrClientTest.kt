package com.asmr.player.data.remote.crawler

import com.asmr.player.data.remote.api.AsmrOneAvailabilityApi
import com.asmr.player.util.ChapterMediaReference
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class JapaneseAsmrClientTest {
    @Test fun dmmWorkReadsServerDirectoryAndKeepsOriginalStream() = runBlocking {
        val server = MockWebServer().apply { start() }
        try {
            server.enqueue(MockResponse().setBody("""{"rj":"UND353674","workId":111456,"pageUrl":"https://japaneseasmr.com/111456/",
                "trackTree":[{"title":"japaneseasmr.com","type":"folder","children":[{"title":"本編.m4a","type":"audio",
                "streamUrl":"https://audio.example/d_353674.mp3#eara-chapter=0,&file=%E6%9C%AC%E7%B7%A8.m4a"}]}]}"""))
            val api = AsmrOneAvailabilityApi(OkHttpClient(), Gson()) { server.url("/").toString() }
            val result = JapaneseAsmrClient(api).load("und353674")
            assertEquals("/api/jp-asmr/tracks?rj=UND353674", server.takeRequest(5, TimeUnit.SECONDS)!!.path)
            assertEquals("https://japaneseasmr.com/111456/", result.pageUrl)
            val leaf = result.tree.single().children!!.single()
            val chapter = ChapterMediaReference.parse(leaf.playbackUrl!!)!!
            assertEquals("https://audio.example/d_353674.mp3", chapter.streamUrl)
            assertNull(chapter.endMs)
            assertNull(leaf.downloadUrl)
            try { api.getTrackTreeByRj("UND353674"); fail("DMM must not query asmr.one") }
            catch (_: IOException) { }
            assertEquals(1, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun numericWorkReadsCanonicalServerIdentityAndOriginalStream() = runBlocking {
        val server = MockWebServer().apply { start() }
        try {
            server.enqueue(MockResponse().setBody("""{"rj":"UN124393","workId":138562,"pageUrl":"https://japaneseasmr.com/138562/",
                "trackTree":[{"title":"japaneseasmr.com","type":"folder","children":[{"title":"本編.m4a","type":"audio",
                "streamUrl":"https://audio.example/124393.m3u8#eara-chapter=0,&file=%E6%9C%AC%E7%B7%A8.m4a"}]}]}"""))
            val api = AsmrOneAvailabilityApi(OkHttpClient(), Gson()) { server.url("/").toString() }
            val result = JapaneseAsmrClient(api).load("un124393")
            assertEquals("/api/jp-asmr/tracks?rj=UN124393", server.takeRequest(5, TimeUnit.SECONDS)!!.path)
            val chapter = ChapterMediaReference.parse(result.tree.single().children!!.single().playbackUrl!!)!!
            assertEquals("https://audio.example/124393.m3u8", chapter.streamUrl)
            assertNull(chapter.endMs)
            try { api.getTrackTreeByRj("UN124393"); fail("UN must not query asmr.one") }
            catch (_: IOException) { }
            assertEquals(1, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun readsServerDirectoryAndPreservesChapterAndDownloadMetadata() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody("""{
                "rj":"RJ01707048", "workId":150867,"pageUrl":"https://japaneseasmr.com/150867/",
                "trackTree":[{"title":"japaneseasmr.com","type":"folder","children":[
                    {"title":"01_導入.m4a","type":"audio","duration":106,
                     "streamUrl":"https://audio.example/a.m3u8#eara-chapter=0,106000&file=01_%E5%B0%8E%E5%85%A5.m4a&download=https%3A%2F%2Fdownload.example%2F1",
                     "mediaDownloadUrl":"https://download.example/1"},
                    {"title":"02_本編.m4a","type":"audio",
                     "streamUrl":"https://audio.example/a.m3u8#eara-chapter=106000,&file=02_%E6%9C%AC%E7%B7%A8.m4a"}
                ]}]
            }"""))
            val api = AsmrOneAvailabilityApi(OkHttpClient(), Gson()) { server.url("/").toString() }
            val result = JapaneseAsmrClient(api).load("rj01707048")
            val request = server.takeRequest(5, TimeUnit.SECONDS)!!
            assertEquals("/api/jp-asmr/tracks?rj=RJ01707048", request.path)
            assertEquals("https://japaneseasmr.com/150867/", result.pageUrl)
            val leaves = result.tree.single().children!!
            val first = ChapterMediaReference.parse(leaves[0].playbackUrl!!)!!
            val last = ChapterMediaReference.parse(leaves[1].playbackUrl!!)!!
            assertEquals(0L, first.startMs)
            assertEquals(106000L, first.endMs)
            assertEquals("01_導入.m4a", first.fileName)
            assertEquals("https://download.example/1", first.downloadUrl)
            assertNull(last.endMs)
            assertNull(leaves[1].downloadUrl)
            assertEquals(1, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun missingDirectoryDoesNotFallBackToSiteRequests() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val api = AsmrOneAvailabilityApi(OkHttpClient(), Gson()) { server.url("/").toString() }
            server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":"tracks_not_found"}"""))
            assertTrue(JapaneseAsmrClient(api).load("RJ01707048").tree.isEmpty())
            server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
            try { JapaneseAsmrClient(api).load("RJ01707048"); fail("expected backend failure") }
            catch (_: IOException) { }
            assertEquals(2, server.requestCount)
        } finally { server.shutdown() }
    }
}
