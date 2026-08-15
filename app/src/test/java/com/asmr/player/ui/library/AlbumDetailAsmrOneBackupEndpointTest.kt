package com.asmr.player.ui.library

import com.asmr.player.data.remote.api.AsmrOneTrackNodeResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AlbumDetailAsmrOneBackupEndpointTest {
    @Test
    fun fetchAsmrOneTracksFromBackup_returnsFirstAvailableTree() = runBlocking {
        val backupTree = listOf(
            AsmrOneTrackNodeResponse(
                title = "1.mp3",
                mediaDownloadUrl = "https://example.com/1.mp3"
            )
        )

        val result = fetchAsmrOneTracksFromBackup(
            candidateRjs = listOf("bj02370869"),
            fetchBackup = { workNo ->
                "100000062" to backupTree.also { assertEquals("BJ02370869", workNo) }
            }
        )

        assertEquals("100000062", result.first)
        assertEquals(backupTree, result.second)
    }

    @Test
    fun fetchAsmrOneTracksFromBackup_returnsEmptyWhenNoCandidateHasTree() = runBlocking {
        val requested = mutableListOf<String>()

        val result = fetchAsmrOneTracksFromBackup(
            candidateRjs = listOf("RJ01271410", "RJ01271411"),
            fetchBackup = { rj ->
                requested += rj
                if (rj == "RJ01271410") error("backup unavailable")
                "1271411" to emptyList()
            }
        )

        assertEquals(listOf("RJ01271410", "RJ01271411"), requested)
        assertEquals(null, result.first)
        assertTrue(result.second.isEmpty())
    }

    @Test
    fun fetchAsmrOneTracksFromBackup_propagatesWhenEveryRequestFails() = runBlocking {
        try {
            fetchAsmrOneTracksFromBackup(
                candidateRjs = listOf("RJ01271410", "RJ01271411"),
                throwWhenAllRequestsFail = true,
                fetchBackup = { error("backup unavailable") }
            )
            fail("Expected the final request failure to be propagated")
        } catch (error: IllegalStateException) {
            assertEquals("backup unavailable", error.message)
        }
    }

    @Test
    fun fetchAsmrOneTracksFromBackup_doesNotPropagateWhenAnyRequestCompletes() = runBlocking {
        val result = fetchAsmrOneTracksFromBackup(
            candidateRjs = listOf("RJ01271410", "RJ01271411"),
            throwWhenAllRequestsFail = true,
            fetchBackup = { rj ->
                if (rj == "RJ01271410") error("backup unavailable")
                "1271411" to emptyList()
            }
        )

        assertEquals(null, result.first)
        assertTrue(result.second.isEmpty())
    }
}
