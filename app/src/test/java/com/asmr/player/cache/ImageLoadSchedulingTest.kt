package com.asmr.player.cache

import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageLoadSchedulingTest {
    @Test
    fun remoteModelDetection_onlyClassifiesHttpSourcesAsRemote() {
        assertTrue(isRemoteImageModel("https://example.com/cover.jpg"))
        assertTrue(
            isRemoteImageModel(
                CacheImageModel(
                    data = "HTTP://example.com/recommendation.jpg",
                    headers = mapOf("Referer" to "https://example.com/"),
                )
            )
        )
        assertFalse(isRemoteImageModel(File("cover.jpg")))
        assertFalse(isRemoteImageModel("content://media/external/images/1"))
        assertFalse(isRemoteImageModel("/storage/emulated/0/Music/cover.jpg"))
    }

    @Test
    fun localLoad_startsWhileRemoteLaneIsOccupied() {
        runBlocking {
            val gate = ImageLoadGate(remotePermits = 1, localPermits = 1)
            val releaseFirstRemote = CompletableDeferred<Unit>()
            val firstRemoteStarted = CompletableDeferred<Unit>()
            val queuedRemoteStarted = CompletableDeferred<Unit>()

            val firstRemote = async(start = CoroutineStart.UNDISPATCHED) {
                gate.withPermit("https://example.com/first.jpg") {
                    firstRemoteStarted.complete(Unit)
                    releaseFirstRemote.await()
                }
            }
            firstRemoteStarted.await()

            val queuedRemote = async {
                gate.withPermit("https://example.com/second.jpg") {
                    queuedRemoteStarted.complete(Unit)
                }
            }
            val local = async {
                gate.withPermit(File("cover.jpg")) { Unit }
            }

            withTimeout(1_000) { local.await() }
            assertFalse(queuedRemoteStarted.isCompleted)

            releaseFirstRemote.complete(Unit)
            firstRemote.await()
            queuedRemote.await()
        }
    }
}
