package com.asmr.player.di

import com.asmr.player.subtitle.DEEPSEEK_TRANSLATION_CONCURRENCY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkModuleTest {
    @Test
    fun deepSeekDispatcher_allowsTenRequestsToTheSameHost() {
        val dispatcher = createDeepSeekDispatcher()

        assertEquals(DEEPSEEK_TRANSLATION_CONCURRENCY, dispatcher.maxRequests)
        assertEquals(DEEPSEEK_TRANSLATION_CONCURRENCY, dispatcher.maxRequestsPerHost)
    }

    @Test
    fun asmrOneSiteRequest_recognizesDirectMirrorsAndBackupPath() {
        assertTrue(isAsmrOneSiteRequest("api.asmr.one", "/api/search"))
        assertTrue(isAsmrOneSiteRequest("api.asmr-100.com", "/api/search"))
        assertTrue(isAsmrOneSiteRequest("api.asmr-200.com", "/api/search"))
        assertTrue(isAsmrOneSiteRequest("api.asmr-300.com", "/api/search"))
        assertTrue(isAsmrOneSiteRequest("example.com", "/api/asmr-one/tracks"))
        assertFalse(isAsmrOneSiteRequest("example.com", "/api/search"))
    }
}
