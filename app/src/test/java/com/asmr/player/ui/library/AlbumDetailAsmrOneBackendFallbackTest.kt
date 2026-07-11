package com.asmr.player.ui.library

import com.asmr.player.data.remote.api.AsmrOneTrackNodeResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumDetailAsmrOneBackendFallbackTest {
    @Test
    fun fetchAsmrOneTracksFromBackend_returnsFirstAvailableBackendTree() = runBlocking {
        val backendTree = listOf(AsmrOneTrackNodeResponse(title = "backend.wav", mediaDownloadUrl = "https://example.com/backend.wav"))

        val result = fetchAsmrOneTracksFromBackend(
            backendRjs = listOf("rj01271410"),
            fetchBackend = { rj -> "1271410" to backendTree.also { assertEquals("RJ01271410", rj) } }
        )

        assertEquals("1271410", result.first)
        assertEquals(backendTree, result.third)
        assertEquals(null, result.second)
    }

    @Test
    fun fetchAsmrOneTracksFromBackend_returnsEmptyWhenBackendFailsOrHasNoTree() = runBlocking {
        val requested = mutableListOf<String>()

        val result = fetchAsmrOneTracksFromBackend(
            backendRjs = listOf("RJ01271410", "RJ01271411"),
            fetchBackend = { rj ->
                requested += rj
                if (rj == "RJ01271410") error("backend unavailable")
                "1271411" to emptyList()
            }
        )

        assertEquals(listOf("RJ01271410", "RJ01271411"), requested)
        assertEquals(null, result.first)
        assertEquals(null, result.second)
        assertTrue(result.third.isEmpty())
    }
}
