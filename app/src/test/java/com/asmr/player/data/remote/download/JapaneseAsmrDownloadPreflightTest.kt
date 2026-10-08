package com.asmr.player.data.remote.download

import com.asmr.player.util.ChapterMediaReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

class JapaneseAsmrDownloadPreflightTest {
    private fun item(name: String = "track.m4a", native: String? = null) = RelativeDownloadItem(
        ChapterMediaReference("https://audio.example/work.m3u8", 10_000L, 20_000L, native, name).encode(),
        "japaneseasmr.com/$name",
    )
    private fun request(vararg items: RelativeDownloadItem) =
        DownloadBatchRequest("RJ01650053", "album:RJ01650053", items.toList())
    private fun client(status: Int, body: ByteArray = byteArrayOf(0, 0, 0, 24) + "ftypM4A ".toByteArray()) =
        OkHttpClient.Builder().addInterceptor { chain ->
            assertEquals("bytes=0-15", chain.request().header("Range"))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(status).message("fixture").body(body.toResponseBody()).build()
        }.build()

    @Test fun cancelMissingFileDoesNotProduceADownloadRequest() = runBlocking {
        var confirmations = 0
        val prepared = JapaneseAsmrDownloadPreflight(OkHttpClient()).prepare(request(item())) { files ->
            assertEquals(listOf("track.m4a"), files)
            confirmations++
            false
        }
        assertNull(prepared)
        assertEquals(1, confirmations)
    }

    @Test fun expiredFilesRequireConfirmationAndKeepTheChapterBoundaries() = runBlocking {
        for (status in listOf(404, 410)) {
            var confirmations = 0
            val prepared = JapaneseAsmrDownloadPreflight(client(status)).prepare(
                request(item(native = "https://files.example/expired.m4a")),
            ) { confirmations++; true }
            val chapter = ChapterMediaReference.parse(prepared!!.items.single().url)!!
            assertEquals(1, confirmations)
            assertTrue(chapter.useHlsDownload)
            assertNull(chapter.downloadUrl)
            assertEquals(10_000L, chapter.startMs)
            assertEquals(20_000L, chapter.endMs)
        }
    }

    @Test fun onlyMissingFilesSwitchToM4aWhileAvailableFilesKeepTheirOriginalRequest() = runBlocking {
        val available = item("original.m4a", "https://files.example/original.m4a")
        val missing = item("missing.flac")
        val prepared = JapaneseAsmrDownloadPreflight(client(206)).prepare(request(available, missing)) { files ->
            assertEquals(listOf("missing.flac"), files)
            true
        }!!
        assertEquals(available, prepared.items[0])
        assertEquals("japaneseasmr.com/missing.m4a", prepared.items[1].relativePath)
        assertTrue(ChapterMediaReference.parse(prepared.items[1].url)!!.useHlsDownload)
    }

    @Test fun forbiddenRateLimitedOrFailedRequestsAreNotReportedAsMissingFiles() = runBlocking {
        for (status in listOf(403, 429, 500)) {
            try {
                JapaneseAsmrDownloadPreflight(client(status)).prepare(
                    request(item(native = "https://files.example/track.m4a")),
                ) { fail("network errors must not prompt about missing files"); true }
                fail("status $status must fail the check")
            } catch (_: IOException) { }
        }
    }

    @Test fun challengeHtmlIsNotAcceptedAsAnAvailableAudioFile() = runBlocking {
        try {
            JapaneseAsmrDownloadPreflight(client(200, "<html>challenge</html>".toByteArray())).prepare(
                request(item(native = "https://files.example/track.m4a")),
            ) { fail("challenge is not evidence that a source file is missing"); true }
            fail("HTML must fail the check")
        } catch (_: IOException) { }
    }

    @Test fun preparingWaitsForAnExplicitAnswerAndCanBeCancelled() = runBlocking {
        val prompted = CompletableDeferred<Unit>()
        val answer = CompletableDeferred<Boolean>()
        val pending = async {
            JapaneseAsmrDownloadPreflight(OkHttpClient()).prepare(request(item())) {
                prompted.complete(Unit)
                answer.await()
            }
        }
        withTimeout(2_000L) { prompted.await() }
        assertFalse(pending.isCompleted)
        pending.cancel()
        pending.join()
        assertTrue(pending.isCancelled)
        assertFalse(answer.isCompleted)
    }

    @Test fun ordinaryAndDmmDownloadsDoNotNeedThisConfirmation() = runBlocking {
        val direct = RelativeDownloadItem("https://files.example/audio.flac", "audio.flac")
        val dmm = RelativeDownloadItem(
            ChapterMediaReference("https://files.example/audio.mp3", 0, null, null, "audio.mp3").encode(),
            "japaneseasmr.com/audio.mp3",
        )
        val request = request(direct, dmm)
        assertEquals(request, JapaneseAsmrDownloadPreflight(OkHttpClient()).prepare(request) {
            fail("ordinary downloads must not prompt"); false
        })
    }

    @Test fun stalledResponseBodyIsCancelledAtTheCheckDeadline() = runBlocking<Unit> {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("audio payload").setBodyDelay(5, TimeUnit.SECONDS))
        server.start()
        try {
            val client = OkHttpClient()
            val started = System.nanoTime()
            try {
                JapaneseAsmrDownloadPreflight(client, 300L).prepare(
                    request(item(native = server.url("/track.m4a").toString())),
                ) { fail("a stalled response is not evidence of a missing file"); true }
                fail("check must time out")
            } catch (_: TimeoutCancellationException) { }
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 2_000L)
            assertEquals(1, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun cancellingTheCheckCancelsTheNetworkRequest() = runBlocking<Unit> {
        val server = MockWebServer()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        server.start()
        try {
            val client = OkHttpClient()
            val pending = async {
                JapaneseAsmrDownloadPreflight(client).prepare(
                    request(item(native = server.url("/track.m4a").toString())),
                ) { fail("cancelled checks must not prompt"); true }
            }
            withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(2, TimeUnit.SECONDS)) }
            val call = client.dispatcher.runningCalls().single()
            pending.cancel()
            withTimeout(1_000L) { pending.join() }
            assertTrue(call.isCanceled())
        } finally { server.shutdown() }
    }

    @Test fun theCheckDeadlineDoesNotLimitWaitingForUserConfirmation() = runBlocking<Unit> {
        val prepared = JapaneseAsmrDownloadPreflight(OkHttpClient(), 100L).prepare(request(item())) {
            delay(200L)
            true
        }
        assertTrue(ChapterMediaReference.parse(prepared!!.items.single().url)!!.useHlsDownload)
    }
}
