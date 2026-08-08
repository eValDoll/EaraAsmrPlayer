package com.asmr.player.subtitle

internal object SubtitleGenerationPolicy {
    private val supportedAudioExtensions = setOf("mp3", "wav")

    fun supportsFileName(fileName: String): Boolean {
        return fileName.substringAfterLast('.', "").lowercase() in supportedAudioExtensions
    }
}
