package com.asmr.player.data.remote.download

import com.asmr.player.util.ChapterMediaReference
import com.asmr.player.util.MessageManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class DownloadPreparationCoordinatorTest {
    private fun request(native: String? = null) = DownloadBatchRequest(
        "RJ01637938", "album:RJ01637938", listOf(RelativeDownloadItem(
            ChapterMediaReference("https://audio.example/work.m3u8", 0L, 239_000L, native, "01_T0.m4a").encode(),
            "japaneseasmr.com/01_T0.m4a",
        )),
    )

    @Test fun leavingTheDetailPageKeepsConfirmationAndEnqueuesOnlyAfterApproval() = runBlocking<Unit> {
        val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val pageScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val enqueued = CompletableDeferred<DownloadBatchRequest>()
        val finished = CompletableDeferred<Boolean>()
        val coordinator = DownloadPreparationCoordinator(
            JapaneseAsmrDownloadPreflight(OkHttpClient()), MessageManager(),
            { enqueued.complete(it); EnqueueDownloadBatchResult.Accepted(it.items.size) },
            applicationScope, Dispatchers.Unconfined,
        )
        try {
            pageScope.launch { coordinator.enqueue(request()) { finished.complete(it) } }.join()
            pageScope.cancel()
            withTimeout(2_000L) { coordinator.onlineAudioConfirmation.first { it != null } }
            assertEquals(listOf("01_T0.m4a"), coordinator.onlineAudioConfirmation.value)
            assertFalse(enqueued.isCompleted)
            assertFalse(finished.isCompleted)
            coordinator.confirmOnlineAudio()
            val prepared = withTimeout(2_000L) { enqueued.await() }
            assertTrue(ChapterMediaReference.parse(prepared.items.single().url)!!.useHlsDownload)
            assertTrue(withTimeout(2_000L) { finished.await() })
            withTimeout(2_000L) { coordinator.onlineAudioConfirmation.first { it == null } }
        } finally { pageScope.cancel(); applicationScope.cancel() }
    }

    @Test fun cancellingConfirmationClosesTheDialogWithoutCreatingATask() = runBlocking<Unit> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val count = AtomicInteger()
        val finished = CompletableDeferred<Boolean>()
        val coordinator = DownloadPreparationCoordinator(
            JapaneseAsmrDownloadPreflight(OkHttpClient()), MessageManager(),
            { count.incrementAndGet(); EnqueueDownloadBatchResult.Accepted(it.items.size) },
            scope, Dispatchers.Unconfined,
        )
        try {
            coordinator.enqueue(request()) { finished.complete(it) }
            withTimeout(2_000L) { coordinator.onlineAudioConfirmation.first { it != null } }
            coordinator.cancelOnlineAudio()
            assertFalse(withTimeout(2_000L) { finished.await() })
            withTimeout(2_000L) { coordinator.onlineAudioConfirmation.first { it == null } }
            assertEquals(0, count.get())
        } finally { scope.cancel() }
    }

    @Test fun aStalledCheckReportsTimeoutWithoutCreatingATask() = runBlocking<Unit> {
        val server = MockWebServer()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        server.start()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val messages = MessageManager()
        val count = AtomicInteger()
        val finished = CompletableDeferred<Boolean>()
        val coordinator = DownloadPreparationCoordinator(
            JapaneseAsmrDownloadPreflight(OkHttpClient(), 300L), messages,
            { count.incrementAndGet(); EnqueueDownloadBatchResult.Accepted(it.items.size) },
            scope, Dispatchers.Unconfined,
        )
        try {
            coordinator.enqueue(request(server.url("/track.m4a").toString())) { finished.complete(it) }
            val message = withTimeout(2_000L) { messages.messages.first { it.message.contains("超时") } }
            assertEquals("源文件检查超时，请重试", message.message)
            assertFalse(withTimeout(2_000L) { finished.await() })
            assertNull(coordinator.onlineAudioConfirmation.value)
            assertEquals(0, count.get())
        } finally { scope.cancel(); server.shutdown() }
    }

    @Test fun unavailableOrExistingTasksDoNotReportSuccessfulEnqueue() = runBlocking<Unit> {
        for (result in listOf(EnqueueDownloadBatchResult.DirectoryUnavailable, EnqueueDownloadBatchResult.TaskBlocked)) {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val finished = CompletableDeferred<Boolean>()
            val coordinator = DownloadPreparationCoordinator(
                JapaneseAsmrDownloadPreflight(OkHttpClient()), MessageManager(), { result },
                scope, Dispatchers.Unconfined,
            )
            try {
                val direct = RelativeDownloadItem("https://files.example/track.m4a", "track.m4a")
                coordinator.enqueue(request().copy(items = listOf(direct))) { finished.complete(it) }
                assertFalse(withTimeout(2_000L) { finished.await() })
            } finally { scope.cancel() }
        }
    }
}
