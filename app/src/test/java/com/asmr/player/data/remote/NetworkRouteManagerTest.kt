package com.asmr.player.data.remote

import com.asmr.player.data.settings.AppProxyMode
import com.asmr.player.data.settings.NetworkRouteSettings
import java.net.Authenticator
import java.net.InetSocketAddress
import java.net.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetworkRouteManagerTest {
    @Test
    fun manualProxyFor_buildsHttpAndSocksRoutes() {
        val http = manualProxyFor(
            NetworkRouteSettings(
                proxyMode = AppProxyMode.HTTP,
                proxyHost = "127.0.0.1",
                proxyPort = 7890
            )
        )
        val socks = manualProxyFor(
            NetworkRouteSettings(
                proxyMode = AppProxyMode.SOCKS5,
                proxyHost = "proxy.local",
                proxyPort = 1080
            )
        )
        requireNotNull(http)
        requireNotNull(socks)

        assertEquals(Proxy.Type.HTTP, http.type())
        assertEquals("127.0.0.1", (http.address() as InetSocketAddress).hostString)
        assertEquals(7890, (http.address() as InetSocketAddress).port)
        assertEquals(Proxy.Type.SOCKS, socks.type())
        assertEquals("proxy.local", (socks.address() as InetSocketAddress).hostString)
        assertEquals(1080, (socks.address() as InetSocketAddress).port)
    }

    @Test
    fun manualProxyFor_returnsNullForSystemOrInvalidRoute() {
        assertNull(manualProxyFor(NetworkRouteSettings()))
        assertNull(
            manualProxyFor(
                NetworkRouteSettings(
                    proxyMode = AppProxyMode.HTTP,
                    proxyHost = "127.0.0.1",
                    proxyPort = 0
                )
            )
        )
    }

    @Test
    fun authenticatedHttpProxy_buildsBasicAuthorizationOnlyOnce() {
        val settings = NetworkRouteSettings(
            proxyMode = AppProxyMode.HTTP,
            proxyHost = "proxy.local",
            proxyPort = 8080,
            proxyAuthenticationEnabled = true,
            proxyUsername = "user",
            proxyPasswordConfigured = true
        )

        assertEquals(
            "Basic dXNlcjpwYXNz",
            httpProxyAuthorizationFor(settings, "pass", alreadyAuthorized = false)
        )
        assertNull(httpProxyAuthorizationFor(settings, "pass", alreadyAuthorized = true))
    }

    @Test
    fun authenticatedSocksProxy_suppliesConfiguredCredentials() {
        val settings = NetworkRouteSettings(
            proxyMode = AppProxyMode.SOCKS5,
            proxyHost = "proxy.local",
            proxyPort = 1080,
            proxyAuthenticationEnabled = true,
            proxyUsername = "user",
            proxyPasswordConfigured = true
        )

        val authentication = socksPasswordAuthenticationFor(
            settings,
            "pass",
            Authenticator.RequestorType.PROXY,
            1080
        )

        requireNotNull(authentication)
        assertEquals("user", authentication.userName)
        assertEquals("pass", authentication.password.concatToString())
        assertNull(
            socksPasswordAuthenticationFor(
                settings,
                "pass",
                Authenticator.RequestorType.SERVER,
                1080
            )
        )
    }
}
