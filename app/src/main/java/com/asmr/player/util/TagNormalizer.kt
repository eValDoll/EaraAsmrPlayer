package com.asmr.player.util

import java.text.Normalizer

object TagNormalizer {
    fun normalize(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return ""
        val nfkc = Normalizer.normalize(trimmed, Normalizer.Form.NFKC)
        val lower = nfkc.lowercase()
        val replaced = lower
            .replace('·', ' ')
            .replace('_', ' ')
            .replace('-', ' ')
            .replace('－', ' ')
            .replace('—', ' ')
            .replace('/', ' ')
            .replace('\\', ' ')
        val collapsedSpaces = replaced.replace(Regex("\\s+"), "")
        return collapsedSpaces
    }
}

