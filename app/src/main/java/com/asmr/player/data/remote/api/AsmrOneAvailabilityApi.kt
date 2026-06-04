package com.asmr.player.data.remote.api

import android.os.Build
import com.asmr.player.BuildConfig
import com.asmr.player.data.remote.NetworkHeaders
import com.asmr.player.listentogether.XxHash64
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.text.Charsets.UTF_8

data class AsmrOneAvailabilityRequest(
    val rjs: List<String>
)

data class AsmrOneAvailabilityResponse(
    val items: List<AsmrOneAvailabilityItem> = emptyList(),
    val serverTimeEpochMs: Long = 0L
)

data class AsmrOneAvailabilityItem(
    val rj: String = "",
    val collected: Boolean = false,
    val workId: Int = 0,
    val matchedRjs: List<String> = emptyList(),
    val originalWorkno: String = "",
    val title: String = ""
)

data class AsmrOneCollectedSearchResponse(
    val items: List<AsmrOneCollectedSearchItem>? = emptyList(),
    val total: Int = 0,
    val limit: Int = 0,
    val offset: Int = 0,
    val serverTimeEpochMs: Long = 0L
)

data class AsmrOneCollectedSearchItem(
    val workId: Int = 0,
    val rj: String = "",
    val title: String = "",
    val circle: String = "",
    val cvs: List<String>? = emptyList(),
    val tags: List<String>? = emptyList(),
    val matchedRjs: List<String>? = emptyList(),
    val originalWorkno: String = "",
    val releaseDate: String = "",
    val createDate: String = "",
    val mainCoverUrl: String = "",
    val dlCount: Int? = null,
    val price: Int? = null,
    val reviewCount: Int? = null,
    val rateCount: Int? = null,
    val rateAverage2dp: Double? = null
)

@Singleton
class AsmrOneAvailabilityApi @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) {
    private val clientSessionId = UUID.randomUUID().toString()
    private val appHeaderValue = "com.asmr.player/${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
    private val deviceFingerprint = buildDeviceFingerprint()
    private val userAgent = buildUserAgent()

    suspend fun check(rjs: List<String>): Map<String, Boolean> {
        val normalized = rjs
            .asSequence()
            .map { it.trim().uppercase() }
            .filter { RJ_CODE_REGEX.matches(it) }
            .distinct()
            .take(MAX_RJS)
            .toList()
        if (normalized.isEmpty() || baseUrl.isBlank()) return emptyMap()

        return runCatching {
            withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url(resolveUrl("api/asmr-one/availability"))
                    .header("User-Agent", userAgent)
                    .header("X-Listen-Together-App", appHeaderValue)
                    .header("X-Listen-Together-Client-Session-Id", clientSessionId)
                    .header("X-Listen-Together-Device-Fingerprint", deviceFingerprint)
                    .header(NetworkHeaders.HEADER_SILENT_IO_ERROR, NetworkHeaders.SILENT_IO_ERROR_ON)
                    .post(gson.toJson(AsmrOneAvailabilityRequest(normalized)).toRequestBody(JSON_MEDIA_TYPE))
                    .build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext emptyMap()
                    val raw = response.body?.string().orEmpty()
                    if (raw.isBlank()) return@withContext emptyMap()
                    val parsed = gson.fromJson(raw, AsmrOneAvailabilityResponse::class.java)
                    parsed.items.associate { it.rj.trim().uppercase() to it.collected }
                }
            }
        }.getOrDefault(emptyMap())
    }

    suspend fun search(keyword: String, limit: Int, offset: Int): AsmrOneCollectedSearchResponse {
        if (baseUrl.isBlank()) throw IOException("asmr.one backend is not configured")
        return withContext(Dispatchers.IO) {
            val url = resolveUrl("api/asmr-one/search")
                .toHttpUrlOrNull()
                ?.newBuilder()
                ?.addQueryParameter("q", keyword.trim())
                ?.addQueryParameter("limit", limit.coerceIn(1, 100).toString())
                ?.addQueryParameter("offset", offset.coerceAtLeast(0).toString())
                ?.build()
                ?: throw IOException("invalid asmr.one backend url")
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
                    throw IOException("asmr.one search failed: HTTP ${response.code}")
                }
                val raw = response.body?.string().orEmpty()
                if (raw.isBlank()) return@withContext AsmrOneCollectedSearchResponse()
                gson.fromJson(raw, AsmrOneCollectedSearchResponse::class.java)
                    ?: AsmrOneCollectedSearchResponse()
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
        return XxHash64.hashHex(source.toByteArray(UTF_8))
    }

    private fun buildUserAgent(): String {
        val deviceModel = listOf(Build.MANUFACTURER, Build.MODEL)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(separator = " ")
            .ifBlank { "Android" }
        return "EaraAsmrOneAvailability/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.SDK_INT}; $deviceModel)"
    }

    private companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val RJ_CODE_REGEX = Regex("""RJ\d{6,}""")
        private const val MAX_RJS = 100
        private val baseUrl: String
            get() = BuildConfig.LISTEN_TOGETHER_BASE_URL
    }
}
