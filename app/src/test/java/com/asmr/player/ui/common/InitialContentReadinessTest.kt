package com.asmr.player.ui.common

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InitialContentReadinessTest {
    @Test(timeout = 10_000)
    fun defaultStateFlowReplayDoesNotCompleteReadinessButRealEmptyResultDoes() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val readiness = InitialContentReadiness()
            val started = CompletableDeferred<Unit>()
            val allowResult = CompletableDeferred<Unit>()
            val cached = readiness.track(flow {
                started.complete(Unit)
                allowResult.await()
                emit(emptyList<String>())
            }).stateIn(scope, SharingStarted.WhileSubscribed(0), emptyList())
            val waiting = async(start = CoroutineStart.UNDISPATCHED) { readiness.awaitReady() }

            assertTrue(cached.value.isEmpty())
            assertFalse(waiting.isCompleted)
            assertFalse(started.isCompleted)

            val collection = launch(start = CoroutineStart.UNDISPATCHED) { cached.collect {} }
            started.await()
            assertFalse(waiting.isCompleted)

            allowResult.complete(Unit)
            waiting.await()
            collection.cancel()
        } finally {
            scope.cancel()
        }
    }

    @Test(timeout = 10_000)
    fun waitsForEveryRegisteredSourceIncludingNullResults() = runBlocking {
        val readiness = InitialContentReadiness()
        val first = readiness.track(flowOf(emptyList<String>()))
        val second = readiness.track(flowOf<String?>(null))
        val waiting = async(start = CoroutineStart.UNDISPATCHED) { readiness.awaitReady() }

        first.first()
        assertFalse(waiting.isCompleted)
        second.first()
        waiting.await()
    }

    @Test(timeout = 10_000)
    fun registeredSourceCanWaitUntilARealPlaylistIsSelected() = runBlocking {
        val readiness = InitialContentReadiness()
        val itemsSource = readiness.registerSource()
        val waiting = async(start = CoroutineStart.UNDISPATCHED) { readiness.awaitReady() }

        flowOf(emptyList<String>()).first()
        assertFalse(waiting.isCompleted)

        itemsSource.track(flowOf(emptyList<String>())).first()
        waiting.await()
    }

    @Test(timeout = 10_000)
    fun cancellingAWaitDoesNotCancelTheSharedReadiness() = runBlocking {
        val readiness = InitialContentReadiness()
        val content = readiness.track(flowOf(1))
        val cancelled = launch(start = CoroutineStart.UNDISPATCHED) { readiness.awaitReady() }
        cancelled.cancel()
        cancelled.join()

        val resumed = async(start = CoroutineStart.UNDISPATCHED) { readiness.awaitReady() }
        assertFalse(resumed.isCompleted)
        content.first()
        resumed.await()
    }
}
