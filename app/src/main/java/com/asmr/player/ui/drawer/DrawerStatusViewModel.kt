package com.asmr.player.ui.drawer

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asmr.player.data.remote.api.Asmr100Api
import com.asmr.player.data.remote.api.Asmr200Api
import com.asmr.player.data.remote.api.Asmr300Api
import com.asmr.player.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject

enum class SiteStatusType { Unknown, Testing, Ok, Fail }

data class SiteStatus(
    val type: SiteStatusType = SiteStatusType.Unknown,
    val latencyMs: Long? = null
)

@HiltViewModel
class DrawerStatusViewModel @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    val asmrOneSite: StateFlow<Int> = settingsRepository.asmrOneSite
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 200)

    private val _dlsite = MutableStateFlow(SiteStatus())
    val dlsite: StateFlow<SiteStatus> = _dlsite

    private val _asmr = MutableStateFlow(SiteStatus())
    val asmr: StateFlow<SiteStatus> = _asmr

    fun testDlsite() {
        viewModelScope.launch(Dispatchers.IO) {
            _dlsite.value = SiteStatus(type = SiteStatusType.Testing)
            val latency = measure("https://www.dlsite.com/")
            _dlsite.value = latency.toStatus()
        }
    }

    fun testAsmrOne() {
        val site = asmrOneSite.value
        // 使用 search 接口测试连通性，比直接访问 ID 更可靠，且能验证 API 是否正常
        val url = when (site) {
            100 -> "${Asmr100Api.BASE_URL}search/RJ01000000"
            300 -> "${Asmr300Api.BASE_URL}search/RJ01000000"
            else -> "${Asmr200Api.BASE_URL}search/RJ01000000"
        }
        viewModelScope.launch(Dispatchers.IO) {
            _asmr.value = SiteStatus(type = SiteStatusType.Testing)
            val latency = measure(url)
            _asmr.value = latency.toStatus()
        }
    }

    fun setAsmrOneSite(site: Int) {
        viewModelScope.launch {
            settingsRepository.setAsmrOneSite(site)
        }
    }

    private fun measure(url: String): Long? {
        val client = okHttpClient.newBuilder()
            .callTimeout(10, TimeUnit.SECONDS)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Cache-Control", "no-cache")
            .header("Accept", "application/json, text/plain, */*")
            .build()
        val start = SystemClock.elapsedRealtime()
        return runCatching {
            client.newCall(request).execute().use { resp ->
                // 只要不是 404 或网络错误，都认为通了（即使是空搜索结果）
                if (!resp.isSuccessful && resp.code != 404) return@use null
                SystemClock.elapsedRealtime() - start
            }
        }.getOrNull()
    }
}

private fun Long?.toStatus(): SiteStatus {
    return if (this == null) SiteStatus(type = SiteStatusType.Fail) else SiteStatus(type = SiteStatusType.Ok, latencyMs = this)
}

