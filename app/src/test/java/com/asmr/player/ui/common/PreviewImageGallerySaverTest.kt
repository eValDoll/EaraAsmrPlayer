package com.asmr.player.ui.common

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.asmr.player.cache.CacheImageModel
import java.util.Base64
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PreviewImageGallerySaverTest {

    @Test
    fun resolveSource_usesCacheModelDataAndHeaders() {
        val source = resolvePreviewImageSaveSource(
            PreviewImageSaveRequest(
                key = "gallery:1",
                title = "样图",
                imageModel = CacheImageModel(
                    data = "https://img.example.com/sample.jpg",
                    headers = mapOf("Referer" to "https://www.dlsite.com/")
                ),
                openPathOrUrl = "https://fallback.example.com/sample.jpg"
            )
        )

        assertEquals("https://img.example.com/sample.jpg", source.location)
        assertEquals(mapOf("Referer" to "https://www.dlsite.com/"), source.headers)
    }

    @Test
    fun resolveSource_fallsBackToOpenPathForUnsupportedModel() {
        val source = resolvePreviewImageSaveSource(
            PreviewImageSaveRequest(
                key = "local:1",
                title = "封面",
                imageModel = Any(),
                openPathOrUrl = "/storage/emulated/0/Music/cover.png"
            )
        )

        assertEquals("/storage/emulated/0/Music/cover.png", source.location)
        assertEquals(emptyMap<String, String>(), source.headers)
    }

    @Test
    fun displayName_keepsReadableChineseAndUsesImageExtension() {
        assertEquals(
            "封面测试.webp",
            buildPreviewImageDisplayName(
                title = "封面:测试",
                sourceLocation = "https://img.example.com/raw.webp?token=1",
                mimeType = "image/webp",
                fallbackId = 42L
            )
        )
        assertEquals(
            "Eara_42.jpg",
            buildPreviewImageDisplayName(
                title = "",
                sourceLocation = "https://img.example.com/",
                mimeType = "image/jpeg",
                fallbackId = 42L
            )
        )
    }

    @Test
    fun prepareRemoteImage_downloadsOriginalBytesWithHeadersAndReturnsContentUri() = runBlocking {
        val server = MockWebServer().apply { start() }
        val imageBytes = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
        )
        try {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "image/png")
                    .setBody(okio.Buffer().write(imageBytes))
            )
            val context = ApplicationProvider.getApplicationContext<Context>()
            var stagedFile: java.io.File? = null
            val prepared = preparePreviewImageForExternalOpen(
                context = context,
                request = PreviewImageSaveRequest(
                    key = "gallery:remote",
                    title = "远程样图",
                    imageModel = CacheImageModel(
                        data = server.url("/protected/sample").toString(),
                        headers = mapOf("Referer" to "https://www.dlsite.com/")
                    ),
                    openPathOrUrl = ""
                ),
                httpClient = OkHttpClient(),
                contentUriForFile = { _, file ->
                    stagedFile = file
                    Uri.parse("content://com.asmr.player.fileprovider/shared/${file.name}")
                }
            )

            assertEquals("content", prepared.uri.scheme)
            assertEquals("image/png", prepared.mimeType)
            assertArrayEquals(imageBytes, stagedFile!!.readBytes())
            assertEquals("https://www.dlsite.com/", server.takeRequest().getHeader("Referer"))
        } finally {
            server.shutdown()
        }
    }
}
