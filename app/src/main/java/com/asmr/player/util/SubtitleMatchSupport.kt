package com.asmr.player.util

internal data class SubtitleMatchCandidate(
    val relativePath: String,
    val sourceRef: String,
    val language: String,
    val stablePath: String
)

internal object SubtitleMatchSupport {
    val AudioExtensions: Set<String> = setOf("mp3", "flac", "wav", "m4a", "ogg", "aac", "opus")
    val VideoExtensions: Set<String> = setOf("mp4", "m4v", "mkv", "webm", "mov")
    val SubtitleExtensions: Set<String> = setOf("lrc", "srt", "vtt")
    val PreferredLanguages: List<String> = listOf("default", "zh", "cn", "chs", "ja", "jp", "jpn", "en")

    fun inferCandidate(relativePath: String, sourceRef: String = relativePath): SubtitleMatchCandidate? {
        val normalizedPath = normalizePath(relativePath)
        if (normalizedPath.isBlank()) return null

        val fileName = normalizedPath.substringAfterLast('/')
        val extension = fileName.substringAfterLast('.', "").lowercase()
        if (!SubtitleExtensions.contains(extension)) return null

        val language = inferLanguage(fileName.substringBeforeLast('.'))
        return SubtitleMatchCandidate(
            relativePath = normalizedPath,
            sourceRef = sourceRef,
            language = language,
            stablePath = normalizedPath.lowercase()
        )
    }

    fun matchBest(
        mediaRelativePath: String,
        candidates: Iterable<SubtitleMatchCandidate>
    ): SubtitleMatchCandidate? {
        val mediaPath = normalizePath(mediaRelativePath)
        if (mediaPath.isBlank()) return null

        val mediaFileName = mediaPath.substringAfterLast('/')
        val mediaStem = mediaFileName.substringBeforeLast('.').lowercase()
        val mediaFileNameLower = mediaFileName.lowercase()
        val mediaDirSegments = directorySegments(mediaPath)

        return candidates
            .mapNotNull { candidate ->
                val candidateKeys = candidateBaseNames(candidate.relativePath)
                if (candidateKeys.none { key -> key == mediaStem || key == mediaFileNameLower }) {
                    return@mapNotNull null
                }

                val candidateDirSegments = directorySegments(candidate.relativePath)
                val commonPrefixLength = commonPrefixLength(mediaDirSegments, candidateDirSegments)
                val distance = (mediaDirSegments.size - commonPrefixLength) +
                    (candidateDirSegments.size - commonPrefixLength)
                MatchRank(
                    candidate = candidate,
                    distance = distance,
                    commonPrefixLength = commonPrefixLength,
                    languageRank = languageRank(candidate.language)
                )
            }
            .sortedWith(
                compareBy<MatchRank> { it.distance }
                    .thenByDescending { it.commonPrefixLength }
                    .thenBy { it.languageRank }
                    .thenBy { it.candidate.stablePath }
            )
            .firstOrNull()
            ?.candidate
    }

    private fun inferLanguage(baseName: String): String {
        val tokens = baseName.split('.').filter { it.isNotBlank() }
        val last = tokens.lastOrNull().orEmpty().lowercase()
        return if (isLanguageToken(last)) last else "default"
    }

    private fun candidateBaseNames(relativePath: String): Set<String> {
        val fileName = normalizePath(relativePath).substringAfterLast('/')
        val extRemovedBase = fileName.substringBeforeLast('.').trim().lowercase()
        if (extRemovedBase.isBlank()) return emptySet()

        val keys = linkedSetOf<String>()
        fun addKey(value: String) {
            val normalized = value.trim().lowercase()
            if (normalized.isNotBlank()) keys.add(normalized)
        }

        addKey(extRemovedBase)

        val parts = extRemovedBase.split('.').filter { it.isNotBlank() }
        val lastPart = parts.lastOrNull().orEmpty().lowercase()
        val hasLanguageSuffix = isLanguageToken(lastPart)
        if (hasLanguageSuffix && extRemovedBase.contains('.')) {
            val withoutLang = extRemovedBase.substringBeforeLast('.').trim()
            if (withoutLang.isNotBlank()) {
                addKey(withoutLang)
                val mediaExtToken = withoutLang.substringAfterLast('.', "").lowercase()
                if (isMediaExtensionToken(mediaExtToken) && withoutLang.contains('.')) {
                    addKey(withoutLang.substringBeforeLast('.').trim())
                }
            }
        }

        val mediaExtToken = extRemovedBase.substringAfterLast('.', "").lowercase()
        if (isMediaExtensionToken(mediaExtToken) && extRemovedBase.contains('.')) {
            addKey(extRemovedBase.substringBeforeLast('.').trim())
        }

        return keys
    }

    private fun isLanguageToken(value: String): Boolean {
        if (value.length !in 2..4) return false
        if (!value.all { it in 'a'..'z' }) return false
        return !isMediaExtensionToken(value)
    }

    private fun isMediaExtensionToken(value: String): Boolean {
        return AudioExtensions.contains(value) || VideoExtensions.contains(value)
    }

    private fun normalizePath(path: String): String {
        return path.replace('\\', '/').trim().trimStart('/').replace(Regex("/+"), "/")
    }

    private fun directorySegments(path: String): List<String> {
        val normalized = normalizePath(path)
        if (!normalized.contains('/')) return emptyList()
        return normalized.substringBeforeLast('/')
            .split('/')
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
    }

    private fun commonPrefixLength(a: List<String>, b: List<String>): Int {
        val limit = minOf(a.size, b.size)
        for (index in 0 until limit) {
            if (a[index] != b[index]) return index
        }
        return limit
    }

    private fun languageRank(language: String): Int {
        val normalized = language.trim().lowercase()
        val index = PreferredLanguages.indexOf(normalized)
        return if (index >= 0) index else Int.MAX_VALUE
    }

    private data class MatchRank(
        val candidate: SubtitleMatchCandidate,
        val distance: Int,
        val commonPrefixLength: Int,
        val languageRank: Int
    )
}
