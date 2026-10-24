package com.asmr.player.subtitle

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.data.local.db.entities.SubtitleTaskEntity
import com.asmr.player.data.local.db.entities.SubtitleTaskItemEntity
import com.asmr.player.data.local.db.entities.SubtitleTitleOwnerEntity
import com.asmr.player.data.local.db.entities.SubtitleTitleOwnerKind
import com.asmr.player.data.local.db.entities.TrackEntity
import com.asmr.player.data.settings.SettingsRepository
import com.google.gson.Gson
import com.google.gson.JsonParser
import java.util.concurrent.TimeUnit
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

@RunWith(RobolectricTestRunner::class)
@Config(
    application = Application::class, sdk = [34], manifest = Config.NONE,
    shadows = [SubtitleTitleTranslationIsolationTest.StoreShadow::class],
    instrumentedPackages = ["com.asmr.player.subtitle.TranslationApiStore"]
)
class SubtitleTitleTranslationIsolationTest {
    private lateinit var database: AppDatabase
    private lateinit var service: SubtitleTaskService
    private lateinit var scope: CoroutineScope
    private val gson = Gson()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        service = Robolectric.buildService(SubtitleTaskService::class.java).get()
        scope = service.javaClass.getDeclaredField("serviceScope").apply { isAccessible = true }.get(service) as CoroutineScope
        setField("database", database)
        setField("gson", gson)
        setField("deepSeekOkHttpClient", OkHttpClient())
        setField("settingsRepository", SettingsRepository(object : DataStore<Preferences> {
            override val data = flowOf(emptyPreferences())
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = transform(emptyPreferences())
        }))
    }

    @After
    fun tearDown() = runBlocking {
        scope.cancel()
        scope.coroutineContext[Job]?.join()
        database.close()
        TranslationApiStore::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, null)
    }

    @Test
    fun exhaustedFirstAlbum_doesNotPreventLaterAlbumTitleTranslation() = runBlocking {
        MockWebServer().use { server ->
            StoreShadow.configuration = TranslationApiConfig(
                TranslationProvider.CUSTOM, "local-test-key", server.url("/v1").toString(), "test-model"
            )
            val firstAlbumId = database.albumDao().insertAlbum(AlbumEntity(title = "首作品原名", path = "/first"))
            val secondAlbumId = database.albumDao().insertAlbum(AlbumEntity(title = "后作品原名", path = "/second"))
            val firstTrackId = database.trackDao().insertTrack(
                TrackEntity(albumId = firstAlbumId, title = "首音轨原名", path = "/first/audio.mp3")
            )
            val secondTrackId = database.trackDao().insertTrack(
                TrackEntity(albumId = secondAlbumId, title = "后音轨原名", path = "/second/audio.mp3")
            )
            database.subtitleTaskDao().insertTask(SubtitleTaskEntity(
                id = "task", origin = SubtitleTaskOrigin.GENERATED, title = "批量翻译", rjCode = "",
                state = SubtitleTaskState.ACTIVE, warning = "", createdAt = 1, updatedAt = 1
            ))
            database.subtitleTaskDao().insertItems(listOf(
                taskItem("first", firstTrackId, "/first/audio.mp3"),
                taskItem("second", secondTrackId, "/second/audio.mp3")
            ))
            val owners = listOf(
                SubtitleTitleOwnerEntity("task", SubtitleTitleOwnerKind.ALBUM, firstAlbumId, "", 1),
                SubtitleTitleOwnerEntity("task", SubtitleTitleOwnerKind.TRACK, firstTrackId, "", 1),
                SubtitleTitleOwnerEntity("task", SubtitleTitleOwnerKind.ALBUM, secondAlbumId, "", 1),
                SubtitleTitleOwnerEntity("task", SubtitleTitleOwnerKind.TRACK, secondTrackId, "", 1)
            )
            database.subtitleTitleOwnerDao().insertMissing(owners)
            repeat(2) { server.enqueue(chatResponse("无效标题响应")) }
            server.enqueue(chatResponse(gson.toJson(mapOf(
                "work_title" to "后作品译名",
                "tracks" to listOf(mapOf("track_id" to secondTrackId, "title" to "后音轨译名"))
            ))))

            withTimeout(10_000) { translateDisplayNames("task", owners) }

            assertEquals(3, server.requestCount)
            val requestedAlbums = (0 until 3).map {
                val request = requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))
                val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
                JsonParser.parseString(body["messages"].asJsonArray[1].asJsonObject["content"].asString)
                    .asJsonObject["work_title"].asString
            }
            assertEquals(listOf("首作品原名", "首作品原名", "后作品原名"), requestedAlbums)
            assertEquals("", database.albumDao().getAlbumById(firstAlbumId)?.displayTitle)
            assertEquals("", database.trackDao().getTrackByIdOnce(firstTrackId)?.displayTitle)
            assertEquals("后作品译名", database.albumDao().getAlbumById(secondAlbumId)?.displayTitle)
            assertEquals("后音轨译名", database.trackDao().getTrackByIdOnce(secondTrackId)?.displayTitle)
            val savedOwners = database.subtitleTitleOwnerDao().getByTask("task")
            assertEquals("后作品译名", savedOwners.single { it.kind == SubtitleTitleOwnerKind.ALBUM && it.targetId == secondAlbumId }.displayTitle)
            assertEquals("后音轨译名", savedOwners.single { it.kind == SubtitleTitleOwnerKind.TRACK && it.targetId == secondTrackId }.displayTitle)
        }
    }

    private fun taskItem(id: String, trackId: Long, path: String) = SubtitleTaskItemEntity(
        id = id, taskId = "task", trackId = trackId, trackTitle = "音轨原名", trackPath = path,
        mode = SubtitleTaskMode.GENERATED, queueSequence = trackId, state = SubtitleItemState.TRANSLATING,
        suspendedFromState = "", transcriptionChunkCursor = 0, transcriptionProgress = 0,
        transcriptionModelId = "", transcribedMs = 0, totalDurationMs = 0, translationCursor = 0,
        translationTotal = 0, translationBatchIndex = 0, translationBatchTotal = 0, attempt = 0,
        nextAttemptAt = 0, errorMessage = "", originalHash = "", lastPublishedHash = "", createdAt = 1, updatedAt = 1
    )

    private fun chatResponse(content: String) = MockResponse().setBody(gson.toJson(mapOf(
        "choices" to listOf(mapOf("finish_reason" to "stop", "message" to mapOf("role" to "assistant", "content" to content)))
    )))

    private fun setField(name: String, value: Any) {
        service.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(service, value)
    }

    private suspend fun translateDisplayNames(taskId: String, owners: List<SubtitleTitleOwnerEntity>): Unit =
        suspendCoroutineUninterceptedOrReturn { continuation ->
            service.javaClass.getDeclaredMethod(
                "translateDisplayNamesForTaskLocked", String::class.java, List::class.java, Continuation::class.java
            ).apply { isAccessible = true }.invoke(service, taskId, owners, continuation)
        }

    @Implements(value = TranslationApiStore::class, isInAndroidSdk = false)
    internal class StoreShadow {
        @Implementation
        protected fun readConfiguration(): TranslationApiConfig = configuration

        companion object {
            lateinit var configuration: TranslationApiConfig
        }
    }
}
