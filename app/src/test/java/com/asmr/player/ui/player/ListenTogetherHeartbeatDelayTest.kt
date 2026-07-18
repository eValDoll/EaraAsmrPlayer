package com.asmr.player.ui.player

import com.asmr.player.listentogether.ListenTogetherPresenceResponse
import org.junit.Assert.assertEquals
import org.junit.Test

class ListenTogetherHeartbeatDelayTest {

    @Test
    fun respectsBackendHeartbeatInterval() {
        val response = response(heartbeatIntervalMs = 15_000L)

        assertEquals(15_000L, resolveListenTogetherHeartbeatDelayMs(response))
    }

    @Test
    fun keepsHeartbeatBelowExpiryWindow() {
        val response = response(
            heartbeatIntervalMs = 30_000L,
            expiresInMs = 18_000L
        )

        assertEquals(12_000L, resolveListenTogetherHeartbeatDelayMs(response))
    }

    @Test
    fun fallsBackToOneMinuteWhenBackendOmitsInterval() {
        assertEquals(60_000L, resolveListenTogetherHeartbeatDelayMs(null))
    }

    private fun response(
        heartbeatIntervalMs: Long,
        expiresInMs: Long? = null
    ): ListenTogetherPresenceResponse {
        return ListenTogetherPresenceResponse(
            listenerCount = 1,
            sessionKey = "RJ000000:track",
            heartbeatIntervalMs = heartbeatIntervalMs,
            expiresInMs = expiresInMs
        )
    }
}
