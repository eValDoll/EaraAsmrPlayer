package com.asmr.player.subtitle

internal object SubtitleTaskOrigin {
    const val GENERATED = "GENERATED"
    const val MANUAL_TRANSLATION = "MANUAL_TRANSLATION"
}

internal object SubtitleTaskMode {
    const val GENERATED = "GENERATED"
    const val MANUAL = "MANUAL"
}

internal object SubtitleTaskState {
    const val ACTIVE = "ACTIVE"
    const val PAUSE_REQUESTED = "PAUSE_REQUESTED"
    const val PAUSED = "PAUSED"
    const val INTERRUPTED = "INTERRUPTED"
    const val CANCEL_REQUESTED = "CANCEL_REQUESTED"
    const val FAILED = "FAILED"
}

internal object SubtitleItemState {
    const val QUEUED_TRANSCRIPTION = "QUEUED_TRANSCRIPTION"
    const val TRANSCRIBING = "TRANSCRIBING"
    const val QUEUED_TRANSLATION = "QUEUED_TRANSLATION"
    const val WAITING_SLOT = "WAITING_SLOT"
    const val WAITING_NETWORK = "WAITING_NETWORK"
    const val RETRY_WAIT = "RETRY_WAIT"
    const val TRANSLATING = "TRANSLATING"
    const val PAUSE_REQUESTED = "PAUSE_REQUESTED"
    const val PAUSED = "PAUSED"
    const val INTERRUPTED = "INTERRUPTED"
    const val CANCEL_REQUESTED = "CANCEL_REQUESTED"
    const val FAILED = "FAILED"
    const val SUCCEEDED = "SUCCEEDED"
    const val CANCELED = "CANCELED"

    private val transitions = mapOf(
        QUEUED_TRANSCRIPTION to setOf(TRANSCRIBING, PAUSE_REQUESTED, CANCEL_REQUESTED, INTERRUPTED, FAILED),
        TRANSCRIBING to setOf(QUEUED_TRANSLATION, PAUSE_REQUESTED, CANCEL_REQUESTED, INTERRUPTED, FAILED),
        QUEUED_TRANSLATION to setOf(WAITING_SLOT, WAITING_NETWORK, TRANSLATING, PAUSE_REQUESTED, CANCEL_REQUESTED, INTERRUPTED, FAILED),
        WAITING_SLOT to setOf(QUEUED_TRANSLATION, WAITING_NETWORK, TRANSLATING, PAUSE_REQUESTED, CANCEL_REQUESTED, INTERRUPTED, FAILED),
        WAITING_NETWORK to setOf(QUEUED_TRANSLATION, WAITING_SLOT, TRANSLATING, PAUSE_REQUESTED, CANCEL_REQUESTED, INTERRUPTED, FAILED),
        RETRY_WAIT to setOf(QUEUED_TRANSLATION, WAITING_NETWORK, TRANSLATING, PAUSE_REQUESTED, CANCEL_REQUESTED, INTERRUPTED, FAILED),
        TRANSLATING to setOf(QUEUED_TRANSLATION, RETRY_WAIT, WAITING_NETWORK, PAUSE_REQUESTED, CANCEL_REQUESTED, INTERRUPTED, FAILED, SUCCEEDED),
        PAUSE_REQUESTED to setOf(PAUSED, CANCEL_REQUESTED, INTERRUPTED),
        PAUSED to setOf(QUEUED_TRANSCRIPTION, QUEUED_TRANSLATION, CANCEL_REQUESTED),
        INTERRUPTED to setOf(QUEUED_TRANSCRIPTION, QUEUED_TRANSLATION, CANCEL_REQUESTED),
        FAILED to setOf(QUEUED_TRANSCRIPTION, QUEUED_TRANSLATION, CANCEL_REQUESTED),
        CANCEL_REQUESTED to setOf(CANCELED),
        SUCCEEDED to emptySet(),
        CANCELED to emptySet()
    )

    fun requireTransition(from: String, to: String) {
        require(to in transitions[from].orEmpty()) { "非法字幕任务状态转换：$from → $to" }
    }

    fun resumeState(suspendedFrom: String, hasTranslationSources: Boolean): String {
        return if (
            hasTranslationSources || suspendedFrom in setOf(
                QUEUED_TRANSLATION,
                WAITING_SLOT,
                WAITING_NETWORK,
                RETRY_WAIT,
                TRANSLATING
            )
        ) {
            QUEUED_TRANSLATION
        } else {
            QUEUED_TRANSCRIPTION
        }
    }
}

internal object SubtitleDispatchPolicy {
    fun selectTranscriptionItem(
        orderedCandidates: List<String>,
        transcriptionActive: Boolean
    ): String? = if (transcriptionActive) null else orderedCandidates.firstOrNull()

    fun selectTranslationItems(
        orderedCandidates: List<String>,
        activeItemIds: Set<String>,
        concurrency: Int
    ): List<String> {
        require(concurrency > 0)
        val freeSlots = (concurrency - activeItemIds.size).coerceAtLeast(0)
        return orderedCandidates.asSequence()
            .filterNot(activeItemIds::contains)
            .take(freeSlots)
            .toList()
    }
}

internal data class SubtitleTaskHandle(
    val taskId: String,
    val itemIds: List<String>,
    val reusedExisting: Boolean
)

internal data class SubtitleTaskUi(
    val id: String,
    val title: String,
    val rjCode: String,
    val state: String,
    val warning: String,
    val createdAt: Long,
    val items: List<SubtitleTaskItemUi>
)

internal data class SubtitleTaskItemUi(
    val id: String,
    val taskId: String,
    val trackId: Long,
    val title: String,
    val mode: String,
    val state: String,
    val transcriptionProgress: Int,
    val transcribedMs: Long,
    val totalDurationMs: Long,
    val translationCursor: Int,
    val translationTotal: Int,
    val translationBatchIndex: Int,
    val translationBatchTotal: Int,
    val attempt: Int,
    val nextAttemptAt: Long,
    val errorMessage: String,
    val createdAt: Long
)

internal data class SubtitleGenerationTarget(
    val trackId: Long,
    val title: String
)

internal data class SubtitleTranslationTarget(
    val trackId: Long,
    val title: String
)
