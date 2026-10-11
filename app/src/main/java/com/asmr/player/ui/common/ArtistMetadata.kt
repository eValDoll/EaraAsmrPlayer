package com.asmr.player.ui.common

internal const val CvNameSeparator = "、"
internal const val ArtistSectionSeparator = " / "

internal fun parseCvNames(text: String): List<String> = text
    .split(',', '，', '、', '/', '\n', ';', '；', '|')
    .map(String::trim)
    .filter(String::isNotBlank)
    .distinct()

internal fun formatCvNames(text: String): String = parseCvNames(text).joinToString(CvNameSeparator)

internal fun formatStoredCv(artist: String, albumCv: String? = null): String =
    formatCvNames(albumCv ?: artist.substringAfter(ArtistSectionSeparator, ""))

internal fun formatArtistMetadata(circle: String, cv: String): String =
    listOf(circle.trim(), formatCvNames(cv))
        .filter(String::isNotBlank)
        .joinToString(ArtistSectionSeparator)

// 历史队列保存的是组合字符串；收藏还可能额外携带同一份专辑 CV。
internal fun formatStoredArtist(artist: String, albumCv: String = ""): String {
    val parts = artist.trim().split(ArtistSectionSeparator, limit = 2)
    if (parts.size == 2) return formatArtistMetadata(parts[0], parts[1].ifBlank { albumCv })
    val cv = formatCvNames(albumCv)
    val normalizedArtist = formatCvNames(artist)
    return when {
        cv.isBlank() -> normalizedArtist
        normalizedArtist.isBlank() || normalizedArtist == cv -> cv
        else -> formatArtistMetadata(artist, cv)
    }
}
