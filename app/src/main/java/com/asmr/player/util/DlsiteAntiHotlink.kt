package com.asmr.player.util

import com.asmr.player.data.remote.NetworkHeaders
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object DlsiteAntiHotlink {
    fun headersForImageUrl(url: String): Map<String, String> {
        val parsed = url.toHttpUrlOrNull() ?: return emptyMap()
        val host = parsed.host.lowercase()
        val path = parsed.encodedPath.lowercase()
        if (!isDlsiteImageRequest(host = host, path = path)) return emptyMap()
        return mapOf(
            "Referer" to NetworkHeaders.REFERER_DLSITE,
            "User-Agent" to NetworkHeaders.USER_AGENT,
            "Accept-Language" to NetworkHeaders.ACCEPT_LANGUAGE
        )
    }

    fun isDlsiteImageRequest(host: String, path: String): Boolean {
        if (isDlsiteImageHost(host)) return true
        if (path.contains("/modpub/images2/")) return true
        if (path.contains("/images2/work/")) return true
        return false
    }

    fun isDlsiteImageHost(host: String): Boolean {
        val h = host.lowercase()
        if (h.isBlank()) return false
        if (h == "dlsite.com" || h.endsWith(".dlsite.com")) return true
        if (h == "dlsite.jp" || h.endsWith(".dlsite.jp")) return true
        if (h.contains("dlsite")) return true
        if (h.endsWith(".volces.com") && h.contains("byteair")) return true
        return false
    }
}
