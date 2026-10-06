package com.asmr.player.subtitle

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.AppDatabaseProvider
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class SubtitleTaskServiceLifecycleTest {
    private lateinit var database: AppDatabase
    private lateinit var service: SubtitleTaskService
    private lateinit var scope: CoroutineScope
    private val inferenceStarted = CountDownLatch(1)
    private val finishInference = CountDownLatch(1)
    private val engineClosed = CountDownLatch(1)
    private val inferenceRunning = AtomicBoolean(false)
    private val releasedDuringInference = AtomicBoolean(false)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        AppDatabaseProvider::class.java.getDeclaredField("instance")
            .apply { isAccessible = true }.set(null, database)
        service = Robolectric.buildService(SubtitleTaskService::class.java).get()
        scope = field("serviceScope") as CoroutineScope
        setField("database", database)
        setField("repository", SubtitleTaskRepository.get(context))
        setField("connectivityManager", context.getSystemService(ConnectivityManager::class.java))
        setField("transcriptionEngine", object : SubtitleTranscriptionEngine {
            override val model = SubtitleTranscriptionModel(
                "test", "测试模型", "测试模型", SubtitleTranscriptionModelType.NEMO_CTC,
                emptyList(), 16_000
            )

            override fun transcribe(
                channelSamples: List<FloatArray>,
                isCancelled: () -> Boolean,
                onProgress: (Int) -> Unit
            ): List<SubtitleTranscriptionSegment> = emptyList()

            override fun close() {
                releasedDuringInference.set(inferenceRunning.get())
                engineClosed.countDown()
            }
        })
    }

    @After
    fun tearDown() = runBlocking {
        finishInference.countDown()
        scope.cancel()
        scope.coroutineContext[Job]?.join()
        database.close()
        AppDatabaseProvider::class.java.getDeclaredField("instance")
            .apply { isAccessible = true }.set(null, null)
        SubtitleTaskRepository::class.java.getDeclaredField("instance")
            .apply { isAccessible = true }.set(null, null)
    }

    @Test
    fun canceledInference_keepsExecutionOwnedUntilItActuallyReturns() = runBlocking {
        val job = startInference()
        job.cancel()
        assertFalse(job.isActive)
        assertFalse(job.isCompleted)

        val isRunning = service.javaClass.getDeclaredMethod("isJobRunning", String::class.java)
            .apply { isAccessible = true }.invoke(service, "item") as Boolean
        assertTrue(isRunning)
        invokeSuspend("scheduleTranscription")
        assertSame(job, field("transcriptionJob"))
        invokeSuspend("releaseTranscriptionEngineWhenIdle")
        invokeSuspend("stopWhenIdle")
        assertFalse(field("stoppingSafely") as Boolean)
        assertEqualsUnreleased()

        finishInference.countDown()
        job.join()
        invokeSuspend("releaseTranscriptionEngineWhenIdle")
        assertTrue(engineClosed.await(2, TimeUnit.SECONDS))
        assertFalse(releasedDuringInference.get())
    }

    @Test
    fun serviceDestroy_returnsWhileInferenceFinishesThenReleasesEngine() = runBlocking {
        val job = startInference()
        service.onDestroy()
        assertFalse(job.isActive)
        assertEqualsUnreleased()

        finishInference.countDown()
        job.join()
        assertTrue(engineClosed.await(2, TimeUnit.SECONDS))
        assertFalse(releasedDuringInference.get())
    }

    private fun startInference(): Job {
        val job = scope.launch {
            inferenceRunning.set(true)
            inferenceStarted.countDown()
            try {
                check(finishInference.await(10, TimeUnit.SECONDS))
            } finally {
                inferenceRunning.set(false)
            }
        }
        setField("transcriptionJob", job)
        setField("transcriptionItemId", "item")
        assertTrue(inferenceStarted.await(2, TimeUnit.SECONDS))
        return job
    }

    private fun assertEqualsUnreleased() {
        assertTrue("推理退出前不能释放模型", engineClosed.count == 1L)
    }

    private fun field(name: String): Any? = service.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.get(service)

    private fun setField(name: String, value: Any) {
        service.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(service, value)
    }

    private suspend fun invokeSuspend(name: String): Unit = suspendCoroutineUninterceptedOrReturn { continuation ->
        service.javaClass.getDeclaredMethod(name, Continuation::class.java)
            .apply { isAccessible = true }.invoke(service, continuation)
    }
}
