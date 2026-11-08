package com.asmr.player.playback

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test

class PlaybackHttpClientFactoryTest {
    @Test
    fun resetCancelsHungLoadsAndUsesANewConnection() {
        val server = MockWebServer()
        val clients = mutableListOf<OkHttpClient>()
        val released = mutableListOf<OkHttpClient>()
        val factory = PlaybackHttpClientFactory(
            OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build(),
            register = { it.also(clients::add) },
            unregister = { released.add(it) },
        )
        try {
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val failed = CountDownLatch(1)
            val oldCall = factory.newCall(Request.Builder().url(server.url("/audio")).build())
            oldCall.enqueue(failureCallback(failed))
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))

            factory.reset()
            assertTrue(oldCall.isCanceled())
            assertTrue(failed.await(5, TimeUnit.SECONDS))
            assertNotSame(clients[0].connectionPool, clients[1].connectionPool)
            assertNotSame(clients[0].dispatcher, clients[1].dispatcher)
            assertEquals(listOf(clients[0]), released)

            server.enqueue(MockResponse().setBody("resumed"))
            factory.newCall(Request.Builder().url(server.url("/audio")).build()).execute().use {
                assertEquals("resumed", it.body?.string())
            }
            assertEquals(0, server.takeRequest(5, TimeUnit.SECONDS)?.sequenceNumber)
        } finally {
            factory.close()
            server.shutdown()
        }
        assertEquals(clients, released)
    }

    @Test
    fun resetAlsoCancelsStreamsWhoseResponseHeadersHaveAlreadyArrived() {
        val server = MockWebServer()
        val factory = PlaybackHttpClientFactory(OkHttpClient(), { it }, {})
        try {
            server.enqueue(MockResponse().setBody("audio"))
            val call = factory.newCall(Request.Builder().url(server.url("/audio")).build())
            call.execute().use {
                factory.reset()
                assertTrue(call.isCanceled())
            }
        } finally {
            factory.close()
            server.shutdown()
        }
    }

    @Test
    fun resettingPlaybackDoesNotCancelTheSharedApiClient() {
        val server = MockWebServer()
        val apiClient = OkHttpClient()
        val factory = PlaybackHttpClientFactory(apiClient, { it }, {})
        var apiCall: Call? = null
        try {
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            apiCall = apiClient.newCall(Request.Builder().url(server.url("/api")).build())
            apiCall.enqueue(failureCallback(CountDownLatch(1)))
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))

            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val playbackCall = factory.newCall(Request.Builder().url(server.url("/audio")).build())
            playbackCall.enqueue(failureCallback(CountDownLatch(1)))
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))

            factory.reset()
            assertTrue(playbackCall.isCanceled())
            assertFalse(apiCall.isCanceled())
        } finally {
            factory.close()
            apiCall?.cancel()
            apiClient.connectionPool.evictAll()
            apiClient.dispatcher.executorService.shutdown()
            server.shutdown()
        }
    }

    @Test
    fun closeReleasesTheClientOnceAndRejectsNewCalls() {
        val clients = mutableListOf<OkHttpClient>()
        val released = mutableListOf<OkHttpClient>()
        val factory = PlaybackHttpClientFactory(
            OkHttpClient(), { it.also(clients::add) }, { released.add(it) },
        )

        factory.close()
        factory.close()

        assertEquals(clients, released)
        assertTrue(clients.single().dispatcher.executorService.isShutdown)
        assertThrows(IllegalStateException::class.java) {
            factory.newCall(Request.Builder().url("https://example.com/audio").build())
        }
    }

    private fun failureCallback(failed: CountDownLatch) = object : Callback {
        override fun onFailure(call: Call, e: IOException) { failed.countDown() }
        override fun onResponse(call: Call, response: Response) { response.close() }
    }
}
