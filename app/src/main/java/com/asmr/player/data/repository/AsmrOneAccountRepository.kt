package com.asmr.player.data.repository

import androidx.room.withTransaction
import com.asmr.player.data.local.db.AsmrMembershipOperation
import com.asmr.player.data.local.db.AsmrOneCloudDatabase
import com.asmr.player.data.local.db.AsmrPlaylistCache
import com.asmr.player.data.local.db.AsmrWorkPageCache
import com.asmr.player.data.remote.api.*
import com.asmr.player.data.remote.auth.AsmrOneCredentialStorage
import com.asmr.player.data.remote.auth.AsmrOneCredentials
import com.asmr.player.domain.model.Album
import com.asmr.player.work.AsmrOneSyncScheduling
import com.google.gson.Gson
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.HttpException

data class AsmrAccountState(val ready: Boolean = false, val name: String = "", val expired: Boolean = false) {
    val authenticated: Boolean get() = name.isNotBlank() && !expired
}
data class AsmrCloudWorkRow(val work: AsmrCloudWork, val album: Album)
data class AsmrCloudPlaylists(val account: String = "", val items: List<AsmrCloudPlaylist> = emptyList())
data class AsmrCloudContent(val account: String = "", val listId: String = "", val rows: List<AsmrCloudWorkRow> = emptyList(),
    val nextPage: Int = 1, val hasMore: Boolean = false)
class AsmrLoginRequired : IllegalStateException()
class AsmrCredentialsRejected : IllegalArgumentException()
class AsmrWriteUncertain : IOException()
class AsmrWorkNotFound : IllegalArgumentException()

enum class AsmrMembershipSaveResult { SYNCED, QUEUED, BLOCKED }
data class AsmrGroupSyncResult(val queued: Int, val skipped: Int)

internal fun mergeCloudWorks(remote: List<AsmrCloudWork>, operations: List<Pair<AsmrCloudWork, Boolean>>): List<AsmrCloudWork> {
    val result = LinkedHashMap<Long, AsmrCloudWork>()
    remote.forEach { result.putIfAbsent(it.id, it) }
    val additions = linkedMapOf<Long, AsmrCloudWork>()
    operations.forEach { (work, desired) ->
        if (!desired) { result.remove(work.id); additions.remove(work.id) }
        else if (work.id !in result) additions[work.id] = work
    }
    return additions.values.toList() + result.values
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Singleton
class AsmrOneAccountRepository @Inject constructor(
    private val database: AsmrOneCloudDatabase,
    private val auth: AsmrOneCredentialStorage,
    private val provider: AsmrOneAccountApiProvider,
    private val gson: Gson,
    private val scheduling: AsmrOneSyncScheduling
) {
    private val dao = database.cloudDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initialized = CompletableDeferred<Unit>()
    private val authMutex = Mutex()
    private val syncMutex = Mutex()
    private val playlistMutex = Mutex()
    @Volatile private var session: AsmrOneCredentials? = null
    private val _account = MutableStateFlow(AsmrAccountState())
    val account: StateFlow<AsmrAccountState> = _account.asStateFlow()

    val playlists: Flow<AsmrCloudPlaylists> = account.flatMapLatest { state ->
        if (state.name.isBlank()) flowOf(AsmrCloudPlaylists())
        else dao.playlists(state.name).map { rows -> AsmrCloudPlaylists(state.name, rows.map { gson.fromJson(it.payload, AsmrCloudPlaylist::class.java) }) }
    }.flowOn(Dispatchers.Default)

    val operations: Flow<List<AsmrMembershipOperation>> = account.flatMapLatest { state ->
        if (state.name.isBlank()) flowOf(emptyList()) else dao.operations(state.name)
    }

    init {
        scope.launch {
            authMutex.withLock {
                try {
                    session = auth.read()
                } catch (_: Exception) {
                    // A storage failure cannot prevent opening the local library.
                } finally {
                    _account.value = AsmrAccountState(ready = true, name = session?.name.orEmpty())
                    initialized.complete(Unit)
                }
            }
            session?.let { scheduling.enqueue(it.name) }
        }
    }

    suspend fun login(name: String, password: String) = withContext(Dispatchers.IO) {
        initialized.await()
        authMutex.withLock {
            val api = provider.api()
            val response = try { api.login(AsmrLoginRequest(name.trim(), password)) }
                catch (error: HttpException) {
                    if (error.code() in listOf(400, 401)) throw AsmrCredentialsRejected()
                    throw error
                }
            val token = response.token?.takeIf { it.isNotBlank() } ?: throw AsmrLoginRequired()
            val user = api.me("Bearer $token").user ?: throw AsmrLoginRequired()
            if (!user.loggedIn || user.name.isNullOrBlank() || user.name != response.user?.name) throw AsmrLoginRequired()
            val next = AsmrOneCredentials(user.name, token)
            auth.save(next)
            session = next
            _account.value = AsmrAccountState(ready = true, name = next.name)
            scheduling.enqueue(next.name)
        }
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        initialized.await()
        authMutex.withLock {
            val old = session
            session = null
            _account.value = AsmrAccountState(ready = true)
            auth.clear()
            old?.let { scheduling.cancel(it.name) }
        }
    }

    fun content(listId: String): Flow<AsmrCloudContent> = account.flatMapLatest { state ->
        if (state.name.isBlank()) flowOf(AsmrCloudContent())
        else combine(dao.pages(state.name, listId), dao.operations(state.name)) { pages, pending ->
            val decoded = pages.map { gson.fromJson(it.payload, AsmrWorkPage::class.java) }
            val remote = decoded.flatMap { it.works.orEmpty() }
            val local = pending.filter { it.listId == listId }.map {
                gson.fromJson(it.workPayload, AsmrCloudWork::class.java) to it.desired
            }
            val last = decoded.lastOrNull()?.pagination
            val lastPage = pages.lastOrNull()?.page ?: 0
            AsmrCloudContent(
                account = state.name, listId = listId,
                rows = mergeCloudWorks(remote, local).map { AsmrCloudWorkRow(it, it.toAlbum()) },
                nextPage = lastPage + 1,
                hasMore = last != null && lastPage.toLong() * last.pageSize < last.totalCount
            )
        }
    }.flowOn(Dispatchers.Default)

    suspend fun refreshPlaylists() = withContext(Dispatchers.IO) {
        playlistMutex.withLock {
            val current = capture()
            val api = provider.api()
            request(current) {
                val user = api.me(bearer(current)).user
                if (user?.loggedIn != true || user.name != current.name) throw AsmrLoginRequired()
            }
            val values = mutableListOf<AsmrCloudPlaylist>()
            var page = 1
            var expectedTotal: Int? = null
            do {
                val result = request(current) { api.playlists(bearer(current), page) }
                val pagination = checkNotNull(result.pagination).apply { validate(page) }
                check(expectedTotal == null || pagination.totalCount == expectedTotal)
                expectedTotal = pagination.totalCount
                val batch = checkNotNull(result.playlists)
                check(batch.all { it.id.isNotBlank() } && batch.size <= pagination.pageSize)
                values += batch
                if (page.toLong() * pagination.pageSize >= pagination.totalCount) break
                check(batch.isNotEmpty() && page < 1000)
                page++
            } while (true)
            check(values.map { it.id }.distinct().size == values.size)
            check(values.size == expectedTotal)
            checkCurrent(current)
            database.withTransaction {
                checkCurrent(current)
                dao.clearPlaylists(current.name)
                dao.putPlaylists(values.mapIndexed { index, value -> AsmrPlaylistCache(current.name, value.id, gson.toJson(value), index) })
            }
        }
    }

    suspend fun loadPage(listId: String, page: Int) = withContext(Dispatchers.IO) {
        require(page > 0)
        val current = capture()
        val api = provider.api()
        val response = request(current) {
            if (listId.startsWith(REVIEW_PREFIX)) api.reviews(bearer(current), page,
                listId.removePrefix(REVIEW_PREFIX).takeUnless { it == "all" })
            else api.works(bearer(current), listId, page)
        }
        val pagination = checkNotNull(response.pagination).apply { validate(page) }
        checkNotNull(response.works).let { works ->
            check(works.all { it.id > 0 } && works.size <= pagination.pageSize)
        }
        database.withTransaction {
            checkCurrent(current)
            if (page == 1) dao.clearPages(current.name, listId)
            dao.putPage(AsmrWorkPageCache(current.name, listId, page, gson.toJson(response)))
        }
    }

    suspend fun memberships(workId: Long): List<AsmrCloudPlaylist> = withContext(Dispatchers.IO) {
        val current = capture()
        val remote = try { membershipRemote(current, provider.api(), workId) }
            catch (error: IOException) {
                val cached = dao.playlists(current.name).first().map { gson.fromJson(it.payload, AsmrCloudPlaylist::class.java) }
                    .filter { it.user_name == current.name }
                if (cached.isEmpty()) throw error
                cached.map { playlist ->
                    val pages = dao.pagesOnce(current.name, playlist.id).map { gson.fromJson(it.payload, AsmrWorkPage::class.java) }
                    val present = pages.any { page -> page.works.orEmpty().any { it.id == workId } }
                    val last = pages.lastOrNull()?.pagination
                    val complete = last != null && pages.size.toLong() * last.pageSize >= last.totalCount
                    playlist.copy(exist = if (present) true else if (complete) false else null)
                }
            }
        val pending = dao.operations(current.name).first().filter { it.workId == workId && !it.blocked }.associateBy { it.listId }
        remote.map { it.copy(exist = pending[it.id]?.desired ?: it.exist) }
    }

    suspend fun resolveMembershipWork(work: AsmrCloudWork): AsmrCloudWork = withContext(Dispatchers.IO) {
        if (work.id > 0) return@withContext work
        val current = capture()
        val workNo = work.source_id.orEmpty().trim().uppercase(java.util.Locale.ROOT)
        if (!Regex("RJ[0-9]{6,}").matches(workNo)) throw AsmrWorkNotFound()
        val response = request(current) { provider.api().searchWork(bearer(current), workNo) }
        val matches = response.works.orEmpty().filter {
            it.id > 0 && it.source_id.orEmpty().uppercase(java.util.Locale.ROOT).removePrefix("RJ").trimStart('0') == workNo.removePrefix("RJ").trimStart('0')
        }
        if (matches.size != 1) throw AsmrWorkNotFound()
        matches.single()
    }

    suspend fun enqueueMembership(playlist: AsmrCloudPlaylist, work: AsmrCloudWork, desired: Boolean) = withContext(Dispatchers.IO) {
        val current = capture()
        require(work.id > 0 && playlist.id.isNotBlank() && playlist.user_name == current.name)
        val operation = AsmrMembershipOperation(current.name, playlist.id, work.id, desired,
            gson.toJson(work), UUID.randomUUID().toString(), System.currentTimeMillis())
        database.withTransaction {
            checkCurrent(current)
            dao.putOperation(operation)
        }
        scheduling.enqueue(current.name)
        operation
    }

    suspend fun createPlaylist(name: String, privacy: Int) = withContext(Dispatchers.IO) {
        require(name.isNotBlank() && name.length <= 100 && privacy in 0..2 && !name.startsWith("__SYS_PLAYLIST_"))
        val current = capture()
        refreshPlaylists()
        checkCurrent(current)
        val previousIds = dao.playlists(current.name).first().mapTo(hashSetOf()) { it.id }
        try {
            request(current) { checkMutation(provider.api().create(bearer(current), AsmrCreatePlaylist(name.trim(), privacy,
                com.asmr.player.i18n.AppLanguage.selected().ifBlank { "en" }))) }
        } catch (error: IOException) {
            throw AsmrWriteUncertain()
        } catch (error: HttpException) {
            if (error.code() >= 500) throw AsmrWriteUncertain()
            throw error
        }
        try {
            refreshPlaylists()
            checkCurrent(current)
            dao.playlists(current.name).first().map { gson.fromJson(it.payload, AsmrCloudPlaylist::class.java) }
                .singleOrNull { it.id !in previousIds && it.user_name == current.name && it.name == name.trim() }
                ?: throw AsmrWriteUncertain()
        } catch (error: CancellationException) {
            throw error
        } catch (error: AsmrLoginRequired) {
            throw error
        } catch (_: Exception) {
            throw AsmrWriteUncertain()
        }
    }

    suspend fun syncMembership(operation: AsmrMembershipOperation): AsmrMembershipSaveResult = withContext(Dispatchers.IO) {
        if (!syncMutex.tryLock()) return@withContext AsmrMembershipSaveResult.QUEUED
        try {
            val current = capture()
            if (current.name != operation.account) throw CancellationException("ASMR.one account changed")
            val pending = dao.operations(current.name).first()
                .firstOrNull { it.listId == operation.listId && it.workId == operation.workId }
                ?: return@withContext AsmrMembershipSaveResult.SYNCED
            if (pending.version != operation.version) return@withContext AsmrMembershipSaveResult.QUEUED
            if (pending.blocked) return@withContext AsmrMembershipSaveResult.BLOCKED
            reconcileOperation(current, provider.api(), pending)
        } finally {
            syncMutex.unlock()
        }
    }

    suspend fun syncGroupWorks(name: String, works: List<AsmrCloudWork>, target: AsmrCloudPlaylist?): AsmrGroupSyncResult =
        withContext(Dispatchers.IO) {
            val current = capture()
            if (target != null) require(target.user_name == current.name && target.id.isNotBlank())
            val resolved = linkedMapOf<Long, AsmrCloudWork>()
            val seen = hashSetOf<String>()
            var skipped = 0
            for (work in works) {
                checkCurrent(current)
                val sourceId = work.source_id.orEmpty().trim().uppercase(java.util.Locale.ROOT)
                val identity = if (work.id > 0) "id:${work.id}" else if (Regex("RJ[0-9]{6,}").matches(sourceId))
                    "rj:${sourceId.removePrefix("RJ").trimStart('0')}" else null
                if (identity != null && !seen.add(identity)) continue
                try {
                    val value = resolveMembershipWork(work)
                    checkCurrent(current)
                    resolved[value.id] = value
                } catch (_: AsmrWorkNotFound) {
                    skipped++
                }
            }
            if (resolved.isEmpty()) return@withContext AsmrGroupSyncResult(0, skipped)
            val destination = if (target == null) createPlaylist(name, 0) else {
                request(current) { provider.api().metadata(bearer(current), target.id) }.also {
                    require(it.id == target.id && it.user_name == current.name)
                }
            }
            checkCurrent(current)
            database.withTransaction {
                checkCurrent(current)
                dao.putOperations(resolved.values.map { work ->
                    AsmrMembershipOperation(current.name, destination.id, work.id, true, gson.toJson(work),
                        UUID.randomUUID().toString(), System.currentTimeMillis())
                })
            }
            scheduling.enqueue(current.name)
            AsmrGroupSyncResult(resolved.size, skipped)
        }

    suspend fun renamePlaylist(playlist: AsmrCloudPlaylist, name: String) = withContext(Dispatchers.IO) {
        require(name.isNotBlank() && name.length <= 100 && !name.startsWith("__SYS_PLAYLIST_"))
        val current = capture()
        val api = provider.api()
        val latest = request(current) { api.metadata(bearer(current), playlist.id) }
        require(latest.user_name == current.name && !latest.isSystem)
        check(latest.name == playlist.name) { "Playlist changed remotely" }
        request(current) { checkMutation(api.edit(bearer(current), AsmrEditPlaylist(latest.id,
            AsmrPlaylistMetadata(name.trim(), latest.privacy, latest.description.orEmpty())))) }
        refreshPlaylists()
    }

    suspend fun deletePlaylist(playlist: AsmrCloudPlaylist) = withContext(Dispatchers.IO) {
        val current = capture()
        require(playlist.user_name == current.name && !playlist.isSystem)
        request(current) { checkMutation(provider.api().delete(bearer(current), AsmrPlaylistId(playlist.id))) }
        database.withTransaction { checkCurrent(current); dao.clearPages(current.name, playlist.id); dao.clearOperations(current.name, playlist.id) }
        refreshPlaylists()
    }

    suspend fun subscribe(id: String) = withContext(Dispatchers.IO) {
        require(id.isNotBlank())
        val current = capture()
        val api = provider.api()
        val playlist = request(current) { api.metadata(bearer(current), id) }
        require(playlist.user_name != current.name)
        request(current) { checkMutation(api.like(bearer(current), AsmrPlaylistId(id))) }
        refreshPlaylists()
    }

    suspend fun unsubscribe(playlist: AsmrCloudPlaylist) = withContext(Dispatchers.IO) {
        val current = capture()
        require(playlist.user_name != current.name)
        request(current) { checkMutation(provider.api().unlike(bearer(current), AsmrPlaylistId(playlist.id))) }
        refreshPlaylists()
    }

    suspend fun setProgress(work: AsmrCloudWork, progress: String) = withContext(Dispatchers.IO) {
        require(progress in LISTENING_PROGRESS)
        val current = capture()
        val api = provider.api()
        val latest = request(current) { api.work(bearer(current), work.id) }
        // Do not replay a stale full-object PUT over another device's review edits.
        check(latest.userRating == work.userRating && latest.review_text == work.review_text && latest.progress == work.progress)
        request(current) { checkMutation(api.progress(bearer(current), AsmrProgressRequest(work.id,
            latest.userRating, latest.review_text, progress))) }
    }

    suspend fun retryPending() = withContext(Dispatchers.IO) {
        val current = capture()
        dao.retry(current.name)
        scheduling.enqueue(current.name)
    }

    /** Reconciles unknown outcomes before every write; old acknowledgements cannot delete newer intents. */
    suspend fun drain(accountName: String): Boolean = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val current = try { capture() } catch (_: AsmrLoginRequired) { return@withLock true }
            if (current.name != accountName) return@withLock true
            val api = provider.api()
            var changed = false
            while (true) {
                val batch = dao.pending(current.name)
                if (batch.isEmpty()) break
                for (operation in batch) {
                    val result = try { reconcileOperation(current, api, operation) }
                    catch (_: AsmrLoginRequired) { return@withLock true }
                    when (result) {
                        AsmrMembershipSaveResult.SYNCED -> changed = true
                        AsmrMembershipSaveResult.QUEUED -> return@withLock false
                        AsmrMembershipSaveResult.BLOCKED -> Unit
                    }
                }
            }
            val complete = dao.pending(current.name).isEmpty()
            if (complete && changed) {
                try { refreshPlaylists() }
                catch (error: CancellationException) { throw error }
                catch (_: Exception) { /* The membership was confirmed; a metadata refresh can wait. */ }
            }
            complete
        }
    }

    private suspend fun reconcileOperation(current: AsmrOneCredentials, api: AsmrOneAccountApi,
        operation: AsmrMembershipOperation): AsmrMembershipSaveResult {
        try {
            checkCurrent(current)
            val target = membershipRemote(current, api, operation.workId).firstOrNull { it.id == operation.listId }
            if (target != null && target.user_name == current.name) {
                if (target.exist != operation.desired) {
                    val body = AsmrPlaylistWorks(operation.listId, listOf(operation.workId))
                    request(current) { checkMutation(if (operation.desired) api.add(bearer(current), body) else api.remove(bearer(current), body)) }
                    val confirmed = membershipRemote(current, api, operation.workId).firstOrNull { it.id == operation.listId }
                    if (confirmed?.exist != operation.desired) return AsmrMembershipSaveResult.QUEUED
                }
                confirmOperation(current, operation)
                return AsmrMembershipSaveResult.SYNCED
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: AsmrLoginRequired) {
            throw error
        } catch (error: HttpException) {
            if (error.code() == 429 || error.code() >= 500) return AsmrMembershipSaveResult.QUEUED
        } catch (_: IOException) {
            return AsmrMembershipSaveResult.QUEUED
        } catch (_: Exception) {
            // Keep rejected intents available for explicit retry without claiming success.
        }
        dao.block(current.name, operation.listId, operation.workId, operation.version)
        return AsmrMembershipSaveResult.BLOCKED
    }

    private suspend fun confirmOperation(current: AsmrOneCredentials, operation: AsmrMembershipOperation) {
        database.withTransaction {
            checkCurrent(current)
            val pages = dao.pagesOnce(current.name, operation.listId)
            val work = gson.fromJson(operation.workPayload, AsmrCloudWork::class.java)
            val decoded = pages.map { it to gson.fromJson(it.payload, AsmrWorkPage::class.java) }
            val existed = decoded.any { (_, data) -> data.works.orEmpty().any { it.id == work.id } }
            decoded.forEach { (page, data) ->
                val works = if (operation.desired) data.works.orEmpty().toMutableList()
                    else data.works.orEmpty().filterNot { it.id == work.id }.toMutableList()
                if (operation.desired && !existed && page.page == 1) works.add(0, work)
                val delta = if (operation.desired && !existed) 1 else if (!operation.desired && existed) -1 else 0
                dao.putPage(page.copy(payload = gson.toJson(data.copy(works = works,
                    pagination = data.pagination?.let { it.copy(totalCount = (it.totalCount + delta).coerceAtLeast(0)) }))))
            }
            dao.finish(current.name, operation.listId, operation.workId, operation.version)
        }
    }

    private suspend fun membershipRemote(current: AsmrOneCredentials, api: AsmrOneAccountApi, workId: Long): List<AsmrCloudPlaylist> {
        val result = mutableListOf<AsmrCloudPlaylist>()
        var page = 1
        do {
            val response = request(current) { api.membership(bearer(current), workId, page) }
            val pagination = checkNotNull(response.pagination).apply { validate(page) }
            val batch = checkNotNull(response.playlists)
            check(batch.all { it.exist != null && it.id.isNotBlank() })
            result += batch
            if (page.toLong() * pagination.pageSize >= pagination.totalCount) break
            check(batch.isNotEmpty() && page < 1000)
            page++
        } while (true)
        return result
    }

    private suspend fun capture(): AsmrOneCredentials {
        initialized.await()
        return session?.takeIf { !_account.value.expired } ?: throw AsmrLoginRequired()
    }
    private fun checkCurrent(current: AsmrOneCredentials) {
        if (session !== current) throw CancellationException("ASMR.one account changed")
    }
    private fun bearer(current: AsmrOneCredentials) = "Bearer ${current.token}"
    private suspend fun <T> request(current: AsmrOneCredentials, action: suspend () -> T): T {
        checkCurrent(current)
        try {
            return action().also { checkCurrent(current) }
        } catch (error: Exception) {
            if (error is AsmrLoginRequired || error is HttpException && error.code() == 401) {
                if (session === current) _account.value = _account.value.copy(expired = true)
                throw AsmrLoginRequired()
            }
            throw error
        }
    }
    private fun checkMutation(response: com.google.gson.JsonElement) {
        check(!response.isJsonObject || !response.asJsonObject.has("error") || response.asJsonObject["error"].isJsonNull) {
            "ASMR.one rejected operation"
        }
    }
    companion object {
        const val REVIEW_PREFIX = "review:"
        val LISTENING_PROGRESS = listOf("marked", "listening", "listened", "replay", "postponed")
    }
}
