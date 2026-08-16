package com.asmr.player.data.remote

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class DnsServerResolverTest {
    @Test
    fun lookup_queriesTheConfiguredDnsServerAddress() {
        val serverFailure = AtomicReference<Throwable?>(null)
        DatagramSocket(InetSocketAddress(InetAddress.getLoopbackAddress(), 0)).use { socket ->
            socket.soTimeout = 5_000
            val serverThread = Thread {
                runCatching {
                    repeat(2) {
                        val requestBuffer = ByteArray(4_096)
                        val requestPacket = DatagramPacket(requestBuffer, requestBuffer.size)
                        socket.receive(requestPacket)
                        val request = requestBuffer.copyOf(requestPacket.length)
                        val response = buildDnsResponse(request)
                        socket.send(
                            DatagramPacket(response, response.size, requestPacket.socketAddress)
                        )
                    }
                }.onFailure(serverFailure::set)
            }.apply { start() }

            val resolver = DnsServerResolver(
                InetSocketAddress(InetAddress.getLoopbackAddress(), socket.localPort)
            )
            val addresses = resolver.lookup("example.test")

            serverThread.join(5_000)
            assertFalse(serverThread.isAlive)
            assertEquals(listOf("203.0.113.9"), addresses.map(InetAddress::getHostAddress))
            assertNull(serverFailure.get())
        }
    }

    private fun buildDnsResponse(request: ByteArray): ByteArray {
        val queryType = ((request[request.size - 4].toInt() and 0xFF) shl 8) or
            (request[request.size - 3].toInt() and 0xFF)
        val hasIpv4Answer = queryType == 1
        return ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.write(request, 0, 2)
                output.writeShort(0x8180)
                output.writeShort(1)
                output.writeShort(if (hasIpv4Answer) 1 else 0)
                output.writeShort(0)
                output.writeShort(0)
                output.write(request, 12, request.size - 12)
                if (hasIpv4Answer) {
                    output.writeShort(0xC00C)
                    output.writeShort(1)
                    output.writeShort(1)
                    output.writeInt(60)
                    output.writeShort(4)
                    output.write(byteArrayOf(203.toByte(), 0, 113, 9))
                }
            }
            bytes.toByteArray()
        }
    }
}
