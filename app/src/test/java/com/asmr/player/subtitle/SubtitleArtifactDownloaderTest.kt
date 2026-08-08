package com.asmr.player.subtitle

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SubtitleArtifactDownloaderTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `downloads a pinned-size file and publishes completion`() = runBlocking {
        val data = ByteArray(32 * 1024) { index -> (index % 251).toByte() }
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(data)))
        val destination = temporaryFolder.newFile("artifact.part")
        val progress = mutableListOf<Long>()

        SubtitleArtifactDownloader.download(
            client = OkHttpClient(),
            url = server.url("/artifact").toString(),
            destination = destination,
            expectedBytes = data.size.toLong(),
            onProgress = progress::add
        )

        assertArrayEquals(data, destination.readBytes())
        assertEquals(data.size.toLong(), progress.last())
    }

    @Test
    fun `resumes when the server returns the matching content range`() = runBlocking {
        val data = "0123456789".toByteArray()
        val destination = temporaryFolder.newFile("artifact.part").apply {
            writeBytes(data.copyOfRange(0, 4))
        }
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes 4-9/10")
                .setBody(Buffer().write(data.copyOfRange(4, data.size)))
        )

        SubtitleArtifactDownloader.download(
            client = OkHttpClient(),
            url = server.url("/artifact").toString(),
            destination = destination,
            expectedBytes = data.size.toLong(),
            onProgress = {}
        )

        assertArrayEquals(data, destination.readBytes())
        assertEquals("bytes=4-", server.takeRequest().getHeader("Range"))
    }

    @Test
    fun `deletes partial data when the content range is invalid`() = runBlocking {
        val destination = temporaryFolder.newFile("artifact.part").apply {
            writeText("part")
        }
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes 0-3/4")
                .setBody("rest")
        )

        val failure = runCatching {
            SubtitleArtifactDownloader.download(
                client = OkHttpClient(),
                url = server.url("/artifact").toString(),
                destination = destination,
                expectedBytes = 8L,
                onProgress = {}
            )
        }.exceptionOrNull()

        assertTrue(failure?.message.orEmpty().contains("无效的断点数据"))
        assertFalse(destination.exists())
    }
}
