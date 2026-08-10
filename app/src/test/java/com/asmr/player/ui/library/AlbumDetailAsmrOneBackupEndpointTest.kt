package com.asmr.player.ui.library

import com.asmr.player.data.remote.api.AsmrOneTrackNodeResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumDetailAsmrOneBackupEndpointTest {
    @Test
    fun fetchAsmrOneTracksFromBackup_returnsFirstAvailableTree() = runBlocking {
        val backupTree = listOf(
            AsmrOneTrackNodeResponse(
                title = "backup.wav",
                mediaDownloadUrl = "https://example.com/backup.wav"
            )
        )

        val result = fetchAsmrOneTracksFromBackup(
            candidateRjs = listOf("rj01271410"),
            fetchBackup = { rj -> "1271410" to backupTree.also { assertEquals("RJ01271410", rj) } }
        )

        assertEquals("1271410", result.first)
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
}
