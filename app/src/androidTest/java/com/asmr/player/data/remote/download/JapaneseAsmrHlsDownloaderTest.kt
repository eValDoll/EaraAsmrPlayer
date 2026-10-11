package com.asmr.player.data.remote.download

import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.asmr.player.subtitle.LocalAudioDecoder
import com.asmr.player.util.ChapterMediaReference
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class JapaneseAsmrHlsDownloaderTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun trimsAcrossSegmentBoundariesWithoutChangingTheAudio() = runBlocking {
        withFixture { url ->
            withOutput { output ->
                export(url, 4_500L, 6_500L, output)
                assertDuration(output, 2_000L)
                var samples = 0
                var crossings = 0
                var previous = 0f
                LocalAudioDecoder(context).decode(output.absolutePath, 0L) { chunk ->
                    for (sample in chunk.channelSamples[0]) {
                        if (previous <= 0f && sample > 0f) crossings++
                        previous = sample
                        samples++
                    }
                }
                val frequency = crossings * 16_000.0 / samples
                assertTrue("expected late-chapter 880 Hz audio, got $frequency", abs(frequency - 880.0) < 20.0)
            }
        }
    }

    @Test fun lastChapterStopsAtTheEndOfThePlaylist() = runBlocking {
        withFixture { url ->
            withOutput { output ->
                export(url, 4_000L, null, output)
                assertDuration(output, 4_000L)
            }
        }
    }

    @Test fun cancellationStopsTheExportAndPreservesTheExistingPartialFile() = runBlocking {
        withFixture(throttled = true) { url ->
            withOutput { output ->
                val previous = byteArrayOf(1, 2, 3, 4)
                output.writeBytes(previous)
                try {
                    withTimeout(800L) {
                        downloadJapaneseAsmrHls(context, OkHttpClient(),
                            ChapterMediaReference(url, 0, null, null, output.name), output) { _, _ -> }
                    }
                    fail("export should have been cancelled")
                } catch (_: TimeoutCancellationException) {
                    assertArrayEquals(previous, output.readBytes())
                    assertFalse(File(output.parentFile, output.name + ".hls.part").exists())
                }
            }
        }
    }

    @Test fun realChapterWhenRequested() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        val url = arguments.getString("hlsUrl")
        assumeTrue("supply hlsUrl to run the live-source check", url != null)
        val start = arguments.getString("hlsStartMs")?.toLong() ?: 0L
        val end = arguments.getString("hlsEndMs")?.toLong() ?: 3_000L
        withOutput { output ->
            export(requireNotNull(url), start, end, output)
            assertDuration(output, end - start)
            Log.i("JapaneseAsmrHlsTest", "url=$url start=$start end=$end bytes=${output.length()} verified")
        }
    }

    @Test fun realMissingSourceRequiresConfirmationWhenRequested() = runBlocking<Unit> {
        val arguments = InstrumentationRegistry.getArguments()
        val url = arguments.getString("hlsUrl")
        assumeTrue("supply hlsUrl to check download preparation", url != null)
        val reference = ChapterMediaReference(requireNotNull(url), 0, 3_000L,
            arguments.getString("hlsDownloadUrl"), "track.m4a")
        val request = DownloadBatchRequest("jp-hls-preflight-test", "jp-hls-preflight-test", listOf(
            RelativeDownloadItem(reference.encode(), "japaneseasmr.com/track.m4a"),
        ))
        val client = EntryPointAccessors.fromApplication(context,
            DownloadWorker.DownloadWorkerEntryPoint::class.java).okHttpClient()
        var prompted = false
        val prepared = JapaneseAsmrDownloadPreflight(client).prepare(request) {
            prompted = true
            false
        }
        assertTrue(prompted)
        assertNull(prepared)
        Log.i("JapaneseAsmrHlsTest", "missing-source confirmation verified; no task created")
    }

    private suspend fun export(url: String, start: Long, end: Long?, output: File) {
        withTimeout(120_000L) {
            val client = if (url.startsWith("https://")) {
                EntryPointAccessors.fromApplication(context, DownloadWorker.DownloadWorkerEntryPoint::class.java).okHttpClient()
            } else OkHttpClient()
            downloadJapaneseAsmrHls(context, client,
                ChapterMediaReference(url, start, end, null, output.name), output) { _, _ -> }
        }
        assertTrue(output.length() > 0L)
        assertFalse(File(output.parentFile, output.name + ".hls.part").exists())
    }

    private fun assertDuration(output: File, expectedMs: Long) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(output.absolutePath)
            assertEquals(1, extractor.trackCount)
            val format = extractor.getTrackFormat(0)
            assertEquals("audio/mp4a-latm", format.getString(MediaFormat.KEY_MIME))
            assertEquals(2, format.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
            val actualMs = format.getLong(MediaFormat.KEY_DURATION) / 1_000L
            assertTrue("duration=$actualMs expected=$expectedMs", abs(actualMs - expectedMs) < 100L)
        } finally {
            extractor.release()
        }
    }

    private suspend fun withOutput(block: suspend (File) -> Unit) {
        val output = File.createTempFile("jp-hls-test-", ".m4a", context.cacheDir)
        try { block(output) } finally {
            output.delete()
            File(output.parentFile, output.name + ".hls.part").delete()
        }
    }

    private suspend fun withFixture(throttled: Boolean = false, block: suspend (String) -> Unit) {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val file = request.path.orEmpty().substringAfterLast('/')
                val bytes = instrumentation.context.assets.open("jp-hls/$file").use { it.readBytes() }
                return MockResponse().setBody(Buffer().write(bytes)).apply {
                    if (throttled && file.endsWith(".ts")) throttleBody(1_024L, 200L, TimeUnit.MILLISECONDS)
                }
            }
        }
        server.start()
        try { block(server.url("/tones.m3u8").toString()) } finally { server.shutdown() }
    }
}
