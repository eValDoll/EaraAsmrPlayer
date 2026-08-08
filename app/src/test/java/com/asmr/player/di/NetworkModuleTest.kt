package com.asmr.player.di

import com.asmr.player.subtitle.DEEPSEEK_TRANSLATION_CONCURRENCY
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkModuleTest {
    @Test
    fun deepSeekDispatcher_allowsTenRequestsToTheSameHost() {
        val dispatcher = createDeepSeekDispatcher()

        assertEquals(DEEPSEEK_TRANSLATION_CONCURRENCY, dispatcher.maxRequests)
        assertEquals(DEEPSEEK_TRANSLATION_CONCURRENCY, dispatcher.maxRequestsPerHost)
    }
}
