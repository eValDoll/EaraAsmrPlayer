package com.asmr.player.data.remote

import com.asmr.player.data.settings.normalizeDnsServerAddress
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.IDN
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets
import java.util.concurrent.ThreadLocalRandom
import okhttp3.Dns

internal class DnsServerResolver private constructor(
    private val servers: List<InetSocketAddress>,
    @Suppress("UNUSED_PARAMETER") marker: Unit
) : Dns {
    constructor(serverAddresses: List<String>) : this(
        serverAddresses.mapNotNull { address ->
            normalizeDnsServerAddress(address)?.let { normalized ->
                InetSocketAddress(InetAddress.getByName(normalized), DNS_PORT)
            }
        },
        Unit
    )

    internal constructor(server: InetSocketAddress) : this(listOf(server), Unit)

    override fun lookup(hostname: String): List<InetAddress> {
        normalizeDnsServerAddress(hostname)?.let { address ->
            return listOf(InetAddress.getByName(address))
        }
        if (servers.isEmpty()) throw UnknownHostException("没有可用的 DNS 服务器")

        var lastFailure: Throwable? = null
        servers.forEach { server ->
            val ipv4 = runCatching { queryFollowingCname(hostname, RECORD_TYPE_A, server) }
            if (ipv4.isFailure) {
                lastFailure = ipv4.exceptionOrNull()
                return@forEach
            }
            val ipv6 = runCatching { queryFollowingCname(hostname, RECORD_TYPE_AAAA, server) }
            val addresses = (ipv4.getOrDefault(emptyList()) + ipv6.getOrDefault(emptyList()))
                .distinctBy { address -> address.hostAddress }
            if (addresses.isNotEmpty()) return addresses
            lastFailure = ipv6.exceptionOrNull()
        }

        throw UnknownHostException("DNS 解析失败：$hostname").apply {
            lastFailure?.let(::initCause)
        }
    }

    private fun queryFollowingCname(
        hostname: String,
        recordType: Int,
        server: InetSocketAddress,
        depth: Int = 0
    ): List<InetAddress> {
        if (depth >= MAX_CNAME_DEPTH) throw IOException("CNAME 跳转次数过多")
        val queryId = ThreadLocalRandom.current().nextInt(0x10000)
        val query = buildQuery(hostname, recordType, queryId)
        var response = queryUdp(server, query)
        validateResponseHeader(response, queryId)
        if (readUnsignedShort(response, FLAGS_OFFSET) and FLAG_TRUNCATED != 0) {
            response = queryTcp(server, query)
            validateResponseHeader(response, queryId)
        }
        val parsed = parseResponse(response, queryId, recordType)
        if (parsed.addresses.isNotEmpty()) return parsed.addresses
        val cname = parsed.canonicalName ?: return emptyList()
        return queryFollowingCname(cname, recordType, server, depth + 1)
    }

    private fun queryUdp(server: InetSocketAddress, query: ByteArray): ByteArray {
        return DatagramSocket().use { socket ->
            socket.soTimeout = NETWORK_TIMEOUT_MS
            socket.connect(server)
            socket.send(DatagramPacket(query, query.size))
            val buffer = ByteArray(MAX_UDP_RESPONSE_BYTES)
            val packet = DatagramPacket(buffer, buffer.size)
            socket.receive(packet)
            buffer.copyOf(packet.length)
        }
    }

    private fun queryTcp(server: InetSocketAddress, query: ByteArray): ByteArray {
        return Socket().use { socket ->
            socket.connect(server, NETWORK_TIMEOUT_MS)
            socket.soTimeout = NETWORK_TIMEOUT_MS
            DataOutputStream(socket.getOutputStream()).use { output ->
                output.writeShort(query.size)
                output.write(query)
                output.flush()
                val input = DataInputStream(socket.getInputStream())
                val responseLength = input.readUnsignedShort()
                if (responseLength !in DNS_HEADER_BYTES..MAX_TCP_RESPONSE_BYTES) {
                    throw IOException("DNS TCP 响应长度无效")
                }
                ByteArray(responseLength).also(input::readFully)
            }
        }
    }

    private fun buildQuery(hostname: String, recordType: Int, queryId: Int): ByteArray {
        val asciiName = runCatching {
            IDN.toASCII(hostname.trim().trimEnd('.'), IDN.USE_STD3_ASCII_RULES)
        }.getOrElse {
            throw UnknownHostException("域名格式无效")
        }
        if (asciiName.isBlank() || asciiName.length > MAX_DOMAIN_BYTES) {
            throw UnknownHostException("域名格式无效")
        }
        return ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeShort(queryId)
                output.writeShort(FLAG_RECURSION_DESIRED)
                output.writeShort(1)
                output.writeShort(0)
                output.writeShort(0)
                output.writeShort(0)
                asciiName.split('.').forEach { label ->
                    val encoded = label.toByteArray(StandardCharsets.US_ASCII)
                    if (encoded.isEmpty() || encoded.size > MAX_LABEL_BYTES) {
                        throw UnknownHostException("域名标签格式无效")
                    }
                    output.writeByte(encoded.size)
                    output.write(encoded)
                }
                output.writeByte(0)
                output.writeShort(recordType)
                output.writeShort(DNS_CLASS_IN)
            }
            bytes.toByteArray()
        }
    }

    private fun parseResponse(response: ByteArray, queryId: Int, recordType: Int): ParsedResponse {
        validateResponseHeader(response, queryId)
        val responseCode = readUnsignedShort(response, FLAGS_OFFSET) and RESPONSE_CODE_MASK
        if (responseCode == RESPONSE_CODE_NAME_ERROR) return ParsedResponse()
        if (responseCode != RESPONSE_CODE_NO_ERROR) throw IOException("DNS 服务器返回错误码 $responseCode")

        val questionCount = readUnsignedShort(response, QUESTION_COUNT_OFFSET)
        val answerCount = readUnsignedShort(response, ANSWER_COUNT_OFFSET)
        var offset = DNS_HEADER_BYTES
        repeat(questionCount) {
            offset = readName(response, offset).nextOffset
            ensureAvailable(response, offset, QUESTION_TRAILER_BYTES)
            offset += QUESTION_TRAILER_BYTES
        }

        val addresses = mutableListOf<InetAddress>()
        var canonicalName: String? = null
        repeat(answerCount) {
            offset = readName(response, offset).nextOffset
            ensureAvailable(response, offset, RECORD_HEADER_BYTES)
            val type = readUnsignedShort(response, offset)
            val dnsClass = readUnsignedShort(response, offset + 2)
            val dataLength = readUnsignedShort(response, offset + 8)
            val dataOffset = offset + RECORD_HEADER_BYTES
            ensureAvailable(response, dataOffset, dataLength)
            when {
                dnsClass == DNS_CLASS_IN && type == RECORD_TYPE_A && dataLength == IPV4_BYTES ->
                    addresses += InetAddress.getByAddress(response.copyOfRange(dataOffset, dataOffset + dataLength))
                dnsClass == DNS_CLASS_IN && type == RECORD_TYPE_AAAA && dataLength == IPV6_BYTES ->
                    addresses += InetAddress.getByAddress(response.copyOfRange(dataOffset, dataOffset + dataLength))
                dnsClass == DNS_CLASS_IN && type == RECORD_TYPE_CNAME ->
                    canonicalName = readName(response, dataOffset).value
            }
            offset = dataOffset + dataLength
        }
        return ParsedResponse(
            addresses = addresses.filter { address ->
                (recordType == RECORD_TYPE_A && address.address.size == IPV4_BYTES) ||
                    (recordType == RECORD_TYPE_AAAA && address.address.size == IPV6_BYTES)
            },
            canonicalName = canonicalName
        )
    }

    private fun validateResponseHeader(response: ByteArray, queryId: Int) {
        ensureAvailable(response, 0, DNS_HEADER_BYTES)
        if (readUnsignedShort(response, 0) != queryId) throw IOException("DNS 响应 ID 不匹配")
        if (readUnsignedShort(response, FLAGS_OFFSET) and FLAG_RESPONSE == 0) {
            throw IOException("收到的不是 DNS 响应")
        }
    }

    private fun readName(data: ByteArray, startOffset: Int): DecodedName {
        var offset = startOffset
        var nextOffset = -1
        var pointerCount = 0
        val labels = mutableListOf<String>()
        while (true) {
            ensureAvailable(data, offset, 1)
            val length = data[offset].toInt() and 0xFF
            when {
                length == 0 -> {
                    if (nextOffset < 0) nextOffset = offset + 1
                    break
                }
                length and POINTER_MASK == POINTER_VALUE -> {
                    ensureAvailable(data, offset, 2)
                    if (nextOffset < 0) nextOffset = offset + 2
                    offset = ((length and POINTER_OFFSET_MASK) shl 8) or
                        (data[offset + 1].toInt() and 0xFF)
                    pointerCount += 1
                    if (pointerCount > MAX_POINTER_DEPTH) throw IOException("DNS 名称压缩指针异常")
                }
                length > MAX_LABEL_BYTES -> throw IOException("DNS 名称标签长度无效")
                else -> {
                    ensureAvailable(data, offset + 1, length)
                    labels += String(data, offset + 1, length, StandardCharsets.US_ASCII)
                    offset += length + 1
                }
            }
        }
        return DecodedName(labels.joinToString("."), nextOffset)
    }

    private fun readUnsignedShort(data: ByteArray, offset: Int): Int {
        ensureAvailable(data, offset, 2)
        return ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
    }

    private fun ensureAvailable(data: ByteArray, offset: Int, length: Int) {
        if (offset < 0 || length < 0 || offset > data.size - length) {
            throw IOException("DNS 响应格式无效")
        }
    }

    private data class ParsedResponse(
        val addresses: List<InetAddress> = emptyList(),
        val canonicalName: String? = null
    )

    private data class DecodedName(
        val value: String,
        val nextOffset: Int
    )

    private companion object {
        const val DNS_PORT = 53
        const val NETWORK_TIMEOUT_MS = 3_000
        const val DNS_HEADER_BYTES = 12
        const val FLAGS_OFFSET = 2
        const val QUESTION_COUNT_OFFSET = 4
        const val ANSWER_COUNT_OFFSET = 6
        const val QUESTION_TRAILER_BYTES = 4
        const val RECORD_HEADER_BYTES = 10
        const val MAX_UDP_RESPONSE_BYTES = 4_096
        const val MAX_TCP_RESPONSE_BYTES = 65_535
        const val MAX_DOMAIN_BYTES = 253
        const val MAX_LABEL_BYTES = 63
        const val MAX_POINTER_DEPTH = 32
        const val MAX_CNAME_DEPTH = 8
        const val DNS_CLASS_IN = 1
        const val RECORD_TYPE_A = 1
        const val RECORD_TYPE_CNAME = 5
        const val RECORD_TYPE_AAAA = 28
        const val IPV4_BYTES = 4
        const val IPV6_BYTES = 16
        const val FLAG_RECURSION_DESIRED = 0x0100
        const val FLAG_TRUNCATED = 0x0200
        const val FLAG_RESPONSE = 0x8000
        const val RESPONSE_CODE_MASK = 0x000F
        const val RESPONSE_CODE_NO_ERROR = 0
        const val RESPONSE_CODE_NAME_ERROR = 3
        const val POINTER_MASK = 0xC0
        const val POINTER_VALUE = 0xC0
        const val POINTER_OFFSET_MASK = 0x3F
    }
}
