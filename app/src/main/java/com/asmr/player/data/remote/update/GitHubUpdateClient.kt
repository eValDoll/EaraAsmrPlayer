package com.asmr.player.data.remote.update

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.Request

data class UpdateRelease(
    val tagName: String,
    val versionName: String,
    val title: String,
    val body: String,
    val publishedAt: String?,
    val apkName: String,
    val apkUrl: String
)

class GitHubUpdateClient(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson = Gson()
) {
    suspend fun fetchLatestRelease(owner: String, repo: String): UpdateRelease {
        val url = "https://api.github.com/repos/$owner/$repo/releases/latest"
        val req = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Eara-Android")
            .get()
            .build()

        val resp = okHttpClient.newCall(req).execute()
        resp.use {
            if (!it.isSuccessful) {
                val code = it.code
                val msg = it.message.ifBlank { "HTTP $code" }
                throw IllegalStateException("GitHub 请求失败：$code $msg")
            }
            val bodyStr = it.body?.string().orEmpty()
            val parsed = gson.fromJson(bodyStr, GitHubReleaseResponse::class.java)
            val tag = parsed.tagName.orEmpty().trim()
            val version = normalizeVersionName(tag)
            val asset = pickApkAsset(parsed.assets.orEmpty())
                ?: throw IllegalStateException("Release 未包含 APK 资源")
            return UpdateRelease(
                tagName = tag,
                versionName = version,
                title = parsed.name.orEmpty().ifBlank { tag },
                body = parsed.body.orEmpty(),
                publishedAt = parsed.publishedAt,
                apkName = asset.name.orEmpty(),
                apkUrl = asset.browserDownloadUrl.orEmpty()
            )
        }
    }

    fun isNewerThanCurrent(latestVersionName: String, currentVersionName: String): Boolean {
        return compareVersion(latestVersionName, currentVersionName) > 0
    }

    private fun normalizeVersionName(tagOrVersion: String): String {
        val s = tagOrVersion.trim()
        if (s.startsWith("v", ignoreCase = true) && s.length > 1) return s.substring(1)
        return s
    }

    private fun pickApkAsset(assets: List<GitHubReleaseAsset>): GitHubReleaseAsset? {
        val apks = assets.filter { it.name.orEmpty().lowercase().endsWith(".apk") }
        if (apks.isEmpty()) return null
        val prefer = apks.firstOrNull { it.name.orEmpty().contains("universal", ignoreCase = true) }
            ?: apks.firstOrNull { it.name.orEmpty().contains("arm64", ignoreCase = true) }
            ?: apks.firstOrNull { it.name.orEmpty().contains("debug", ignoreCase = true) }
            ?: apks.first()
        return prefer.takeIf { it.browserDownloadUrl.orEmpty().startsWith("http", ignoreCase = true) }
    }

    private fun compareVersion(a: String, b: String): Int {
        val pa = parseVersionParts(a)
        val pb = parseVersionParts(b)
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val va = pa.getOrElse(i) { 0 }
            val vb = pb.getOrElse(i) { 0 }
            if (va != vb) return va.compareTo(vb)
        }
        return 0
    }

    private fun parseVersionParts(s: String): List<Int> {
        val cleaned = normalizeVersionName(s)
        return Regex("\\d+").findAll(cleaned).mapNotNull { m ->
            m.value.toIntOrNull()
        }.toList()
    }
}

data class GitHubReleaseResponse(
    @SerializedName("tag_name") val tagName: String? = null,
    val name: String? = null,
    val body: String? = null,
    @SerializedName("published_at") val publishedAt: String? = null,
    val assets: List<GitHubReleaseAsset>? = null
)

data class GitHubReleaseAsset(
    val name: String? = null,
    @SerializedName("browser_download_url") val browserDownloadUrl: String? = null,
    val size: Long? = null
)

