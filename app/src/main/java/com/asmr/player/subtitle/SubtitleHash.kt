package com.asmr.player.subtitle

import com.asmr.player.data.local.db.entities.SubtitleEntity
import java.security.MessageDigest

internal fun subtitleHash(subtitles: List<SubtitleEntity>): String {
    val digest = MessageDigest.getInstance("SHA-256")
    subtitles.sortedWith(SUBTITLE_ORDER).forEach { subtitle ->
        digest.update(subtitle.startMs.toString().toByteArray(Charsets.UTF_8))
        digest.update(0)
        digest.update(subtitle.endMs.toString().toByteArray(Charsets.UTF_8))
        digest.update(0)
        digest.update(subtitle.text.toByteArray(Charsets.UTF_8))
        digest.update('\n'.code.toByte())
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}

internal fun taskStillControlsSubtitles(currentHash: String, lastPublishedHash: String): Boolean =
    currentHash.isNotBlank() && currentHash == lastPublishedHash
