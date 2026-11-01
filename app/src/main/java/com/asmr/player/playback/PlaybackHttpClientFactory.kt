package com.asmr.player.playback

import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import okhttp3.Call
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.Request

internal class PlaybackHttpClientFactory(
    private val baseClient: OkHttpClient,
    private val register: (OkHttpClient) -> OkHttpClient,
    private val unregister: (OkHttpClient) -> Unit,
) : Call.Factory, AutoCloseable {
    private var client = ClientState()
    private var closed = false

    @Synchronized
    override fun newCall(request: Request): Call {
        check(!closed) { "Playback HTTP client is closed" }
        return client.httpClient.newCall(request)
    }

    fun reset() {
        val previous = synchronized(this) {
            if (closed) return
            val next = ClientState()
            client.also { client = next }
        }
        previous.close()
    }

    override fun close() {
        val previous = synchronized(this) {
            if (closed) return
            closed = true
            client
        }
        previous.close()
    }

    private inner class ClientState : AutoCloseable {
        private val calls = ConcurrentHashMap.newKeySet<Call>()
        @Volatile private var closed = false

        val httpClient: OkHttpClient = register(
            baseClient.newBuilder()
                .dispatcher(Dispatcher())
                .connectionPool(ConnectionPool())
                .eventListener(object : EventListener() {
                    override fun callStart(call: Call) {
                        calls.add(call)
                        if (closed) call.cancel()
                    }

                    override fun callEnd(call: Call) = finish(call)
                    override fun callFailed(call: Call, ioe: IOException) = finish(call)
                })
                .build(),
        )

        private fun finish(call: Call) {
            calls.remove(call)
            if (closed) httpClient.connectionPool.evictAll()
        }

        override fun close() {
            closed = true
            unregister(httpClient)
            // Dispatcher 在交付响应头后便不再跟踪异步 Call，流式响应体仍需显式取消。
            calls.forEach(Call::cancel)
            httpClient.connectionPool.evictAll()
            httpClient.dispatcher.executorService.shutdown()
        }
    }
}
