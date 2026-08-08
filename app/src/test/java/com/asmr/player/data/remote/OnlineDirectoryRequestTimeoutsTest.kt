package com.asmr.player.data.remote

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test

class OnlineDirectoryRequestTimeoutsTest {
    @Test
    fun withOnlineDirectoryRequestTimeouts_setsAllTimeoutsTo2500Milliseconds() {
        val client = OkHttpClient().withOnlineDirectoryRequestTimeouts()

        assertEquals(2_500, client.callTimeoutMillis)
        assertEquals(2_500, client.connectTimeoutMillis)
        assertEquals(2_500, client.readTimeoutMillis)
        assertEquals(2_500, client.writeTimeoutMillis)
    }
}
