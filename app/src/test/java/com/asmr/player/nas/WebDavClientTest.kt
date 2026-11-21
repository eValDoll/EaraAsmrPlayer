package com.asmr.player.nas

import com.asmr.player.LocalizedRobolectricTest
import okhttp3.Credentials
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

class WebDavClientTest : LocalizedRobolectricTest() {
    private val server = MockWebServer()
    private val location = WebDavLocation(server.url("/music/").toString(), "listener")
    private val client = WebDavClient(location, "secret")

    @After fun close() = server.shutdown()

    private fun entry(href: String, dir: Boolean = false, size: Int = 0) = """
        <D:response><D:href>$href</D:href><D:propstat><D:prop>
        <D:resourcetype>${if (dir) "<D:collection/>" else ""}</D:resourcetype>
        <D:getcontentlength>$size</D:getcontentlength></D:prop>
        <D:status>HTTP/1.1 200 OK</D:status></D:propstat></D:response>
    """.trimIndent()

    private fun listing(vararg entries: String) = server.enqueue(MockResponse().setResponseCode(207)
        .setBody("""<D:multistatus xmlns:D="DAV:">${entries.joinToString("")}</D:multistatus>"""))

    @Test fun listsImmediateChildrenDecodesNamesAndConfinesCredentials() {
        listing(entry("/music/", true), entry("/music/RJ123456%20%23/"), entry("/outside/"), entry("https://another.test/music/private"),
            entry("/music/nested/song"), entry("/music/a%2Fb"))
        assertEquals(listOf("RJ123456 #"), client.list("").map { it.name })
        val request = server.takeRequest()
        assertEquals("PROPFIND", request.method)
        assertEquals("1", request.getHeader("Depth"))
        assertEquals(Credentials.basic("listener", "secret"), request.getHeader("Authorization"))
        assertEquals("/music/RJ123456%20%23", location.url("RJ123456 #").encodedPath)
    }

    @Test fun incompleteOrFailedListingsNeverReturnAnEmptyLibrary() {
        listing(entry("/music/file.mp3"))
        assertThrows(IOException::class.java) { client.list("") }
        listing(entry("/music/", true), "<D:response><D:href>/music/RJ/</D:href><D:status>HTTP/1.1 503 Inaccessible</D:status></D:response>")
        assertThrows(IOException::class.java) { client.list("") }
        server.enqueue(MockResponse().setResponseCode(401))
        assertThrows(IOException::class.java) { client.list("") }
    }

    @Test fun rangeReadsAndUploadsWaitForTheServerResponse() {
        listing(entry("/music/test.wav", size = 8))
        server.enqueue(MockResponse().setResponseCode(206).setBody("efgh"))
        val data = WebDavMediaSource(client, "test.wav")
        val out = ByteArray(4)
        assertEquals(4, data.readAt(4, out, 0, 4))
        assertEquals("efgh", out.decodeToString())
        server.takeRequest()
        assertEquals("bytes=4-7", server.takeRequest().getHeader("Range"))
        server.enqueue(MockResponse().setResponseCode(201))
        client.mkdir(".eara-downloads")
        assertEquals("MKCOL", server.takeRequest().method)
        server.enqueue(MockResponse().setHeadersDelay(11, TimeUnit.SECONDS).setResponseCode(201))
        client.put(".eara-downloads/test.wav", out.toRequestBody())
        val upload = server.takeRequest()
        assertEquals("PUT", upload.method)
        assertEquals("efgh", upload.body.readUtf8())
        server.enqueue(MockResponse().setResponseCode(507))
        assertThrows(IOException::class.java) { client.put(".eara-downloads/test.wav", out.toRequestBody()) }
    }
}
