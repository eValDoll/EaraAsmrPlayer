package com.asmr.player.ui.asmrone

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asmr.player.R
import com.asmr.player.data.remote.api.AsmrCloudPlaylist
import com.asmr.player.data.remote.api.AsmrCloudWork
import com.asmr.player.data.repository.AsmrCloudContent
import com.asmr.player.data.repository.AsmrCloudPlaylists
import com.asmr.player.data.repository.AsmrCredentialsRejected
import com.asmr.player.data.repository.AsmrLoginRequired
import com.asmr.player.data.repository.AsmrOneAccountRepository
import com.asmr.player.data.repository.AsmrWriteUncertain
import com.asmr.player.i18n.appString
import com.asmr.player.util.MessageManager
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class AsmrOneViewModel @Inject constructor(
    private val repository: AsmrOneAccountRepository,
    private val savedState: SavedStateHandle,
    private val messages: MessageManager
) : ViewModel() {
    val account = repository.account
    val playlists = repository.playlists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), AsmrCloudPlaylists())
    val operations = repository.operations.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), emptyList())
    val selectedId = savedState.getStateFlow<String?>("asmrCloudList", null)
    val content = selectedId.flatMapLatest { id -> if (id == null) flowOf(AsmrCloudContent()) else repository.content(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), AsmrCloudContent())
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _memberships = MutableStateFlow<List<AsmrCloudPlaylist>>(emptyList())
    val memberships = _memberships.asStateFlow()
    private val _membershipLoading = MutableStateFlow(false)
    val membershipLoading = _membershipLoading.asStateFlow()
    private val _membershipWork = MutableStateFlow<AsmrCloudWork?>(null)
    val membershipWork = _membershipWork.asStateFlow()
    private var membershipJob: Job? = null
    private val memberMutex = Mutex()
    private val actionMutex = Mutex()

    fun select(id: String?) { savedState["asmrCloudList"] = id }
    fun login(name: String, password: String, onSuccess: () -> Unit = {}) = action {
        repository.login(name, password)
        select(null)
        onSuccess()
        repository.refreshPlaylists()
    }
    fun logout() = action { repository.logout(); select(null); _memberships.value = emptyList() }
    fun refresh() = action {
        repository.retryPending()
        repository.refreshPlaylists()
        selectedId.value?.let { repository.loadPage(it, 1) }
    }
    fun load(id: String, page: Int = 1) = action { if (selectedId.value == id) repository.loadPage(id, page) }
    fun create(name: String, privacy: Int) = action { repository.createPlaylist(name, privacy) }
    fun rename(playlist: AsmrCloudPlaylist, name: String) = action { repository.renamePlaylist(playlist, name) }
    fun delete(playlist: AsmrCloudPlaylist) = action { repository.deletePlaylist(playlist); if (selectedId.value == playlist.id) select(null) }
    fun subscribe(id: String) = action { repository.subscribe(id) }
    fun unsubscribe(playlist: AsmrCloudPlaylist) = action { repository.unsubscribe(playlist); if (selectedId.value == playlist.id) select(null) }
    fun progress(work: AsmrCloudWork, progress: String) = action {
        repository.setProgress(work, progress)
        selectedId.value?.let { repository.loadPage(it, 1) }
    }

    fun openMembership(work: AsmrCloudWork) {
        membershipJob?.cancel()
        _memberships.value = emptyList()
        _membershipWork.value = null
        _membershipLoading.value = true
        membershipJob = viewModelScope.launch {
            try {
                val resolved = repository.resolveMembershipWork(work)
                _membershipWork.value = resolved
                _memberships.value = repository.memberships(resolved.id)
            }
            catch (error: Exception) { report(error) }
            finally { _membershipLoading.value = false }
        }
    }

    fun setMembership(playlist: AsmrCloudPlaylist, work: AsmrCloudWork, desired: Boolean) {
        viewModelScope.launch {
            memberMutex.withLock {
                try {
                    repository.enqueueMembership(playlist, work, desired)
                    _memberships.update { rows -> rows.map { if (it.id == playlist.id) it.copy(exist = desired) else it } }
                } catch (error: Exception) { report(error) }
            }
        }
    }

    fun clearMembership() { membershipJob?.cancel(); _memberships.value = emptyList(); _membershipWork.value = null }

    private fun action(block: suspend () -> Unit) {
        val accountAtSubmission = account.value.name
        viewModelScope.launch {
            actionMutex.withLock {
                if (account.value.name != accountAtSubmission) return@withLock
                _busy.value = true
                try { block() }
                catch (error: Exception) { report(error) }
                finally { _busy.value = false }
            }
        }
    }

    private fun report(error: Exception) {
        if (error is CancellationException) throw error
        messages.showError(appString(when {
            error is AsmrCredentialsRejected -> R.string.cloud_invalid_credentials
            error is AsmrLoginRequired || error is HttpException && error.code() == 401 -> R.string.cloud_login_required
            error is AsmrWriteUncertain -> R.string.cloud_write_uncertain
            error is IOException -> R.string.cloud_network_error
            error is HttpException && error.code() == 429 -> R.string.cloud_rate_limited
            error is HttpException && error.code() == 403 -> R.string.cloud_access_denied
            else -> R.string.cloud_operation_failed
        }))
    }
}
