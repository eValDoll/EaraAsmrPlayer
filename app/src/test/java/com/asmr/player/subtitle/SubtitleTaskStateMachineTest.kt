package com.asmr.player.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class SubtitleTaskStateMachineTest {
    @Test
    fun generatedItem_onlySucceedsAfterTranslation() {
        SubtitleItemState.requireTransition(SubtitleItemState.QUEUED_TRANSCRIPTION, SubtitleItemState.TRANSCRIBING)
        SubtitleItemState.requireTransition(SubtitleItemState.TRANSCRIBING, SubtitleItemState.QUEUED_TRANSLATION)
        SubtitleItemState.requireTransition(SubtitleItemState.QUEUED_TRANSLATION, SubtitleItemState.TRANSLATING)
        SubtitleItemState.requireTransition(SubtitleItemState.TRANSLATING, SubtitleItemState.SUCCEEDED)

        assertThrows(IllegalArgumentException::class.java) {
            SubtitleItemState.requireTransition(SubtitleItemState.TRANSCRIBING, SubtitleItemState.SUCCEEDED)
        }
    }

    @Test
    fun resume_usesPersistedTranslationSourcesInsteadOfRetranscribing() {
        assertEquals(
            SubtitleItemState.QUEUED_TRANSLATION,
            SubtitleItemState.resumeState(SubtitleItemState.TRANSLATING, hasTranslationSources = true)
        )
        assertEquals(
            SubtitleItemState.QUEUED_TRANSCRIPTION,
            SubtitleItemState.resumeState(SubtitleItemState.TRANSCRIBING, hasTranslationSources = false)
        )
    }

    @Test
    fun translationDispatcher_isGlobalFifoAndCapsAtTen() {
        val candidates = (1..12).map { "translation-$it" }

        assertEquals(
            candidates.take(10),
            SubtitleDispatchPolicy.selectTranslationItems(
                candidates,
                emptySet(),
                DEEPSEEK_TRANSLATION_CONCURRENCY
            )
        )
        assertEquals(
            candidates.drop(2).take(8),
            SubtitleDispatchPolicy.selectTranslationItems(
                candidates,
                candidates.take(2).toSet(),
                DEEPSEEK_TRANSLATION_CONCURRENCY
            )
        )
    }

    @Test
    fun nineTranscriptions_continueEvenWhenTenTranslationsAreActive() {
        val transcriptionQueue = (1..9).map { "audio-$it" }.toMutableList()
        val transcribed = mutableListOf<String>()
        val busyTranslationSlots = (1..DEEPSEEK_TRANSLATION_CONCURRENCY).mapTo(mutableSetOf()) {
            "translation-$it"
        }

        while (transcriptionQueue.isNotEmpty()) {
            val next = SubtitleDispatchPolicy.selectTranscriptionItem(
                orderedCandidates = transcriptionQueue,
                transcriptionActive = false
            )
            assertEquals(DEEPSEEK_TRANSLATION_CONCURRENCY, busyTranslationSlots.size)
            transcribed += checkNotNull(next)
            transcriptionQueue.removeAt(0)
        }

        assertEquals((1..9).map { "audio-$it" }, transcribed)
    }

    @Test
    fun cancellationRestore_requiresTaskOwnedPublishedHash() {
        assertTrue(taskStillControlsSubtitles("published", "published"))
        assertFalse(taskStillControlsSubtitles("user-import", "published"))
        assertFalse(taskStillControlsSubtitles("", ""))
    }

    @Test
    fun pauseAndCancelTransitions_coverRacingControlRequests() {
        SubtitleItemState.requireTransition(SubtitleItemState.TRANSLATING, SubtitleItemState.PAUSE_REQUESTED)
        SubtitleItemState.requireTransition(SubtitleItemState.PAUSE_REQUESTED, SubtitleItemState.PAUSED)
        SubtitleItemState.requireTransition(SubtitleItemState.PAUSE_REQUESTED, SubtitleItemState.CANCEL_REQUESTED)
        SubtitleItemState.requireTransition(SubtitleItemState.CANCEL_REQUESTED, SubtitleItemState.CANCELED)

        assertThrows(IllegalArgumentException::class.java) {
            SubtitleItemState.requireTransition(SubtitleItemState.SUCCEEDED, SubtitleItemState.CANCEL_REQUESTED)
        }
    }
}
