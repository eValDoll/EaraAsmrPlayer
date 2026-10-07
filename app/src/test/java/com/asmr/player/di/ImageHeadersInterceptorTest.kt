package com.asmr.player.di

import com.asmr.player.data.remote.NetworkHeaders
import com.asmr.player.util.JapaneseAsmrAntiHotlink
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.InetAddress

class ImageHeadersInterceptorTest {
    @Test
    fun protectedJapaneseCoverLoadsThroughTheImageClient() {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.getHeader("Referer") == JapaneseAsmrAntiHotlink.REFERER) {
                    MockResponse().setResponseCode(200).setBody("cover")
                } else {
                    MockResponse().setResponseCode(403)
                }
        }
        server.start()
        try {
            val client = imageClient()
            val request = Request.Builder().url("http://pic.weeabo0.xyz:${server.port}/RJ01728295_img_main.jpg").build()
            client.newCall(request).execute().use { assertEquals(200, it.code) }
            val sent = server.takeRequest()
            assertEquals(NetworkHeaders.USER_AGENT, sent.getHeader("User-Agent"))
            assertNull(sent.getHeader(NetworkHeaders.HEADER_EARA_DEVICE_ID))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun callerRefererIsPreserved() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("cover"))
        server.start()
        try {
            val referer = "https://japaneseasmr.com/151151/"
            val request = Request.Builder().url("http://pic.weeabo0.xyz:${server.port}/cover.jpg")
                .header("Referer", referer).build()
            imageClient().newCall(request).execute().close()
            assertEquals(referer, server.takeRequest().getHeader("Referer"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun asmrOneImageHeadersRemainCompatible() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("cover"))
        server.start()
        try {
            imageClient().newCall(Request.Builder().url("http://api.asmr.one:${server.port}/cover.jpg").build()).execute().close()
            val sent = server.takeRequest()
            assertEquals("https://www.asmr.one/", sent.getHeader("Referer"))
            assertEquals("https://www.asmr.one", sent.getHeader("Origin"))
        } finally {
            server.shutdown()
        }
    }

    private fun imageClient(): OkHttpClient = OkHttpClient.Builder()
        .dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> =
                listOf(InetAddress.getByName("127.0.0.1"))
        })
        .addInterceptor(createImageHeadersInterceptor { error("External images must not request the device ID") })
        .build()
}
