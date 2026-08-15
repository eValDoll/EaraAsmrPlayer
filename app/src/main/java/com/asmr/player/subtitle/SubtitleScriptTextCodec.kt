package com.asmr.player.subtitle

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * 台本/文本文件的编码识别与解码。
 *
 * 顺序：先按 BOM 识别 UTF-8 / UTF-16LE / UTF-16BE；无 BOM 时按
 * UTF-8 → Shift-JIS → GBK 依次做“严格”解码（非法字节直接报错而非替换），
 * 取第一个能完整解码的编码；全部失败则退回宽松 UTF-8 兜底。
 *
 * 局限（诚实说明）：Shift-JIS / GBK / EUC-JP 等 CJK 编码的字节区间互相重叠，
 * 纯字节判断无法 100% 区分——例如部分 GBK 中文、以及绝大多数 EUC-JP 文本在
 * Shift-JIS 下也能“成功”解码（只是变成乱码）。因此：
 * - UTF-8 / UTF-16（带 BOM）能可靠识别；
 * - 非 UTF-8 的文本默认按 Shift-JIS（日文台本最常见）解析；
 * - GBK 仅在字节恰好不是合法 Shift-JIS 时才生效。
 * 需要更强识别时（如区分 EUC-JP / GBK），应改用统计式字符集检测器。
 */
internal object SubtitleScriptTextCodec {

    fun decode(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        if (bytes.startsWith(UTF8_BOM)) return decodeStrict(bytes, UTF8_BOM.size, "UTF-8").orEmpty()
        if (bytes.startsWith(UTF16LE_BOM)) return decodeStrict(bytes, UTF16LE_BOM.size, "UTF-16LE").orEmpty()
        if (bytes.startsWith(UTF16BE_BOM)) return decodeStrict(bytes, UTF16BE_BOM.size, "UTF-16BE").orEmpty()
        for (name in FALLBACK_CHARSETS) {
            decodeStrict(bytes, 0, name)?.let { return it }
        }
        return bytes.toString(Charsets.UTF_8)
    }

    private fun decodeStrict(bytes: ByteArray, offset: Int, charsetName: String): String? = runCatching {
        Charset.forName(charsetName).newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes, offset, bytes.size - offset))
            .toString()
    }.getOrNull()

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    private val FALLBACK_CHARSETS = listOf("UTF-8", "Shift_JIS", "GBK")

    private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    private val UTF16LE_BOM = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
    private val UTF16BE_BOM = byteArrayOf(0xFE.toByte(), 0xFF.toByte())
}
