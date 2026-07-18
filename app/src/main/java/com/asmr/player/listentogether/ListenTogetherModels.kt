package com.asmr.player.listentogether

data class ListenTogetherTrackIdentity(
    val albumKey: String,
    val mediaFingerprint: String,
    val fileSizeBytes: Long,
    val fingerprintAlgorithm: String,
    val mediaKind: ListenTogetherMediaKind,
    val sourcePath: String,
    val mediaId: String,
    val rjCode: String,
    val displayTitle: String,
    val displayArtist: String
) {
    val sessionKey: String
        get() = "$albumKey:$mediaFingerprint"
}

enum class ListenTogetherMediaKind {
    AUDIO,
    VIDEO
}

data class ListenTogetherPresencePayload(
    val sessionKey: String,
    val albumKey: String,
    val mediaFingerprint: String,
    val fileSizeBytes: Long,
    val fingerprintAlgorithm: String,
    val mediaKind: String,
    val sourcePathHint: String,
    val mediaId: String,
    val rjCode: String,
    val title: String,
    val artist: String,
    val playbackPositionMs: Long,
    val isPlaying: Boolean,
    val sentAtEpochMs: Long,
    val clientSessionId: String,
    val deviceId: String,
    val appVersion: String? = null
)

data class ListenTogetherLeavePayload(
    val sessionKey: String,
    val clientSessionId: String,
    val deviceId: String,
    val sentAtEpochMs: Long
)

data class ListenTogetherPresenceResponse(
    val listenerCount: Int,
    val sessionKey: String,
    val heartbeatIntervalMs: Long = 15_000L,
    val expiresInMs: Long? = null,
    val serverTimeEpochMs: Long? = null
)

data class ListenTogetherRjSummaryResponse(
    val rjCode: String,
    val listenerCount: Int,
    val sessionCount: Int = 0,
    val serverTimeEpochMs: Long? = null
)

data class ListenTogetherUiState(
    val available: Boolean = false,
    val listenerCount: Int? = null,
    val currentIdentity: ListenTogetherTrackIdentity? = null,
    val syncing: Boolean = false,
    val backendConfigured: Boolean = false,
    val status: ListenTogetherStatus = ListenTogetherStatus.Preparing
)

enum class ListenTogetherStatus {
    Preparing,
    Ready,
    OnlineSkipped,
    Unsupported,
    BackendUnavailable,
    Error
}
