package com.asmr.player.subtitle

import com.asmr.player.util.SubtitleMatchSupport

internal object SubtitleGenerationPolicy {
    fun supportsFileName(fileName: String): Boolean {
        return fileName.substringAfterLast('.', "").lowercase() in SubtitleMatchSupport.AudioExtensions
    }
}
