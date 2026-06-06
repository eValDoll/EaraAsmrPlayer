package com.asmr.player.hotlistening

import android.os.Build
import com.asmr.player.BuildConfig
import com.asmr.player.data.remote.NetworkHeaders
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.text.Charsets.UTF_8

data class HotListeningReportRequest(
    val rj: String
)

data class HotListeningDurationReportRequest(
    val rj: String,
    val durationMs: Long
)

data class HotListeningReportResponse(
    val rj: String,
    val valid: Boolean,
    val durationMs: Long = 0L
)

data class HotListeningItem(
    val rj: String = "",
    val title: String = "",
    val circle: String = "",
    val cv: String = "",
    val tags: String = "",
    val coverUrl: String = "",
    val playCount: Int = 0,
    val listenDurationMs: Long = 0L
) {
    val cvList: List<String>
        get() = cv.split(",").map { it.trim() }.filter { it.isNotBlank() }

    val tagList: List<String>
        get() = tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
}

data class SearchSuggestionTerm(
    val value: String = "",
    val count: Int = 0,
    val rank: Int = 0
)

data class SearchSuggestionsResponse(
    val hotCvs: List<SearchSuggestionTerm> = emptyList(),
    val hotTags: List<SearchSuggestionTerm> = emptyList(),
    val hotWorks: List<HotListeningItem> = emptyList(),
    val serverTimeEpochMs: Long = 0L
)

enum class HotListeningSortMode(val wireValue: String, val label: String) {
    PlayCount("play_count", "按播放次数"),
    ListenDuration("listen_duration", "按收听时长");

    companion object {
        fun fromWireValue(value: String?): HotListeningSortMode {
            return entries.firstOrNull { it.wireValue == value } ?: PlayCount
        }
    }
}

@Singleton
class HotListeningApi @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) {
    private val clientSessionId = UUID.randomUUID().toString()
    private val appHeaderValue = "com.asmr.player/${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
    private val deviceFingerprint = buildDeviceFingerprint()
    private val userAgent = buildUserAgent()

    val isBackendConfigured: Boolean
        get() = baseUrl.isNotBlank()

    suspend fun reportListen(rj: String): HotListeningReportResponse? {
        if (!isBackendConfigured) return null
        val normalizedRj = rj.trim().uppercase()
        if (normalizedRj.isBlank()) return null
        return postJson(
            path = "api/hot-listenings/report",
            body = HotListeningReportRequest(rj = normalizedRj),
            responseClass = HotListeningReportResponse::class.java
        )
    }

    suspend fun reportListenDuration(rj: String, durationMs: Long): HotListeningReportResponse? {
        if (!isBackendConfigured) return null
        val normalizedRj = rj.trim().uppercase()
        if (normalizedRj.isBlank() || durationMs < MIN_DURATION_REPORT_MS) return null
        return postJson(
            path = "api/hot-listenings/report-duration",
            body = HotListeningDurationReportRequest(rj = normalizedRj, durationMs = durationMs),
            responseClass = HotListeningReportResponse::class.java
        )
    }

    suspend fun getTopListings(
        period: String,
        sortMode: HotListeningSortMode = HotListeningSortMode.PlayCount
    ): List<HotListeningItem>? {
        if (!isBackendConfigured) return null
        val normalizedPeriod = period.trim().lowercase()
        if (normalizedPeriod !in setOf("day", "week", "month", "year")) return null
        val listType = com.google.gson.reflect.TypeToken.getParameterized(
            List::class.java,
            HotListeningItem::class.java
        ).type
        return getJson(
            path = "api/hot-listenings/top",
            queryParameters = mapOf(
                "period" to normalizedPeriod,
                "sort" to sortMode.wireValue
            ),
            responseType = listType
        )
    }

    suspend fun getSearchSuggestions(): SearchSuggestionsResponse? {
        if (!isBackendConfigured) return null
        return getJson(
            path = "api/search-suggestions",
            responseType = SearchSuggestionsResponse::class.java
        )
    }

    private suspend fun <T> getJson(
        path: String,
        queryParameters: Map<String, String> = emptyMap(),
        responseType: java.lang.reflect.Type
    ): T? {
        return withContext(Dispatchers.IO) {
            val url = resolveUrl(path)
                .toHttpUrlOrNull()
                ?.newBuilder()
                ?.apply {
                    queryParameters.forEach { (key, value) ->
                        addQueryParameter(key, value)
                    }
                }
                ?.build()
                ?: throw IOException("Invalid HotListening URL: $path")

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("X-Listen-Together-App", appHeaderValue)
                .header("X-Listen-Together-Client-Session-Id", clientSessionId)
                .header("X-Listen-Together-Device-Fingerprint", deviceFingerprint)
                .header(NetworkHeaders.HEADER_SILENT_IO_ERROR, NetworkHeaders.SILENT_IO_ERROR_ON)
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HotListening request failed: ${response.code}")
                }
                val raw = response.body?.string().orEmpty()
                if (raw.isBlank()) return@withContext null
                @Suppress("UNCHECKED_CAST")
                gson.fromJson(raw, responseType) as T?
            }
        }
    }

    private suspend fun <T : Any> postJson(path: String, body: Any, responseClass: Class<T>): T? {
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(resolveUrl(path))
                .header("User-Agent", userAgent)
                .header("X-Listen-Together-App", appHeaderValue)
                .header("X-Listen-Together-Client-Session-Id", clientSessionId)
                .header("X-Listen-Together-Device-Fingerprint", deviceFingerprint)
                .header(NetworkHeaders.HEADER_SILENT_IO_ERROR, NetworkHeaders.SILENT_IO_ERROR_ON)
                .post(gson.toJson(body).toRequestBody(JSON_MEDIA_TYPE))
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HotListening request failed: ${response.code}")
                }
                val raw = response.body?.string().orEmpty()
                if (raw.isBlank()) return@withContext null
                gson.fromJson(raw, responseClass)
            }
        }
    }

    private fun resolveUrl(path: String): String {
        val root = baseUrl.trimEnd('/')
        val normalizedPath = path.trimStart('/')
        return "$root/$normalizedPath"
    }

    private fun buildDeviceFingerprint(): String {
        val source = listOf(
            Build.BRAND,
            Build.MANUFACTURER,
            Build.MODEL,
            Build.DEVICE,
            Build.PRODUCT,
            Build.VERSION.SDK_INT.toString(),
            BuildConfig.APPLICATION_ID,
            BuildConfig.VERSION_NAME,
        ).joinToString(separator = "|") { it.trim() }
        return com.asmr.player.listentogether.XxHash64.hashHex(source.toByteArray(UTF_8))
    }

    private fun buildUserAgent(): String {
        val deviceModel = listOf(Build.MANUFACTURER, Build.MODEL)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(separator = " ")
            .ifBlank { "Android" }
        return "EaraHotListening/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.SDK_INT}; $deviceModel)"
    }

    private companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val MIN_DURATION_REPORT_MS = 30_000L
        private val baseUrl: String
            get() = BuildConfig.LISTEN_TOGETHER_BASE_URL
    }
}
