package com.asmr.player.ui.dlsite

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asmr.player.data.remote.auth.DlsiteAuthStore
import com.asmr.player.data.remote.auth.DlsiteLoginClient
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DlsiteLoginUiState(
    val isLoading: Boolean = false,
    val message: String = "",
    val isLoggedIn: Boolean = false,
    val dlsiteCookie: String = "",
    val playCookie: String = "",
    val dlsiteExpiresAtMs: Long? = null,
    val playExpiresAtMs: Long? = null
)

@HiltViewModel
class DlsiteLoginViewModel @Inject constructor(
    private val loginClient: DlsiteLoginClient,
    @ApplicationContext context: Context
) : ViewModel() {
    private val authStore = DlsiteAuthStore(context)
    private val _uiState = MutableStateFlow(snapshot())
    val uiState: StateFlow<DlsiteLoginUiState> = _uiState.asStateFlow()

    private fun snapshot(message: String = "", isLoading: Boolean = false): DlsiteLoginUiState {
        return DlsiteLoginUiState(
            isLoading = isLoading,
            message = message,
            isLoggedIn = authStore.isLoggedIn(),
            dlsiteCookie = authStore.getDlsiteCookie(),
            playCookie = authStore.getPlayCookie(),
            dlsiteExpiresAtMs = authStore.getDlsiteCookieExpiresAtMs(),
            playExpiresAtMs = authStore.getPlayCookieExpiresAtMs()
        )
    }

    fun clear() {
        authStore.clear()
        _uiState.value = snapshot(message = "已退出登录")
    }

    fun login(loginId: String, password: String) {
        viewModelScope.launch {
            _uiState.value = snapshot(isLoading = true)
            val result = runCatching { loginClient.login(loginId, password) }.getOrElse { e ->
                _uiState.value = snapshot(message = e.message.orEmpty().ifBlank { "登录失败" })
                return@launch
            }
            if (result.dlsiteCookie.isNotBlank()) {
                authStore.saveDlsiteCookie(result.dlsiteCookie, expiresAtMs = result.dlsiteExpiresAtMs)
            }
            if (result.playCookie.isNotBlank()) {
                authStore.savePlayCookie(result.playCookie, expiresAtMs = result.playExpiresAtMs)
            }
            _uiState.value = snapshot(message = "登录成功")
        }
    }
}
