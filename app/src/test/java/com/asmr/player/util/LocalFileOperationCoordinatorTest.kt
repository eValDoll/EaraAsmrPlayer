package com.asmr.player.util

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class LocalFileOperationCoordinatorTest {
    @Test
    fun unrelatedAlbumsCanRunWhileConflictingAlbumWaits() = runBlocking {
        withTimeout(2_000) {
            val coordinator = LocalFileOperationCoordinator()
            val first = LocalFileScope(setOf("work:RJ111111"), setOf("/albums/RJ111111"))
            val second = LocalFileScope(setOf("work:RJ222222"), setOf("/albums/RJ222222"))
            val downloading = checkNotNull(coordinator.tryAcquire(LocalFileOperation.DOWNLOAD, scope = first))
            val waiting = launch(start = CoroutineStart.UNDISPATCHED) {
                coordinator.withOperation(LocalFileOperation.SUBTITLE, scope = first) { }
            }
            val independent = checkNotNull(coordinator.tryAcquire(LocalFileOperation.SUBTITLE, scope = second))
            assertNull(coordinator.tryAcquire(LocalFileOperation.DELETE, scope = first))
            assertNull(coordinator.tryAcquire(LocalFileOperation.CHANGE_DIRECTORY))
            independent.close()
            downloading.close()
            waiting.join()
            checkNotNull(coordinator.tryAcquire(LocalFileOperation.DELETE, scope = first)).close()
        }
    }

    @Test
    fun overlappingPathsConflictEvenWhenAlbumIdsDiffer() {
        val coordinator = LocalFileOperationCoordinator()
        val root = LocalFileScope(setOf("album:1"), setOf("/music/work"))
        val child = LocalFileScope(setOf("album:2"), setOf("/music/work/disc1"))
        val neighbour = LocalFileScope(setOf("album:3"), setOf("/music/work2"))
        val download = checkNotNull(coordinator.tryAcquire(LocalFileOperation.DOWNLOAD, scope = root))
        assertNull(coordinator.tryAcquire(LocalFileOperation.SUBTITLE, scope = child))
        checkNotNull(coordinator.tryAcquire(LocalFileOperation.SUBTITLE, scope = neighbour)).close()
        download.close()
    }

    @Test
    fun sameFileDownloadsAreSerializedWhileOtherFilesCanRun() {
        val coordinator = LocalFileOperationCoordinator()
        val first = checkNotNull(coordinator.tryAcquire(LocalFileOperation.DOWNLOAD, "/album/a.wav"))
        assertNull(coordinator.tryAcquire(LocalFileOperation.DOWNLOAD, "/album/a.wav"))
        val other = checkNotNull(coordinator.tryAcquire(LocalFileOperation.DOWNLOAD, "/album/b.wav"))
        first.close()
        other.close()
    }

    @Test
    fun incompatibleOperationsCannotOverlap() {
        LocalFileOperation.entries.forEach { first ->
            LocalFileOperation.entries.forEach { second ->
                val coordinator = LocalFileOperationCoordinator()
                val owner = checkNotNull(coordinator.tryAcquire(first))
                val other = coordinator.tryAcquire(second)
                assertEquals("$first / $second", first == second && first.parallel, other != null)
                other?.close()
                owner.close()
                checkNotNull(coordinator.tryAcquire(second)).close()
            }
        }
    }

    @Test
    fun waitingExclusiveOperationIsNotStarvedByNewDownloads() = runBlocking {
        withTimeout(2_000) {
            val coordinator = LocalFileOperationCoordinator()
            val first = checkNotNull(coordinator.tryAcquire(LocalFileOperation.DOWNLOAD))
            val second = checkNotNull(coordinator.tryAcquire(LocalFileOperation.DOWNLOAD))
            val entered = CompletableDeferred<Unit>()
            val finish = CompletableDeferred<Unit>()
            val exclusive = launch(start = CoroutineStart.UNDISPATCHED) {
                coordinator.withOperation(LocalFileOperation.LIBRARY) {
                    entered.complete(Unit)
                    finish.await()
                }
            }
            assertNull(coordinator.tryAcquire(LocalFileOperation.DOWNLOAD))
            first.close()
            yield()
            assertFalse(entered.isCompleted)
            second.close()
            entered.await()
            assertNull(coordinator.tryAcquire(LocalFileOperation.SUBTITLE))
            finish.complete(Unit)
            exclusive.join()
            checkNotNull(coordinator.tryAcquire(LocalFileOperation.DOWNLOAD)).close()
        }
    }

    @Test
    fun cancelledWaiterDoesNotLeakLeaseBeforeOrAfterGrant() = runBlocking {
        withTimeout(2_000) {
            repeat(2) { grantBeforeCancel ->
                val coordinator = LocalFileOperationCoordinator()
                val owner = checkNotNull(coordinator.tryAcquire(LocalFileOperation.LIBRARY))
                val waiter = launch(start = CoroutineStart.UNDISPATCHED) {
                    coordinator.withOperation(LocalFileOperation.SUBTITLE) { error("取消后不应执行") }
                }
                if (grantBeforeCancel == 1) owner.close()
                waiter.cancelAndJoin()
                owner.close()
                checkNotNull(coordinator.tryAcquire(LocalFileOperation.DELETE)).close()
            }
        }
    }

    @Test
    fun nestedWorkAndFailureReleaseOnlyTheirOwnLease() = runBlocking {
        withTimeout(2_000) {
            val coordinator = LocalFileOperationCoordinator()
            try {
                coordinator.withOperation(LocalFileOperation.SUBTITLE) {
                    coordinator.withOperation(LocalFileOperation.SUBTITLE) {
                        assertNull(coordinator.tryAcquire(LocalFileOperation.DELETE))
                        throw IllegalStateException("任务失败")
                    }
                }
            } catch (_: IllegalStateException) {
                val old = checkNotNull(coordinator.tryAcquire(LocalFileOperation.DELETE))
                old.close()
                val current = checkNotNull(coordinator.tryAcquire(LocalFileOperation.LIBRARY))
                old.close()
                assertNull(coordinator.tryAcquire(LocalFileOperation.DELETE))
                current.close()
            }
        }
    }

    @Test
    fun cancellationKeepsLeaseUntilCleanupCompletes() = runBlocking {
        withTimeout(2_000) {
            val coordinator = LocalFileOperationCoordinator()
            val entered = CompletableDeferred<Unit>()
            val owner = launch(start = CoroutineStart.UNDISPATCHED) {
                coordinator.withOperation(LocalFileOperation.DOWNLOAD) {
                    try {
                        entered.complete(Unit)
                        CompletableDeferred<Unit>().await()
                    } finally {
                        assertNull(coordinator.tryAcquire(LocalFileOperation.DELETE))
                    }
                }
            }
            entered.await()
            val next = async(start = CoroutineStart.UNDISPATCHED) {
                coordinator.withOperation(LocalFileOperation.DELETE) { true }
            }
            owner.cancelAndJoin()
            assertTrue(next.await())
        }
    }
}
