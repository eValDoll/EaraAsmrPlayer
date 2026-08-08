package com.asmr.player.data.remote.api

import android.os.Build
import com.asmr.player.BuildConfig
import com.asmr.player.data.remote.NetworkHeaders
import com.asmr.player.data.remote.awaitResponse
import com.asmr.player.data.remote.withOnlineDirectoryRequestTimeouts
import com.asmr.player.data.remote.withSearchTimeouts
import com.asmr.player.listentogether.XxHash64
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
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
    val sort: String = "",
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

data class AsmrOneRecommendationRequest(
    val seedRjs: List<String> = emptyList(),
    val excludeRjs: List<String> = emptyList(),
    val limit: Int,
    val cursor: String = ""
)

data class AsmrOneRecommendationResponse(
    val items: List<AsmrOneRecommendationItem>? = emptyList(),
    val matchedSeedRjs: List<String>? = emptyList(),
    val unmatchedSeedRjs: List<String>? = emptyList(),
    val limit: Int = 0,
    val nextCursor: String = "",
    val hasMore: Boolean = false,
    val serverTimeEpochMs: Long = 0L
)

data class AsmrOneRecommendationItem(
    val rj: String = "",
    val title: String = "",
    val cvs: List<String>? = emptyList(),
    val matchedRjs: List<String>? = emptyList(),
    val originalWorkno: String = "",
    val mainCoverUrl: String = ""
)

class AsmrOneRecommendationCursorExpiredException :
    IOException("asmr.one recommendation cursor expired")

data class AsmrOneBackendTrackTreeResponse(
    val rj: String = "",
    val workId: Int = 0,
    val sourceIdDigits: String = "",
    val trackTree: List<AsmrOneTrackNodeResponse>? = emptyList(),
    val fetchedAt: String = "",
    val lastError: String = "",
    val serverTimeEpochMs: Long = 0L
)

@Singleton
class AsmrOneAvailabilityApi @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) {
    internal constructor(
        okHttpClient: OkHttpClient,
        gson: Gson,
        baseUrlProvider: () -> String
    ) : this(okHttpClient, gson) {
        this.baseUrlProvider = baseUrlProvider
    }

    private var baseUrlProvider: () -> String = { BuildConfig.LISTEN_TOGETHER_BASE_URL }
    private val clientSessionId = UUID.randomUUID().toString()
    private val appHeaderValue = "com.asmr.player/${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
    private val deviceFingerprint = buildDeviceFingerprint()
    private val userAgent = buildUserAgent()
    private val requestClient by lazy { okHttpClient.withSearchTimeouts() }
    private val trackTreeClient by lazy { okHttpClient.withOnlineDirectoryRequestTimeouts() }

    val isBackendConfigured: Boolean
        get() = backendBaseUrl.isNotBlank()

    suspend fun check(rjs: List<String>): Map<String, Boolean> {
        val normalized = rjs
            .asSequence()
            .map { it.trim().uppercase() }
            .filter { RJ_CODE_REGEX.matches(it) }
            .distinct()
            .take(MAX_RJS)
            .toList()
        if (normalized.isEmpty() || backendBaseUrl.isBlank()) return emptyMap()

        return try {
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
                requestClient.newCall(request).awaitResponse().use { response ->
                    if (!response.isSuccessful) return@withContext emptyMap()
                    val raw = response.body?.string().orEmpty()
                    if (raw.isBlank()) return@withContext emptyMap()
                    val parsed = gson.fromJson(raw, AsmrOneAvailabilityResponse::class.java)
                    val requested = normalized.toSet()
                    buildMap {
                        parsed.items.forEach { item ->
                            item.matchedRequestRjs(requested).forEach { rj ->
                                put(rj, item.collected)
                            }
                        }
                    }
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            emptyMap()
        }
    }

    suspend fun search(keyword: String, limit: Int, offset: Int, sort: String): AsmrOneCollectedSearchResponse {
        if (backendBaseUrl.isBlank()) throw IOException("asmr.one backend is not configured")
        return withContext(Dispatchers.IO) {
            val url = resolveUrl("api/asmr-one/search")
                .toHttpUrlOrNull()
                ?.newBuilder()
                ?.addQueryParameter("q", keyword.trim())
                ?.addQueryParameter("limit", limit.coerceIn(1, 100).toString())
                ?.addQueryParameter("offset", offset.coerceAtLeast(0).toString())
                ?.addQueryParameter("sort", sort.trim().ifBlank { "release" })
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
            requestClient.newCall(request).awaitResponse().use { response ->
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

    suspend fun getRecommendations(
        seedRjs: List<String>,
        excludeRjs: List<String>,
        limit: Int
    ): AsmrOneRecommendationResponse {
        if (!isBackendConfigured) throw IOException("asmr.one backend is not configured")
        val normalizedSeeds = normalizeRecommendationRjs(seedRjs, MAX_RECOMMENDATION_SEEDS)
        if (normalizedSeeds.isEmpty()) return AsmrOneRecommendationResponse()
        return requestRecommendations(
            AsmrOneRecommendationRequest(
                seedRjs = normalizedSeeds,
                excludeRjs = normalizeRecommendationRjs(excludeRjs, MAX_RECOMMENDATION_EXCLUDES),
                limit = limit.coerceIn(1, MAX_RECOMMENDATION_LIMIT)
            )
        )
    }

    suspend fun continueRecommendations(
        cursor: String,
        limit: Int
    ): AsmrOneRecommendationResponse {
        if (!isBackendConfigured) throw IOException("asmr.one backend is not configured")
        val normalizedCursor = cursor.trim()
        if (normalizedCursor.isEmpty()) throw AsmrOneRecommendationCursorExpiredException()
        return requestRecommendations(
            AsmrOneRecommendationRequest(
                cursor = normalizedCursor,
                limit = limit.coerceIn(1, MAX_RECOMMENDATION_LIMIT)
            )
        )
    }

    private suspend fun requestRecommendations(
        requestBody: AsmrOneRecommendationRequest
    ): AsmrOneRecommendationResponse =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(resolveUrl("api/asmr-one/recommendations"))
                .header("User-Agent", userAgent)
                .header("X-Listen-Together-App", appHeaderValue)
                .header("X-Listen-Together-Client-Session-Id", clientSessionId)
                .header("X-Listen-Together-Device-Fingerprint", deviceFingerprint)
                .header(NetworkHeaders.HEADER_SILENT_IO_ERROR, NetworkHeaders.SILENT_IO_ERROR_ON)
                .post(gson.toJson(requestBody).toRequestBody(JSON_MEDIA_TYPE))
                .build()
            requestClient.newCall(request).awaitResponse().use { response ->
                if (response.code == 410) {
                    throw AsmrOneRecommendationCursorExpiredException()
                }
                if (!response.isSuccessful) {
                    throw IOException("asmr.one recommendations failed: HTTP ${response.code}")
                }
                val raw = response.body?.string().orEmpty()
                if (raw.isBlank()) return@withContext AsmrOneRecommendationResponse()
                gson.fromJson(raw, AsmrOneRecommendationResponse::class.java)
                    ?: AsmrOneRecommendationResponse()
            }
        }

    suspend fun getTrackTreeByRj(rj: String): AsmrOneBackendTrackTreeResponse {
        if (backendBaseUrl.isBlank()) throw IOException("asmr.one backend is not configured")
        val normalizedRj = rj.trim().uppercase()
        if (!RJ_CODE_REGEX.matches(normalizedRj)) throw IOException("asmr.one tracks RJ is invalid")
        return withContext(Dispatchers.IO) {
            val url = resolveUrl("api/asmr-one/tracks")
                .toHttpUrlOrNull()
                ?.newBuilder()
                ?.addQueryParameter("rj", normalizedRj)
                ?.build()
                ?: throw IOException("invalid asmr.one tracks backend url")
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("X-Listen-Together-App", appHeaderValue)
                .header("X-Listen-Together-Client-Session-Id", clientSessionId)
                .header("X-Listen-Together-Device-Fingerprint", deviceFingerprint)
                .header(NetworkHeaders.HEADER_SILENT_IO_ERROR, NetworkHeaders.SILENT_IO_ERROR_ON)
                .get()
                .build()
            trackTreeClient.newCall(request).awaitResponse().use { response ->
                val raw = response.body?.string().orEmpty()
                if (response.code == 404 && raw.contains("\"tracks_not_found\"", ignoreCase = true)) {
                    throw IOException("asmr.one tracks backend not found")
                }
                if (!response.isSuccessful) {
                    throw IOException("asmr.one tracks backend failed: HTTP ${response.code}")
                }
                if (raw.isBlank()) return@withContext AsmrOneBackendTrackTreeResponse(rj = normalizedRj)
                val responseType = object : TypeToken<AsmrOneBackendTrackTreeResponse>() {}.type
                gson.fromJson<AsmrOneBackendTrackTreeResponse>(raw, responseType)
                    ?: AsmrOneBackendTrackTreeResponse(rj = normalizedRj)
            }
        }
    }

    private fun resolveUrl(path: String): String {
        val root = backendBaseUrl.trimEnd('/')
        val normalizedPath = path.trimStart('/')
        return "$root/$normalizedPath"
    }

    private val backendBaseUrl: String
        get() = baseUrlProvider().trim()

    private fun buildDeviceFingerprint(): String {
        val source = listOf(
            Build.BRAND.orEmpty(),
            Build.MANUFACTURER.orEmpty(),
            Build.MODEL.orEmpty(),
            Build.DEVICE.orEmpty(),
            Build.PRODUCT.orEmpty(),
            Build.VERSION.SDK_INT.toString(),
            BuildConfig.APPLICATION_ID,
            BuildConfig.VERSION_NAME,
        ).joinToString(separator = "|") { it.trim() }
        return XxHash64.hashHex(source.toByteArray(UTF_8))
    }

    private fun buildUserAgent(): String {
        val deviceModel = listOf(Build.MANUFACTURER.orEmpty(), Build.MODEL.orEmpty())
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
        private const val MAX_RECOMMENDATION_SEEDS = 20
        private const val MAX_RECOMMENDATION_EXCLUDES = 200
        private const val MAX_RECOMMENDATION_LIMIT = 50
    }
}

internal fun normalizeRecommendationRjs(values: List<String>, limit: Int): List<String> =
    values.asSequence()
        .map { it.trim().uppercase() }
        .filter { RECOMMENDATION_RJ_CODE_REGEX.matches(it) }
        .distinct()
        .take(limit.coerceAtLeast(0))
        .toList()

private val RECOMMENDATION_RJ_CODE_REGEX = Regex("""RJ\d{6,}""")

private fun AsmrOneAvailabilityItem.matchedRequestRjs(requested: Set<String>): List<String> {
    if (requested.isEmpty()) return emptyList()
    return buildList {
        add(rj)
        add(originalWorkno)
        addAll(matchedRjs)
    }
        .asSequence()
        .map { it.trim().uppercase() }
        .filter { it in requested }
        .distinct()
        .toList()
}
