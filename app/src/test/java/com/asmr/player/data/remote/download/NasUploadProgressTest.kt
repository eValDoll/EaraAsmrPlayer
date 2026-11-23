package com.asmr.player.data.remote.download

import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NasUploadProgressTest {
    @Test
    fun countsWrittenBytesAcrossArchiveEntriesUntilServerReturns() = runBlocking {
        var now = 0L
        val tracker = NasUploadTracker { now }
        val output = ByteArrayOutputStream()
        tracker.track("archive", 5L) { copy ->
            assertEquals(NasUploadProgress(0, 5, 0), tracker.active.value["archive"])
            copy(byteArrayOf(1).inputStream(), output)
            assertEquals(0L, tracker.active.value.getValue("archive").sent)
            now = 1_000_000_000
            copy(byteArrayOf(2, 3).inputStream(), output)
            assertEquals(NasUploadProgress(3, 5, 3), tracker.active.value["archive"])
            copy(byteArrayOf(4, 5).inputStream(), output)
            assertEquals(NasUploadProgress(5, 5, 0), tracker.active.value["archive"])
        }
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5), output.toByteArray())
        assertTrue(tracker.active.value.isEmpty())
    }

    @Test
    fun failedOrCancelledUploadsReleaseOnlyTheirOwnProgress() = runBlocking {
        val tracker = NasUploadTracker()
        val started = CompletableDeferred<Unit>()
        val pending = launch {
            tracker.track("pending", 12L) {
                started.complete(Unit)
                awaitCancellation()
            }
        }
        started.await()
        val failure = runCatching {
            tracker.track("failed", 2L) { copy ->
                copy(byteArrayOf(1, 2).inputStream(), ByteArrayOutputStream())
                throw IOException("PUT failed")
            }
        }.exceptionOrNull()
        assertTrue(failure is IOException)
        assertEquals(setOf("pending"), tracker.active.value.keys)
        pending.cancelAndJoin()
        assertTrue(tracker.active.value.isEmpty())
    }
}
