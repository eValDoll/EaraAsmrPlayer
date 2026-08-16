package com.asmr.player.data.remote

import android.content.Context
import com.asmr.player.data.settings.AppProxyMode
import com.asmr.player.data.settings.NetworkRouteSettings
import com.asmr.player.data.settings.ProxyPasswordStore
import com.asmr.player.data.settings.SettingsRepository
import com.asmr.player.data.settings.isValidManualProxy
import com.asmr.player.data.settings.normalizeProxyHost
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.net.Authenticator
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import okhttp3.ConnectionPool
import okhttp3.Credentials
import okhttp3.Dns
import okhttp3.OkHttpClient

@Singleton
class NetworkRouteManager @Inject constructor(
    settingsRepository: SettingsRepository,
    @ApplicationContext context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val settingsRef = AtomicReference(NetworkRouteSettings())
    private val proxyPasswordStore = ProxyPasswordStore.get(context)
    private val systemProxySelector = ProxySelector.getDefault()
    private val connectionPools = CopyOnWriteArraySet<ConnectionPool>()
    private val dnsResolvers = ConcurrentHashMap<List<String>, Dns>()

    val proxySelector: ProxySelector = object : ProxySelector() {
        override fun select(uri: URI): List<Proxy> {
            manualProxyFor(settingsRef.get())?.let { return listOf(it) }
            return runCatching { systemProxySelector?.select(uri) }
                .getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?: listOf(Proxy.NO_PROXY)
        }

        override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
            if (manualProxyFor(settingsRef.get()) != null) return
            runCatching { systemProxySelector?.connectFailed(uri, sa, ioe) }
        }
    }

    val proxyAuthenticator: okhttp3.Authenticator = okhttp3.Authenticator { _, response ->
        val settings = settingsRef.get()
        val authorization = httpProxyAuthorizationFor(
            settings = settings,
            password = proxyPasswordStore.read(),
            alreadyAuthorized = response.request.header(PROXY_AUTHORIZATION_HEADER) != null
        ) ?: return@Authenticator null
        response.request.newBuilder()
            .header(PROXY_AUTHORIZATION_HEADER, authorization)
            .build()
    }

    val dns: Dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val settings = settingsRef.get()
            val proxyHost = normalizeProxyHost(settings.proxyHost)
            if (proxyHost != null && hostname.equals(proxyHost, ignoreCase = true)) {
                return Dns.SYSTEM.lookup(hostname)
            }
            val servers = settings.activeDnsServerAddresses
            if (servers.isEmpty()) return Dns.SYSTEM.lookup(hostname)
            return dnsResolvers.getOrPut(servers) { DnsServerResolver(servers) }.lookup(hostname)
        }
    }

    init {
        installSocksAuthenticator()
        scope.launch {
            settingsRepository.networkRouteSettings
                .distinctUntilChanged()
                .collect { settings ->
                    val previous = settingsRef.getAndSet(settings)
                    if (!previous.hasSameNetworkRouteAs(settings)) {
                        dnsResolvers.clear()
                        connectionPools.forEach(ConnectionPool::evictAll)
                    }
                }
        }
    }

    fun register(client: OkHttpClient): OkHttpClient {
        connectionPools += client.connectionPool
        return client
    }

    private fun installSocksAuthenticator() {
        Authenticator.setDefault(object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication? {
                val settings = settingsRef.get()
                return socksPasswordAuthenticationFor(
                    settings = settings,
                    password = proxyPasswordStore.read(),
                    requestorType = requestorType,
                    requestingPort = requestingPort
                )
            }
        })
    }
}

internal fun manualProxyFor(settings: NetworkRouteSettings): Proxy? {
    if (!isValidManualProxy(settings.proxyMode, settings.proxyHost, settings.proxyPort)) return null
    val host = normalizeProxyHost(settings.proxyHost) ?: return null
    val type = when (settings.proxyMode) {
        AppProxyMode.SYSTEM -> return null
        AppProxyMode.HTTP -> Proxy.Type.HTTP
        AppProxyMode.SOCKS5 -> Proxy.Type.SOCKS
    }
    return Proxy(type, InetSocketAddress.createUnresolved(host, settings.proxyPort))
}

internal fun httpProxyAuthorizationFor(
    settings: NetworkRouteSettings,
    password: String,
    alreadyAuthorized: Boolean
): String? {
    if (
        settings.proxyMode != AppProxyMode.HTTP ||
        !settings.proxyAuthenticationEnabled ||
        settings.proxyUsername.isBlank() ||
        password.isEmpty() ||
        alreadyAuthorized
    ) return null
    return Credentials.basic(settings.proxyUsername, password)
}

internal fun socksPasswordAuthenticationFor(
    settings: NetworkRouteSettings,
    password: String,
    requestorType: Authenticator.RequestorType,
    requestingPort: Int
): PasswordAuthentication? {
    if (
        requestorType != Authenticator.RequestorType.PROXY ||
        settings.proxyMode != AppProxyMode.SOCKS5 ||
        !settings.proxyAuthenticationEnabled ||
        settings.proxyUsername.isBlank() ||
        password.isEmpty() ||
        requestingPort != settings.proxyPort
    ) return null
    return PasswordAuthentication(settings.proxyUsername, password.toCharArray())
}

private fun NetworkRouteSettings.hasSameNetworkRouteAs(other: NetworkRouteSettings): Boolean {
    return proxyMode == other.proxyMode &&
        proxyHost == other.proxyHost &&
        proxyPort == other.proxyPort &&
        proxyAuthenticationEnabled == other.proxyAuthenticationEnabled &&
        proxyUsername == other.proxyUsername &&
        proxyPasswordConfigured == other.proxyPasswordConfigured &&
        proxyCredentialVersion == other.proxyCredentialVersion &&
        customDnsServer == other.customDnsServer
}

private const val PROXY_AUTHORIZATION_HEADER = "Proxy-Authorization"
