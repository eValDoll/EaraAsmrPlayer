package com.asmr.player.data.repository

import androidx.room.Room
import com.asmr.player.LocalizedRobolectricTest
import com.asmr.player.data.local.db.AsmrOneCloudDatabase
import com.asmr.player.data.remote.api.*
import com.asmr.player.data.remote.auth.AsmrOneCredentialStorage
import com.asmr.player.data.remote.auth.AsmrOneCredentials
import com.asmr.player.work.AsmrOneSyncScheduling
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class AsmrOneAccountRepositoryTest : LocalizedRobolectricTest() {
    private lateinit var database: AsmrOneCloudDatabase
    private lateinit var server: MockWebServer
    private lateinit var repository: AsmrOneAccountRepository
    private val gson = Gson()
    private val member = AtomicBoolean(false)
    private val addCalls = AtomicInteger()
    private val removeCalls = AtomicInteger()
    private val reviewWrites = AtomicInteger()
    private var memberStatus = 200
    private var anonymous = false
    private var failLists = false
    private var wrongWorkPage = false
    private var loginName = "alice"
    private var currentReview = AsmrCloudWork(42, "作品", "RJ000042", progress = "marked",
        userRating = JsonPrimitive(4), review_text = "保留评价")
    private val credentials = MemoryCredentials()
    private val scheduling = object : AsmrOneSyncScheduling {
        override fun enqueue(account: String) = Unit
        override fun cancel(account: String) = Unit
    }
    private val playlist = AsmrCloudPlaylist("list-id", "列表", "alice", privacy = 1, description = "保留描述")

    @Before fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AsmrOneCloudDatabase::class.java).build()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = respond(request)
        }
        server.start()
        repository = newRepository()
        withTimeout(5_000) { repository.account.first { it.ready } }
        Unit
    }
    @After fun tearDown() { database.close(); server.shutdown() }

    private fun newRepository(): AsmrOneAccountRepository {
        val api = Retrofit.Builder().baseUrl(server.url("/api/"))
            .addConverterFactory(GsonConverterFactory.create(gson)).build().create(AsmrOneAccountApi::class.java)
        return AsmrOneAccountRepository(database, credentials, AsmrOneAccountApiProvider { api }, gson, scheduling)
    }

    private fun respond(request: RecordedRequest): MockResponse {
        val path = request.requestUrl!!.encodedPath
        return when (path) {
            "/api/auth/me" -> if (request.method == "POST") {
                loginName = JsonParser.parseString(request.body.readUtf8()).asJsonObject["name"].asString
                json("""{"user":{"loggedIn":true,"name":"$loginName"},"token":"token-$loginName"}""")
            } else if (anonymous) json("""{"user":{"loggedIn":false}}""")
            else json("""{"user":{"loggedIn":true,"name":"${request.getHeader("Authorization")!!.substringAfter("token-")}"}}""")
            "/api/playlist/get-playlists" -> if (failLists) MockResponse().setResponseCode(503)
                else json(gson.toJson(AsmrPlaylistPage(listOf(playlist), AsmrCloudPagination(page = 1, pageSize = 20, totalCount = 1))))
            "/api/playlist/get-work-exist-status-in-my-playlists" -> if (memberStatus != 200) MockResponse().setResponseCode(memberStatus)
                else json(gson.toJson(AsmrPlaylistPage(listOf(playlist.copy(exist = member.get())), AsmrCloudPagination(page = 1, pageSize = 20, totalCount = 1))))
            "/api/playlist/get-playlist-works", "/api/review" -> {
                if (request.method == "PUT") { reviewWrites.incrementAndGet(); json("{}") }
                else json(gson.toJson(AsmrWorkPage(listOf(currentReview), AsmrCloudPagination(
                    page = if (path.endsWith("review")) 0 else if (wrongWorkPage) 7 else 1,
                    currentPage = if (path.endsWith("review")) 1 else 0, pageSize = 20, totalCount = 1))))
            }
            "/api/playlist/add-works-to-playlist" -> { addCalls.incrementAndGet(); member.set(true); json("{}") }
            "/api/playlist/remove-works-from-playlist" -> { removeCalls.incrementAndGet(); member.set(false); json("{}") }
            "/api/work/42" -> json(gson.toJson(currentReview))
            "/api/search/RJ000042" -> json(gson.toJson(AsmrWorkPage(listOf(currentReview),
                AsmrCloudPagination(page = 1, pageSize = 20, totalCount = 1))))
            "/api/playlist/get-playlist-metadata" -> json(gson.toJson(playlist))
            "/api/playlist/edit-playlist-metadata" -> json("{}")
            else -> MockResponse().setResponseCode(404)
        }
    }

    @Test fun anonymousHttp200ExpiresSessionAndKeepsCachedPlaylists() = runBlocking {
        repository.refreshPlaylists()
        anonymous = true
        expectFailure<AsmrLoginRequired> { repository.refreshPlaylists() }
        assertTrue(repository.account.value.expired)
        assertEquals("列表", repository.playlists.first().items.single().name)
    }

    @Test fun failedRefreshDoesNotClearCachedPlaylistsOrPages() = runBlocking {
        repository.refreshPlaylists()
        repository.loadPage("list-id", 1)
        failLists = true
        expectFailure<retrofit2.HttpException> { repository.refreshPlaylists() }
        wrongWorkPage = true
        expectFailure<IllegalStateException> { repository.loadPage("list-id", 1) }
        assertEquals(1, repository.playlists.first().items.size)
        assertEquals(42L, repository.content("list-id").first().rows.single().work.id)
    }

    @Test fun legacyCurrentPageAndNewPageBothLoadRealRepository() = runBlocking {
        repository.loadPage("list-id", 1)
        repository.loadPage("review:all", 1)
        assertEquals("想听", com.asmr.player.i18n.appString(com.asmr.player.R.string.cloud_progress_marked))
        assertEquals(listOf(42L), repository.content("review:all").first().rows.map { it.work.id })
    }

    @Test fun persistedOperationIsReconciledWithoutRepeatingAlreadyCommittedWrite() = runBlocking {
        repository.loadPage("list-id", 1)
        repository.enqueueMembership(playlist, currentReview, true)
        member.set(true) // The previous process lost the successful response.
        val restarted = newRepository()
        withTimeout(5_000) { restarted.account.first { it.ready } }
        assertTrue(restarted.drain("alice"))
        assertEquals(0, addCalls.get())
        assertTrue(database.cloudDao().pending("alice").isEmpty())
        assertEquals(42L, restarted.content("list-id").first().rows.single().work.id)
    }

    @Test fun latestOfflineIntentWinsAndOtherAccountNeverDrainsIt() = runBlocking {
        repository.enqueueMembership(playlist, currentReview, true)
        repository.enqueueMembership(playlist, currentReview, false)
        assertEquals(1, database.cloudDao().pending("alice").size)
        assertFalse(database.cloudDao().pending("alice").single().desired)
        repository.logout()
        repository.login("bob", "password")
        assertTrue(repository.drain("alice"))
        assertEquals(1, database.cloudDao().pending("alice").size)
        assertEquals("bob", repository.playlists.first().account)
        assertTrue(repository.playlists.first().items.isEmpty())
        assertEquals(0, addCalls.get() + removeCalls.get())
    }

    @Test fun olderAcknowledgementCannotDeleteNewerMembershipIntent() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.requestUrl!!.encodedPath.endsWith("add-works-to-playlist")) {
                    started.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                }
                return respond(request)
            }
        }
        repository.enqueueMembership(playlist, currentReview, true)
        val drain = async(Dispatchers.IO) { repository.drain("alice") }
        assertTrue(withContext(Dispatchers.IO) { started.await(5, TimeUnit.SECONDS) })
        repository.enqueueMembership(playlist, currentReview, false)
        release.countDown()
        assertFalse(drain.await())
        assertFalse(database.cloudDao().pending("alice").single().desired)
        assertTrue(repository.drain("alice"))
        assertEquals(1, addCalls.get())
        assertEquals(1, removeCalls.get())
        assertFalse(member.get())
    }

    @Test fun permissionFailureBlocksOnlyOperationAndDoesNotLogOut() = runBlocking {
        repository.enqueueMembership(playlist, currentReview, true)
        memberStatus = 403
        assertTrue(repository.drain("alice"))
        assertTrue(repository.account.value.authenticated)
        assertTrue(database.cloudDao().operations("alice").first().single().blocked)
        memberStatus = 200
        repository.retryPending()
        assertTrue(repository.drain("alice"))
        assertTrue(member.get())
    }

    @Test fun latePageResponseCannotPopulateAnotherAccountsCache() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.requestUrl!!.encodedPath.endsWith("get-playlist-works")) {
                    started.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                }
                return respond(request)
            }
        }
        val request = async(Dispatchers.IO) {
            try { repository.loadPage("list-id", 1); false } catch (_: CancellationException) { true }
        }
        assertTrue(withContext(Dispatchers.IO) { started.await(5, TimeUnit.SECONDS) })
        repository.logout()
        repository.login("bob", "password")
        release.countDown()
        assertTrue(request.await())
        assertTrue(database.cloudDao().pagesOnce("alice", "list-id").isEmpty())
        assertTrue(database.cloudDao().pagesOnce("bob", "list-id").isEmpty())
    }

    @Test fun unauthorizedPausesOutboxWithoutDiscardingIntent() = runBlocking {
        repository.enqueueMembership(playlist, currentReview, true)
        memberStatus = 401
        assertTrue(repository.drain("alice"))
        assertTrue(repository.account.value.expired)
        assertEquals(1, database.cloudDao().pending("alice").size)
    }

    @Test fun reviewStateChangePreservesReviewAndRejectsConcurrentEdit() = runBlocking {
        repository.setProgress(currentReview, "listened")
        var written: RecordedRequest? = null
        repeat(server.requestCount) { server.takeRequest().let { if (it.method == "PUT") written = it } }
        val body = JsonParser.parseString(written!!.body.readUtf8()).asJsonObject
        assertEquals(4, body["rating"].asInt)
        assertEquals("保留评价", body["review_text"].asString)
        assertEquals("listened", body["progress"].asString)
        val old = currentReview
        currentReview = currentReview.copy(review_text = "网页刚修改的评价")
        expectFailure<IllegalStateException> { repository.setProgress(old, "listening") }
        assertEquals(1, reviewWrites.get())
    }

    @Test fun loginDoesNotSendPreviousAuthorizationAndPasswordIsNotStored() = runBlocking {
        repository.login("bob", "temporary-password")
        val login = server.takeRequest()
        assertEquals("POST", login.method)
        assertNull(login.getHeader("Authorization"))
        assertEquals("bob", credentials.value!!.name)
        assertEquals("token-bob", credentials.value!!.token)
        assertFalse(gson.toJson(credentials.value).contains("temporary-password"))
    }

    @Test fun systemAndOtherUsersPlaylistsCannotBeDeleted() = runBlocking {
        expectFailure<IllegalArgumentException> { repository.deletePlaylist(playlist.copy(name = AsmrCloudPlaylist.LIKED)) }
        expectFailure<IllegalArgumentException> { repository.deletePlaylist(playlist.copy(user_name = "bob")) }
        assertEquals(0, server.requestCount)
    }

    @Test fun localAlbumWithoutNumericIdResolvesExactSiteWorkBeforeChangingMembership() = runBlocking {
        val local = AsmrCloudWork(title = "本地作品", source_id = "RJ000042")
        val resolved = repository.resolveMembershipWork(local)
        assertEquals(42L, resolved.id)
        repository.enqueueMembership(playlist, resolved, true)
        assertTrue(repository.drain("alice"))
        assertEquals(1, addCalls.get())
    }

    private suspend inline fun <reified T : Throwable> expectFailure(block: () -> Unit) {
        try { block(); fail("Expected ${T::class.java.simpleName}") }
        catch (error: Throwable) { if (error !is T) throw error }
    }
    private fun json(value: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(value)
    private class MemoryCredentials : AsmrOneCredentialStorage {
        @Volatile var value: AsmrOneCredentials? = AsmrOneCredentials("alice", "token-alice")
        override suspend fun read() = value
        override suspend fun save(credentials: AsmrOneCredentials) { value = credentials }
        override suspend fun clear() { value = null }
    }
}
