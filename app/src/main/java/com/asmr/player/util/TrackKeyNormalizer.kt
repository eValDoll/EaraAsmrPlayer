package com.asmr.player.util

import java.text.Normalizer

object TrackKeyNormalizer {
    private fun nfkcLower(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return ""
        val nfkc = Normalizer.normalize(trimmed, Normalizer.Form.NFKC)
        return nfkc.lowercase()
    }

    private fun collapseToToken(input: String): String {
        val lower = nfkcLower(input)
        if (lower.isBlank()) return ""
        val replaced = lower
            .replace('·', ' ')
            .replace('_', ' ')
            .replace('-', ' ')
            .replace('－', ' ')
            .replace('—', ' ')
            .replace('/', ' ')
            .replace('\\', ' ')
            .replace('|', ' ')
            .replace('　', ' ')
        return replaced.replace(Regex("\\s+"), "")
    }

    fun normalizeGroup(group: String): String = collapseToToken(group)

    fun normalizeTitle(title: String): String {
        var t = nfkcLower(title)
        if (t.isBlank()) return ""

        t = t.replace(Regex("\\.[a-z0-9]{2,5}$", RegexOption.IGNORE_CASE), "")

        val patterns = listOf(
            Regex("""^\s*[\[(（【]?\s*\d{1,3}\s*[\])）】]?\s*[-_.\s]+"""),
            Regex("""^\s*\d{1,3}\s*[-_.\s]+"""),
            Regex("""^\s*(track|trk)\s*\d{1,3}\s*[-_.\s]+""", RegexOption.IGNORE_CASE),
            Regex("""^\s*\d{1,3}\s*[:：]\s*""")
        )
        patterns.forEach { re ->
            t = t.replace(re, "")
        }

        return collapseToToken(t)
    }

    fun normalizeRelativePath(relativePath: String): String {
        var p = nfkcLower(relativePath)
        if (p.isBlank()) return ""
        p = p.replace('\\', '/').trim().trimStart('/').trimStart('.').trimStart('/')
        p = p.replace(Regex("/+"), "/")
        p = p.replace(Regex("\\.[a-z0-9]{2,5}$", RegexOption.IGNORE_CASE), "")
        return p
    }

    fun tryRelativePathFromFilePath(filePath: String, rootDir: String): String? {
        val file = filePath.trim()
        val root = rootDir.trim()
        if (file.isBlank() || root.isBlank()) return null

        val fileNorm = file.replace('\\', '/')
        val rootNorm = root.replace('\\', '/').trimEnd('/')
        if (!fileNorm.startsWith(rootNorm + "/")) return null
        val rel = fileNorm.removePrefix(rootNorm + "/")
        return rel.takeIf { it.isNotBlank() }
    }

    fun buildKey(
        title: String,
        group: String,
        relativePath: String?
    ): String {
        val rel = relativePath?.let { normalizeRelativePath(it) }.orEmpty()
        return if (rel.isNotBlank()) {
            "rel\u0000$rel"
        } else {
            val t = normalizeTitle(title)
            val g = normalizeGroup(group)
            "name\u0000$t\u0000$g"
        }
    }
}

