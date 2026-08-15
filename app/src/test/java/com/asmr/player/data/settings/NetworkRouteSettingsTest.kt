package com.asmr.player.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkRouteSettingsTest {
    @Test
    fun normalizeDnsServerAddress_acceptsIpv4AndIpv6() {
        assertEquals("223.5.5.5", normalizeDnsServerAddress(" 223.005.5.5 "))
        assertEquals(
            "2001:4860:4860:0:0:0:0:8888",
            normalizeDnsServerAddress("[2001:4860:4860::8888]")
        )
    }

    @Test
    fun normalizeDnsServerAddress_rejectsDomainsAndInvalidAddresses() {
        assertNull(normalizeDnsServerAddress("dns.google"))
        assertNull(normalizeDnsServerAddress("https://1.1.1.1/dns-query"))
        assertNull(normalizeDnsServerAddress("256.1.1.1"))
        assertNull(normalizeDnsServerAddress(""))
    }

    @Test
    fun manualProxyValidation_requiresManualModeHostAndValidPort() {
        assertTrue(isValidManualProxy(AppProxyMode.HTTP, "127.0.0.1", 7890))
        assertTrue(isValidManualProxy(AppProxyMode.SOCKS5, "proxy.local", 1080))
        assertFalse(isValidManualProxy(AppProxyMode.SYSTEM, "127.0.0.1", 7890))
        assertFalse(isValidManualProxy(AppProxyMode.HTTP, "http://127.0.0.1", 7890))
        assertFalse(isValidManualProxy(AppProxyMode.HTTP, "127.0.0.1", 0))
    }

    @Test
    fun activeDnsServerAddress_usesOnlyTheUserInput() {
        assertEquals(
            listOf("1.1.1.1"),
            NetworkRouteSettings(customDnsServer = "1.1.1.1").activeDnsServerAddresses
        )
        assertEquals(emptyList<String>(), NetworkRouteSettings().activeDnsServerAddresses)
    }
}
