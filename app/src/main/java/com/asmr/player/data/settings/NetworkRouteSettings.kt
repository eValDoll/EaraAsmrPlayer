package com.asmr.player.data.settings

import java.net.InetAddress

enum class AppProxyMode(val storageValue: String) {
    SYSTEM("system"),
    HTTP("http"),
    SOCKS5("socks5");

    companion object {
        fun fromStorageValue(value: String?): AppProxyMode =
            entries.firstOrNull { it.storageValue == value } ?: SYSTEM
    }
}

data class NetworkRouteSettings(
    val proxyMode: AppProxyMode = AppProxyMode.SYSTEM,
    val proxyHost: String = "",
    val proxyPort: Int = 0,
    val proxyAuthenticationEnabled: Boolean = false,
    val proxyUsername: String = "",
    val proxyPasswordConfigured: Boolean = false,
    val proxyCredentialVersion: Long = 0L,
    val customDnsServer: String = ""
) {
    val activeDnsServerAddresses: List<String>
        get() = listOfNotNull(normalizeDnsServerAddress(customDnsServer))
}

internal fun normalizeProxyHost(raw: String): String? {
    val normalized = raw.trim().removeSurrounding("[", "]")
    if (
        normalized.isBlank() ||
        normalized.any(Char::isWhitespace) ||
        "://" in normalized ||
        '/' in normalized
    ) return null
    return normalized
}

internal fun isValidManualProxy(mode: AppProxyMode, host: String, port: Int): Boolean {
    return mode != AppProxyMode.SYSTEM && normalizeProxyHost(host) != null && port in 1..65_535
}

internal fun normalizeDnsServerAddress(raw: String): String? {
    val candidate = raw.trim().removeSurrounding("[", "]")
    if (candidate.isBlank() || '%' in candidate) return null

    normalizeIpv4Address(candidate)?.let { return it }
    if (':' !in candidate) return null

    return runCatching { InetAddress.getByName(candidate) }
        .getOrNull()
        ?.takeIf { address -> address.address.size == 16 }
        ?.hostAddress
}

private fun normalizeIpv4Address(candidate: String): String? {
    val parts = candidate.split('.')
    if (parts.size != 4) return null
    val octets = parts.map { part ->
        if (part.isEmpty() || part.length > 3 || part.any { !it.isDigit() }) return null
        part.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
    }
    return octets.joinToString(".")
}
