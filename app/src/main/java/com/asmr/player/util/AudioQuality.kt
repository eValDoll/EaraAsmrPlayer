package com.asmr.player.util

internal enum class AudioQuality { HQ, SQ }

internal data class AudioTechnicalMetadata(
    val sampleRate: Int = 0,
    val bitrate: Int = 0,
    val channelCount: Int = 0,
    val bitsPerSample: Int = 0,
    val mimeType: String = "",
    val durationSeconds: Double = 0.0,
) {
    val quality: AudioQuality?
        get() {
            if (sampleRate < 44_100) return null
            if (mimeType in LosslessMimeTypes && bitsPerSample >= 16) return AudioQuality.SQ
            return if (bitrate >= 192_000) AudioQuality.HQ else null
        }
}

private val LosslessMimeTypes = setOf(
    "audio/raw", "audio/flac", "audio/x-flac", "audio/alac", "audio/x-alac",
    "audio/wav", "audio/x-wav", "audio/wave", "audio/aiff", "audio/x-aiff",
    "audio/ape", "audio/x-ape", "audio/wavpack", "audio/x-wavpack",
)
