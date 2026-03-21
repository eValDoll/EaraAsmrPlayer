package com.asmr.player.ui.library

import com.asmr.player.data.remote.dlsite.DlsiteCloudSyncCandidate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CloudSyncSelectionRequestQueueTest {
    @Test
    fun enqueueAndResolve_keepsFifoOrder() = runBlocking {
        val queue = CloudSyncSelectionRequestQueue()

        val first = queue.enqueue(albumId = 1L, albumTitle = "Album A", candidates = listOf(candidate("RJ000001", "First")))
        val second = queue.enqueue(albumId = 2L, albumTitle = "Album B", candidates = listOf(candidate("RJ000002", "Second")))

        assertEquals("Album A", queue.dialogState.value?.albumTitle)
        assertEquals(1, queue.dialogState.value?.currentPosition)
        assertEquals(2, queue.dialogState.value?.totalCount)

        queue.resolveCurrent("RJ000001")

        assertEquals("RJ000001", first.await())
        assertEquals("Album B", queue.dialogState.value?.albumTitle)
        assertEquals(2, queue.dialogState.value?.currentPosition)
        assertEquals(2, queue.dialogState.value?.totalCount)

        queue.resolveCurrent(null)

        assertNull(second.await())
        assertNull(queue.dialogState.value)
    }

    @Test
    fun enqueueWhileCurrentIsVisible_doesNotInterruptCurrentRequest() {
        val queue = CloudSyncSelectionRequestQueue()

        queue.enqueue(albumId = 1L, albumTitle = "Album A", candidates = listOf(candidate("RJ000001", "First")))
        queue.enqueue(albumId = 2L, albumTitle = "Album B", candidates = listOf(candidate("RJ000002", "Second")))
        queue.enqueue(albumId = 3L, albumTitle = "Album C", candidates = listOf(candidate("RJ000003", "Third")))

        assertEquals("Album A", queue.dialogState.value?.albumTitle)
        assertEquals(1, queue.dialogState.value?.currentPosition)
        assertEquals(3, queue.dialogState.value?.totalCount)
    }

    @Test
    fun cancelCurrentAndQueuedRequests_releasesDeferredsAndAdvancesQueue() = runBlocking {
        val queue = CloudSyncSelectionRequestQueue()

        val first = queue.enqueue(albumId = 1L, albumTitle = "Album A", candidates = listOf(candidate("RJ000001", "First")))
        val second = queue.enqueue(albumId = 2L, albumTitle = "Album B", candidates = listOf(candidate("RJ000002", "Second")))
        val third = queue.enqueue(albumId = 3L, albumTitle = "Album C", candidates = listOf(candidate("RJ000003", "Third")))

        queue.cancelForAlbum(1L)

        assertNull(first.await())
        assertEquals("Album B", queue.dialogState.value?.albumTitle)
        assertEquals(2, queue.dialogState.value?.currentPosition)
        assertEquals(3, queue.dialogState.value?.totalCount)

        queue.cancelAll()

        assertNull(second.await())
        assertNull(third.await())
        assertEquals(0, queue.pendingCount())
        assertNull(queue.dialogState.value)
    }

    @Test
    fun ignoreAllRemainingInBatch_releasesCurrentQueuedAndFutureRequests() = runBlocking {
        val queue = CloudSyncSelectionRequestQueue()
        queue.beginBatchSession()

        val first = queue.enqueue(albumId = 1L, albumTitle = "Album A", candidates = listOf(candidate("RJ000001", "First")))
        val second = queue.enqueue(albumId = 2L, albumTitle = "Album B", candidates = listOf(candidate("RJ000002", "Second")))

        queue.ignoreAllRemainingInBatch()

        assertNull(first.await())
        assertNull(second.await())
        assertNull(queue.dialogState.value)
        assertEquals(0, queue.pendingCount())

        val future = queue.enqueue(albumId = 3L, albumTitle = "Album C", candidates = listOf(candidate("RJ000003", "Third")))
        assertNull(future.await())
        assertNull(queue.dialogState.value)

        queue.endBatchSession()

        queue.enqueue(albumId = 4L, albumTitle = "Album D", candidates = listOf(candidate("RJ000004", "Fourth")))
        assertEquals("Album D", queue.dialogState.value?.albumTitle)
    }

    private fun candidate(workno: String, title: String): DlsiteCloudSyncCandidate {
        return DlsiteCloudSyncCandidate(
            workno = workno,
            title = title,
            cv = "CV",
            coverUrl = ""
        )
    }
}
