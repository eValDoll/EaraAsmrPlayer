package com.asmr.player.data.remote.download

import com.asmr.player.data.remote.crawler.isJapaneseAsmrAudioName
import org.junit.Assert.*
import org.junit.Test
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

class JapaneseAsmrDownloadTest {
    @Test fun onlySupportedAudioFilesAndAudioPayloadsAreAccepted() {
        listOf("mp3", "m4a", "flac", "opus").forEach { assertTrue(isJapaneseAsmrAudioName("track.$it")) }
        listOf("exe", "scr", "lnk", "py", "bat", "cmd", "zip").forEach { assertFalse(isJapaneseAsmrAudioName("track.$it")) }
        assertTrue(isJapaneseAsmrAudioHeader("track.m4a", byteArrayOf(0, 0, 0, 24) + "ftypM4A ".toByteArray()))
        assertTrue(isJapaneseAsmrAudioHeader("track.mp3", "ID3".toByteArray()))
        assertTrue(isJapaneseAsmrAudioHeader("track.flac", "fLaC".toByteArray()))
        assertTrue(isJapaneseAsmrAudioHeader("track.opus", "OggS".toByteArray()))
        assertFalse(isJapaneseAsmrAudioHeader("track.m4a", "<html>challenge</html>".toByteArray()))
        assertFalse(isJapaneseAsmrAudioHeader("track.mp3", "MZ executable".toByteArray()))
        assertFalse(isJapaneseAsmrAudioHeader("track.exe", "ID3".toByteArray()))
        assertFalse(isJapaneseAsmrAudioHeader("track.m4a", byteArrayOf()))
    }

    @Test fun hostRecognitionDoesNotAcceptUnrelatedHosts() {
        assertTrue(isBuzzheavierUrl("https://ts.buzzheavier.com/d/id"))
        assertTrue(isBuzzheavierUrl("https://buzzheavier.com/id"))
        assertFalse(isBuzzheavierUrl("https://buzzheavier.com.example/id"))
    }

    @Test fun resolvesDownloadEndpointAndRefreshesItsLinkForEachTransfer() {
        var resolutions = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            if (request.url.encodedPath.endsWith("/download")) {
                assertEquals("true", request.header("HX-Request"))
                assertEquals("https://buzzheavier.com/file", request.header("Referer"))
                resolutions++
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .header("HX-Redirect", "https://files.example/audio.m4a?token=$resolutions")
                    .body("".toResponseBody()).build()
            } else {
                Response.Builder().request(Request.Builder().url("https://buzzheavier.com/file").build())
                    .protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .header("Content-Type", "text/html")
                    .body("<button hx-get='/file/download'>Download</button>".toResponseBody()).build()
            }
        }.build()
        assertEquals("https://files.example/audio.m4a?token=1",
            resolveJapaneseAsmrDownload(client, "https://ts.buzzheavier.com/d/file", "track.m4a"))
        assertEquals("https://files.example/audio.m4a?token=2",
            resolveJapaneseAsmrDownload(client, "https://ts.buzzheavier.com/d/file", "track.m4a"))
    }

    @Test(expected = java.io.IOException::class)
    fun challengeResponsesCannotBeSavedAsAudio() {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(403).message("Forbidden")
                .body("<html>Just a moment...</html>".toResponseBody()).build()
        }.build()
        resolveJapaneseAsmrDownload(client, "https://buzzheavier.com/file", "track.mp3")
    }
}
