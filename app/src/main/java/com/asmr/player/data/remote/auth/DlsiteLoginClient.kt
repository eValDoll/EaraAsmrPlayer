package com.asmr.player.data.remote.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.asmr.player.data.remote.NetworkHeaders
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URLDecoder
import javax.inject.Inject
import javax.inject.Singleton

data class DlsiteLoginResult(
    val dlsiteCookie: String,
    val playCookie: String,
    val dlsiteExpiresAtMs: Long?,
    val playExpiresAtMs: Long?
)

@Singleton
class DlsiteLoginClient @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private companion object {
        private const val TAG = "DlsiteLoginClient"
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    suspend fun login(loginId: String, password: String): DlsiteLoginResult = withContext(Dispatchers.IO) {
        android.util.Log.d(TAG, "Starting login process for user: $loginId")
        val id = loginId.trim()
        val pw = password
        require(id.isNotBlank()) { "账号不能为空" }
        require(pw.isNotBlank()) { "密码不能为空" }

        val jar = MemoryCookieJar()
        val client = okHttpClient.newBuilder().cookieJar(jar).build()

        // 1. GET Login Page
        android.util.Log.d(TAG, "Fetching login page...")
        val loginPageUrl = "https://login.dlsite.com/login"
        val loginPageHtml = client.newCall(
            Request.Builder()
                .url(loginPageUrl)
                .get()
                .header("User-Agent", UA)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", NetworkHeaders.ACCEPT_LANGUAGE)
                .build()
        ).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("打开登录页失败（${resp.code}）")
            resp.body?.string().orEmpty()
        }

        val doc = Jsoup.parse(loginPageHtml, loginPageUrl)
        val form = doc.selectFirst("form")
        if (form == null) {
            android.util.Log.d(TAG, "No login form found. Checking if already logged in...")
            if (loginPageHtml.contains("mypage") || !loginPageHtml.contains("login_id")) {
                android.util.Log.d(TAG, "Already logged in detected.")
            } else {
                throw IllegalStateException("未找到登录表单且未检测到登录状态")
            }
        } else {
            val actionUrl = form.absUrl("action").ifBlank { loginPageUrl }
            android.util.Log.d(TAG, "Submitting login form to: $actionUrl")

            val hiddenInputs = form.select("input[type=hidden][name]")
            val params = LinkedHashMap<String, String>()
            hiddenInputs.forEach { input ->
                val name = input.attr("name").trim()
                if (name.isNotBlank()) {
                    params[name] = input.attr("value").orEmpty()
                }
            }

            val idField = form.selectFirst("input[type=email][name], input[name*=mail][name], input[name*=login][name]")
                ?.attr("name")?.trim().orEmpty().ifBlank { "login_id" }
            val pwField = form.selectFirst("input[type=password][name]")?.attr("name")?.trim().orEmpty().ifBlank { "password" }

            params[idField] = id
            params[pwField] = pw

            val formBody = FormBody.Builder().apply {
                params.forEach { (k, v) -> add(k, v) }
            }.build()

            val xsrfToken = jar.cookieValueFor(loginPageUrl, "XSRF-TOKEN")
                .takeIf { it.isNotBlank() }
                ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }

            client.newCall(
                Request.Builder()
                    .url(actionUrl)
                    .post(formBody)
                    .header("User-Agent", UA)
                    .header("Referer", loginPageUrl)
                    .apply {
                        if (!xsrfToken.isNullOrBlank()) {
                            header("X-XSRF-TOKEN", xsrfToken)
                        }
                    }
                    .build()
            ).execute().use { resp ->
                val html = resp.body?.string().orEmpty()
                if (html.contains("login_id") && html.contains("password")) {
                    val errorDoc = Jsoup.parse(html)
                    val errorMsg = errorDoc.select(".error_msg, .alert-danger").text().trim()
                    android.util.Log.e(TAG, "Login failed: $errorMsg")
                    throw IllegalStateException(errorMsg.ifBlank { "登录失败，请检查账号密码" })
                }
                android.util.Log.d(TAG, "Login form submitted successfully. Final URL: ${resp.request.url}")
            }
        }

        // 2. Cookie Promotion & Domain Sync (Crucial for play.dlsite.com)
        android.util.Log.d(TAG, "Promoting cookies from login.dlsite.com to .dlsite.com")
        jar.promoteHostOnlyCookies(fromHost = "login.dlsite.com", toDomain = "dlsite.com")

        // 3. PC-like session activation flow
        val syncUrls = listOf(
            "https://www.dlsite.com/home/mypage",
            "https://play.dlsite.com/login/"
        )
        
        syncUrls.forEach { url ->
            android.util.Log.d(TAG, "Activating session at: $url")
            client.newCall(
                Request.Builder()
                    .url(url)
                    .get()
                    .header("User-Agent", UA)
                    .header("Referer", "https://login.dlsite.com/")
                    .build()
            ).execute().close()
        }

        // 4. Verify Authorization
        android.util.Log.d(TAG, "Verifying play.dlsite.com authorization...")
        val authResult = client.newCall(
            Request.Builder()
                .url("https://play.dlsite.com/api/authorize")
                .get()
                .header("User-Agent", UA)
                .header("Referer", "https://play.dlsite.com/library")
                .header("Accept", "application/json, text/plain, */*")
                .build()
        ).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            android.util.Log.d(TAG, "Authorize API Response: $body")
            body.isNotBlank() && body != "null" && body != "{}"
        }

        if (!authResult) {
            android.util.Log.e(TAG, "Play authorization failed after login sync.")
        }

        val dlsiteCookie = jar.cookieHeaderFor("https://www.dlsite.com/")
        val playCookie = jar.cookieHeaderFor("https://play.dlsite.com/")
        val dlsiteExpiresAtMs = jar.cookieExpiresAtFor("https://www.dlsite.com/")
        val playExpiresAtMs = jar.cookieExpiresAtFor("https://play.dlsite.com/")

        android.util.Log.d(TAG, "Login completed. DlsiteCookie length: ${dlsiteCookie.length}, PlayCookie length: ${playCookie.length}")
        
        if (dlsiteCookie.isBlank() && playCookie.isBlank()) {
            throw IllegalStateException("未获取到登录 Cookie")
        }
        
        DlsiteLoginResult(
            dlsiteCookie = dlsiteCookie,
            playCookie = playCookie,
            dlsiteExpiresAtMs = dlsiteExpiresAtMs,
            playExpiresAtMs = playExpiresAtMs
        )
    }

    private class MemoryCookieJar : CookieJar {
        private val store = LinkedHashMap<String, MutableList<Cookie>>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val key = url.host
            val list = store.getOrPut(key) { mutableListOf() }
            cookies.forEach { c ->
                list.removeAll { it.name == c.name && it.path == c.path && it.domain == c.domain }
                list.add(c)
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val host = url.host
            val path = url.encodedPath
            val nowMs = System.currentTimeMillis()
            val out = mutableListOf<Cookie>()
            store.values.flatten().forEach { c ->
                if (c.expiresAt < nowMs) return@forEach
                if (!matches(host, path, c)) return@forEach
                out.add(c)
            }
            return out
        }

        fun cookieHeaderFor(url: String): String {
            val httpUrl = url.toHttpUrlOrNull() ?: return ""
            return loadForRequest(httpUrl).joinToString("; ") { "${it.name}=${it.value}" }
        }

        fun cookieExpiresAtFor(url: String): Long? {
            val httpUrl = url.toHttpUrlOrNull() ?: return null
            val nowMs = System.currentTimeMillis()
            return loadForRequest(httpUrl)
                .asSequence()
                .filter { it.persistent }
                .map { it.expiresAt }
                .filter { it > nowMs }
                .minOrNull()
        }

        fun cookieValueFor(url: String, name: String): String {
            val httpUrl = url.toHttpUrlOrNull() ?: return ""
            return loadForRequest(httpUrl).firstOrNull { it.name == name }?.value.orEmpty()
        }

        fun promoteHostOnlyCookies(fromHost: String, toDomain: String) {
            val from = store[fromHost].orEmpty()
            if (from.isEmpty()) return
            val out = store.getOrPut(toDomain) { mutableListOf() }
            from.forEach { c ->
                if (!c.hostOnly) return@forEach
                if (c.domain != fromHost) return@forEach
                val builder = Cookie.Builder()
                    .name(c.name)
                    .value(c.value)
                    .domain(toDomain)
                    .path(c.path)
                if (c.secure) builder.secure()
                if (c.httpOnly) builder.httpOnly()
                if (c.persistent) builder.expiresAt(c.expiresAt)
                val promoted = builder.build()
                out.removeAll { it.name == promoted.name && it.path == promoted.path && it.domain == promoted.domain }
                out.add(promoted)
            }
        }

        private fun matches(host: String, path: String, cookie: Cookie): Boolean {
            val domain = cookie.domain
            val domainMatch = if (cookie.hostOnly) {
                host == domain
            } else {
                host == domain || host.endsWith(".$domain")
            }
            if (!domainMatch) return false
            if (!path.startsWith(cookie.path)) return false
            return true
        }
    }
}
