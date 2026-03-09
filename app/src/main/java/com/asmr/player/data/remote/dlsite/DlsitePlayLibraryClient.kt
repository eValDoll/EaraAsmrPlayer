package com.asmr.player.data.remote.dlsite

import android.content.Context
import com.asmr.player.data.remote.NetworkHeaders
import com.asmr.player.data.remote.auth.DlsiteAuthStore
import com.asmr.player.domain.model.Album
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DlsitePlayLibraryClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    @ApplicationContext context: Context
) {
    private val authStore = DlsiteAuthStore(context)
    private val gson = Gson()
    private val cacheTtlMs = TimeUnit.MINUTES.toMillis(5)

    private var cachedAlbums: List<Album> = emptyList()
    private var cachedTs: Long = 0L

    suspend fun searchPurchased(keyword: String, page: Int, pageSize: Int): PurchasedSearchPage {
        return withContext(Dispatchers.IO) {
            val all = ensureLibraryCached()
            val filtered = filterByKeyword(all, keyword)
            val safePage = page.coerceAtLeast(1)
            val start = (safePage - 1) * pageSize
            val end = (start + pageSize).coerceAtMost(filtered.size)
            val items = if (start in 0..filtered.size && start < end) filtered.subList(start, end) else emptyList()
            val canGoNext = end < filtered.size
            PurchasedSearchPage(
                items = items,
                page = safePage,
                pageSize = pageSize,
                totalCount = filtered.size,
                canGoNext = canGoNext
            )
        }
    }

    private suspend fun ensureLibraryCached(): List<Album> {
        val now = System.currentTimeMillis()
        if (cachedAlbums.isNotEmpty() && now - cachedTs < cacheTtlMs) return cachedAlbums

        val cookie0 = authStore.getPlayCookie().trim()
        android.util.Log.d(TAG, "Ensuring library cached. Initial play cookie length: ${cookie0.length}")
        
        val cookie = ensurePlayAuthorized(cookie0)
        if (cookie != cookie0 && cookie.isNotBlank()) {
            android.util.Log.d(TAG, "Play cookie updated after authorization check.")
            authStore.savePlayCookie(cookie)
        }
        
        if (cookie.isBlank()) {
            android.util.Log.e(TAG, "No play cookie available. Login required.")
            throw IllegalStateException("请先登录 DLsite（需要 play.dlsite.com 的 Cookie）")
        }

        android.util.Log.d(TAG, "Fetching sales...")
        val worknoToSalesDate = fetchSales(cookie)
        android.util.Log.d(TAG, "Fetched ${worknoToSalesDate.size} sales items.")
        
        val works = fetchWorks(cookie, worknoToSalesDate.keys.toList())
        android.util.Log.d(TAG, "Fetched ${works.size} work details.")

        val albums = works.mapNotNull { w ->
            val workno = pickString(w["workno"]).uppercase().trim()
            if (workno.isBlank()) return@mapNotNull null
            val title = pickLocalized(w["name"]).ifBlank { workno }
            val maker = when (val makerObj = w["maker"]) {
                is Map<*, *> -> pickLocalized(makerObj["name"]).ifBlank { pickLocalized(makerObj) }
                else -> pickLocalized(makerObj)
            }
            val cover = run {
                val main = pickLocalized(w["image_main"])
                if (main.isNotBlank()) return@run normalizeCover(main)
                val wf = w["work_files"]
                if (wf is Map<*, *>) {
                    val v = pickLocalized(wf["main"]).ifBlank { pickLocalized(wf["sam"]) }
                    return@run normalizeCover(v)
                }
                ""
            }
            val tagObjects = (w["tags"] as? List<*>)?.mapNotNull { it as? Map<*, *> }.orEmpty()
            val tags = tagObjects.filter { pickString(it["class"]) != "voice_by" }
                .map { pickLocalized(it["name"]) }
                .filter { it.isNotBlank() }
            
            val cvList = tagObjects.filter { pickString(it["class"]) == "voice_by" }
                .map { pickLocalized(it["name"]) }
                .filter { it.isNotBlank() }
            val cv = cvList.joinToString(", ")
            
            val purchaseDate = worknoToSalesDate[workno].orEmpty()

            Album(
                title = title,
                path = "",
                workId = workno,
                rjCode = workno,
                circle = maker,
                cv = cv,
                tags = tags,
                coverUrl = cover,
                description = purchaseDate
            )
        }

        cachedAlbums = albums
        cachedTs = now
        return cachedAlbums
    }

    private fun fetchSales(cookie: String): Map<String, String> {
        val nowSec = (System.currentTimeMillis() / 1000L).toString()
        val request = Request.Builder()
            .url("https://play.dlsite.com/api/v3/content/sales?query[last]=$nowSec")
            .header("Cookie", cookie)
            .header("Referer", "https://play.dlsite.com/library")
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", NetworkHeaders.ACCEPT_LANGUAGE)
            .header("User-Agent", NetworkHeaders.USER_AGENT)
            .header("Cache-Control", "no-cache")
            .header("Pragma", "no-cache")
            .get()
            .build()

        okHttpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                if (resp.code == 401) {
                    throw IllegalStateException("已购库鉴权失败（401），请重新登录 DLsite")
                }
                throw IllegalStateException("获取已购列表失败（${resp.code}）")
            }
            val body = resp.body?.string().orEmpty()
            val type = object : TypeToken<List<Map<String, Any?>>>() {}.type
            val parsed = runCatching { gson.fromJson<Any>(body, type) }.getOrNull()
            val list: List<Map<String, Any?>> = (parsed as? List<*>)?.mapNotNull { it as? Map<String, Any?> }.orEmpty()
            val out = LinkedHashMap<String, String>()
            list.forEach { item ->
                val workno = pickString(item["workno"]).uppercase().trim()
                if (workno.isBlank()) return@forEach
                val date = pickString(item["sales_date"]).trim()
                out[workno] = date
            }
            return out
        }
    }

    private fun fetchWorks(cookie: String, worknos: List<String>): List<Map<String, Any?>> {
        if (worknos.isEmpty()) return emptyList()
        val headers = mapOf(
            "Cookie" to cookie,
            "Referer" to "https://play.dlsite.com/library",
            "Accept" to "application/json, text/plain, */*",
            "Accept-Language" to NetworkHeaders.ACCEPT_LANGUAGE,
            "User-Agent" to NetworkHeaders.USER_AGENT,
            "Cache-Control" to "no-cache",
            "Pragma" to "no-cache",
            "Content-Type" to "application/json"
        )
        val type = object : TypeToken<Map<String, Any?>>() {}.type
        val worksOut = ArrayList<Map<String, Any?>>()
        val jsonType = "application/json; charset=utf-8".toMediaType()
        val chunkSize = 100
        for (i in worknos.indices step chunkSize) {
            val chunk = worknos.subList(i, (i + chunkSize).coerceAtMost(worknos.size))
            val body = gson.toJson(chunk).toRequestBody(jsonType)
            val reqBuilder = Request.Builder().url("https://play.dlsite.com/api/v3/content/works").post(body)
            headers.forEach { (k, v) -> reqBuilder.header(k, v) }
            okHttpClient.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    if (resp.code == 401) {
                        throw IllegalStateException("已购库鉴权失败（401），请重新登录 DLsite")
                    }
                    return@use
                }
                val raw = resp.body?.string().orEmpty()
                val parsed = runCatching { gson.fromJson<Any>(raw, type) }.getOrNull()
                val obj = parsed as? Map<*, *> ?: emptyMap<Any, Any>()
                val works = obj["works"] as? List<*>
                works?.forEach { w ->
                    if (w is Map<*, *>) {
                        @Suppress("UNCHECKED_CAST")
                        worksOut.add(w as Map<String, Any?>)
                    }
                }
            }
        }
        return worksOut
    }

    private companion object {
        private const val TAG = "DlsitePlayClient"
    }


    private fun filterByKeyword(all: List<Album>, keyword: String): List<Album> {
        val kw = keyword.trim()
        if (kw.isBlank()) return all
        val rj = Regex("""\bRJ\d{6,10}\b""", RegexOption.IGNORE_CASE).find(kw)?.value?.uppercase()
        if (!rj.isNullOrBlank()) return all.filter { it.rjCode.uppercase() == rj || it.workId.uppercase() == rj }

        val tokens = kw.split(Regex("""\s+""")).filter { it.isNotBlank() }.map { it.lowercase() }
        if (tokens.isEmpty()) return all
        return all.filter { album ->
            val hay = "${album.rjCode} ${album.workId} ${album.title} ${album.circle}".lowercase()
            tokens.all { hay.contains(it) }
        }
    }

    private fun pickString(v: Any?): String = when (v) {
        null -> ""
        is String -> v
        else -> v.toString()
    }

    private fun pickLocalized(v: Any?): String {
        if (v is String) return v.trim()
        if (v !is Map<*, *>) return pickString(v).trim()
        val keys = listOf("zh_CN", "zh_TW", "zh_HK", "CHI_HANS", "CHI_HANT", "ja_JP", "en_US")
        for (k in keys) {
            val vv = v[k]
            if (vv is String && vv.isNotBlank()) return vv.trim()
        }
        v.values.forEach { vv ->
            if (vv is String && vv.isNotBlank()) return vv.trim()
        }
        return ""
    }

    private fun normalizeCover(url: String): String {
        val trimmed = url.trim()
        return if (trimmed.startsWith("//")) "https:$trimmed" else trimmed
    }

    private fun ensurePlayAuthorized(cookie: String): String {
        val trimmed = cookie.trim()
        if (trimmed.isBlank()) return ""

        val (cookie1, ok1) = fetchAuthorize(trimmed)
        if (ok1) return cookie1

        val cookie2 = mergeCookieHeader(cookie1, fetchLoginSetCookies(cookie1))
        val (cookie3, ok3) = fetchAuthorize(cookie2)
        if (ok3) return cookie3

        return cookie3
    }

    private fun fetchAuthorize(cookie: String): Pair<String, Boolean> {
        val request = Request.Builder()
            .url("https://play.dlsite.com/api/authorize")
            .header("Cookie", cookie)
            .header("Referer", "https://play.dlsite.com/library")
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", NetworkHeaders.ACCEPT_LANGUAGE)
            .header("User-Agent", NetworkHeaders.USER_AGENT)
            .header("Cache-Control", "no-cache")
            .header("Pragma", "no-cache")
            .get()
            .build()
        okHttpClient.newCall(request).execute().use { resp ->
            val mergedCookie = mergeCookieHeader(cookie, resp.headers("Set-Cookie"))
            val body = if (resp.isSuccessful) resp.body?.string().orEmpty() else ""
            val ok = resp.isSuccessful && body.isNotBlank() && body != "null" && body != "{}"
            return mergedCookie to ok
        }
    }

    private fun fetchLoginSetCookies(cookie: String): List<String> {
        val request = Request.Builder()
            .url("https://play.dlsite.com/login/")
            .header("Cookie", cookie)
            .header("Referer", "https://play.dlsite.com/")
            .header("Accept-Language", NetworkHeaders.ACCEPT_LANGUAGE)
            .header("User-Agent", NetworkHeaders.USER_AGENT)
            .get()
            .build()
        okHttpClient.newCall(request).execute().use { resp ->
            return resp.headers("Set-Cookie")
        }
    }

    private fun mergeCookieHeader(base: String, setCookies: List<String>): String {
        if (setCookies.isEmpty()) return base.trim()
        val map = parseCookieHeader(base)
        setCookies.forEach { sc ->
            val pair = sc.substringBefore(';').trim()
            val idx = pair.indexOf('=')
            if (idx <= 0) return@forEach
            val name = pair.substring(0, idx).trim()
            val value = pair.substring(idx + 1).trim()
            if (name.isBlank()) return@forEach
            map[name] = value
        }
        return map.entries.joinToString("; ") { (k, v) -> "$k=$v" }
    }

    private fun parseCookieHeader(cookieHeader: String): LinkedHashMap<String, String> {
        val out = LinkedHashMap<String, String>()
        cookieHeader.split(';').forEach { part ->
            val p = part.trim()
            if (p.isBlank()) return@forEach
            val idx = p.indexOf('=')
            if (idx <= 0) return@forEach
            val name = p.substring(0, idx).trim()
            val value = p.substring(idx + 1).trim()
            if (name.isBlank()) return@forEach
            out[name] = value
        }
        return out
    }
}

data class PurchasedSearchPage(
    val items: List<Album>,
    val page: Int,
    val pageSize: Int,
    val totalCount: Int,
    val canGoNext: Boolean
)
