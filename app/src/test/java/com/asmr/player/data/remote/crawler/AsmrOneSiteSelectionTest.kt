package com.asmr.player.data.remote.crawler

import com.asmr.player.data.remote.api.AsmrOneEndpoint
import com.asmr.player.data.remote.api.AsmrOneTrackNodeResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AsmrOneSiteSelectionTest {
    @Test
    fun endpointOptions_containMainSiteThreeMirrorsAndBackup() {
        assertEquals(listOf(0, 100, 200, 300, -1), AsmrOneEndpoint.options)
        assertEquals("asmr.one", AsmrOneEndpoint.displayName(0))
        assertEquals("asmr-100", AsmrOneEndpoint.displayName(100))
        assertEquals("asmr-200", AsmrOneEndpoint.displayName(200))
        assertEquals("asmr-300", AsmrOneEndpoint.displayName(300))
        assertEquals("备用", AsmrOneEndpoint.displayName(-1))
        assertEquals(null, AsmrOneEndpoint.directBaseUrl(-1))
    }

    @Test
    fun endpointNormalization_preservesEverySelectableEndpoint() {
        AsmrOneEndpoint.options.forEach { endpoint ->
            assertEquals(endpoint, AsmrOneEndpoint.normalize(endpoint))
        }
        assertEquals(200, AsmrOneEndpoint.normalize(Int.MAX_VALUE))
    }

    @Test
    fun fetchTracks_requestsOnlySelectedEndpoint() = runBlocking {
        val calls = mutableListOf<Int>()
        val tree = listOf(track("selected.wav"))

        val result = fetchAsmrOneTracksFromSelectedSite(
            preferredSite = AsmrOneEndpoint.MAIN,
            fetchSelected = { endpoint ->
                calls += endpoint
                tree
            }
        )

        assertEquals(listOf(AsmrOneEndpoint.MAIN), calls)
        assertEquals(AsmrOneEndpoint.MAIN, result.site)
        assertEquals(tree, result.tree)
    }

    @Test
    fun fetchTracks_doesNotTryAnotherEndpointWhenSelectedEndpointIsEmpty() = runBlocking {
        val calls = mutableListOf<Int>()

        val result = fetchAsmrOneTracksFromSelectedSite(
            preferredSite = AsmrOneEndpoint.MIRROR_300,
            fetchSelected = { endpoint ->
                calls += endpoint
                emptyList()
            }
        )

        assertEquals(listOf(AsmrOneEndpoint.MIRROR_300), calls)
        assertEquals(AsmrOneEndpoint.MIRROR_300, result.site)
        assertTrue(result.tree.isEmpty())
    }

    @Test
    fun fetchTracks_doesNotTryAnotherEndpointWhenSelectedEndpointFails() = runBlocking {
        val calls = mutableListOf<Int>()

        val failure = runCatching {
            fetchAsmrOneTracksFromSelectedSite(
                preferredSite = AsmrOneEndpoint.MIRROR_200,
                fetchSelected = { endpoint ->
                    calls += endpoint
                    error("selected endpoint failed")
                }
            )
        }.exceptionOrNull()

        assertEquals(listOf(AsmrOneEndpoint.MIRROR_200), calls)
        assertNotNull(failure)
    }

    private fun track(title: String): AsmrOneTrackNodeResponse {
        return AsmrOneTrackNodeResponse(
            title = title,
            mediaDownloadUrl = "https://example.com/$title"
        )
    }
}
