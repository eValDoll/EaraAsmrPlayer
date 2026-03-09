package com.asmr.player.util

import java.text.Normalizer

class SmartSortKey private constructor(
    private val tokens: List<Token>,
    private val normalized: String
) : Comparable<SmartSortKey> {
    override fun compareTo(other: SmartSortKey): Int {
        val a = tokens
        val b = other.tokens
        val n = minOf(a.size, b.size)
        for (i in 0 until n) {
            val c = compareToken(a[i], b[i])
            if (c != 0) return c
        }
        val lenCmp = a.size.compareTo(b.size)
        if (lenCmp != 0) return lenCmp
        return normalized.compareTo(other.normalized)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SmartSortKey) return false
        return normalized == other.normalized
    }

    override fun hashCode(): Int = normalized.hashCode()

    companion object {
        fun of(input: String): SmartSortKey {
            val normalized = normalizeInput(input)
            if (normalized.isBlank()) return SmartSortKey(emptyList(), "")
            val tokens = tokenize(normalized)
            return SmartSortKey(tokens, normalized)
        }
    }
}

private sealed interface Token

private data class NumToken(val value: Long) : Token

private data class TextToken(val value: String) : Token

private fun compareToken(a: Token, b: Token): Int {
    return when {
        a is NumToken && b is NumToken -> a.value.compareTo(b.value)
        a is TextToken && b is TextToken -> a.value.compareTo(b.value)
        a is NumToken && b is TextToken -> -1
        a is TextToken && b is NumToken -> 1
        else -> 0
    }
}

private fun normalizeInput(input: String): String {
    val trimmed = input.trim()
    if (trimmed.isBlank()) return ""
    val nfkc = Normalizer.normalize(trimmed, Normalizer.Form.NFKC).lowercase()
    return nfkc
        .replace('·', ' ')
        .replace('_', ' ')
        .replace('-', ' ')
        .replace('－', ' ')
        .replace('—', ' ')
        .replace('/', ' ')
        .replace('\\', ' ')
        .replace('|', ' ')
        .replace('　', ' ')
        .replace('.', ' ')
        .replace('。', ' ')
        .replace('、', ' ')
        .replace(',', ' ')
        .replace('，', ' ')
        .replace(':', ' ')
        .replace('：', ' ')
        .replace(';', ' ')
        .replace('；', ' ')
        .replace('（', ' ')
        .replace('）', ' ')
        .replace('(', ' ')
        .replace(')', ' ')
        .replace('[', ' ')
        .replace(']', ' ')
        .replace('【', ' ')
        .replace('】', ' ')
        .replace('《', ' ')
        .replace('》', ' ')
        .replace('「', ' ')
        .replace('」', ' ')
        .replace('『', ' ')
        .replace('』', ' ')
        .replace('{', ' ')
        .replace('}', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun tokenize(input: String): List<Token> {
    val out = ArrayList<Token>(8)
    var i = 0
    while (i < input.length) {
        val ch = input[i]
        if (ch.isWhitespace()) {
            i++
            continue
        }
        if (ch.isDigit()) {
            val start = i
            i++
            while (i < input.length && input[i].isDigit()) i++
            val raw = input.substring(start, i)
            val v = raw.toLongOrNull()
            out.add(NumToken(v ?: Long.MAX_VALUE))
            continue
        }
        if (isChineseNumberChar(ch)) {
            val start = i
            i++
            while (i < input.length && isChineseNumberChar(input[i])) i++
            val raw = input.substring(start, i)
            val v = parseChineseNumber(raw)
            if (v != null) out.add(NumToken(v)) else out.add(TextToken(raw))
            continue
        }
        val start = i
        i++
        while (i < input.length) {
            val c = input[i]
            if (c.isWhitespace() || c.isDigit() || isChineseNumberChar(c)) break
            i++
        }
        out.add(TextToken(input.substring(start, i)))
    }
    return out
}

private fun isChineseNumberChar(ch: Char): Boolean {
    return ch == '零' ||
        ch == '〇' ||
        ch == '一' ||
        ch == '二' ||
        ch == '两' ||
        ch == '三' ||
        ch == '四' ||
        ch == '五' ||
        ch == '六' ||
        ch == '七' ||
        ch == '八' ||
        ch == '九' ||
        ch == '十' ||
        ch == '百' ||
        ch == '千' ||
        ch == '万' ||
        ch == '亿'
}

private fun parseChineseNumber(raw: String): Long? {
    if (raw.isBlank()) return null
    val digits = mapOf(
        '零' to 0L,
        '〇' to 0L,
        '一' to 1L,
        '二' to 2L,
        '两' to 2L,
        '三' to 3L,
        '四' to 4L,
        '五' to 5L,
        '六' to 6L,
        '七' to 7L,
        '八' to 8L,
        '九' to 9L
    )
    val smallUnits = mapOf(
        '十' to 10L,
        '百' to 100L,
        '千' to 1000L
    )
    val bigUnits = mapOf(
        '万' to 10_000L,
        '亿' to 100_000_000L
    )
    val hasUnit = raw.any { it == '十' || it == '百' || it == '千' || it == '万' || it == '亿' }
    if (!hasUnit && raw.all { digits.containsKey(it) }) {
        var v = 0L
        for (ch in raw) {
            v = v * 10L + (digits[ch] ?: return null)
        }
        return v
    }

    var total = 0L
    var section = 0L
    var number = 0L
    var ok = false
    for (ch in raw) {
        val d = digits[ch]
        if (d != null) {
            number = d
            ok = true
            continue
        }
        val su = smallUnits[ch]
        if (su != null) {
            ok = true
            val n = if (number == 0L) 1L else number
            section += n * su
            number = 0L
            continue
        }
        val bu = bigUnits[ch]
        if (bu != null) {
            ok = true
            section += number
            if (section == 0L) section = 1L
            total += section * bu
            section = 0L
            number = 0L
            continue
        }
        return null
    }
    if (!ok) return null
    return total + section + number
}

