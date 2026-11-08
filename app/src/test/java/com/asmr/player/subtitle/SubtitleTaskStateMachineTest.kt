package com.asmr.player.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleTaskStateMachineTest {
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
    fun albumPolish_isBlockedOnlyByActiveOrTransitioningItems() {
        assertTrue(SubtitleItemState.blocksAlbumPolish(SubtitleItemState.TRANSCRIBING))
        assertTrue(SubtitleItemState.blocksAlbumPolish(SubtitleItemState.TRANSLATING))
        assertTrue(SubtitleItemState.blocksAlbumPolish(SubtitleItemState.PAUSE_REQUESTED))
        assertFalse(SubtitleItemState.blocksAlbumPolish(SubtitleItemState.PAUSED))
        assertFalse(SubtitleItemState.blocksAlbumPolish(SubtitleItemState.FAILED))
        assertFalse(SubtitleItemState.blocksAlbumPolish(SubtitleItemState.SUCCEEDED))
    }

    @Test
    fun subtitleAlbumKey_isCaseAndWhitespaceInsensitive() {
        assertEquals("RJ01413836", "  rj01413836 ".normalizedSubtitleAlbumKey())
    }

    @Test
    fun transcriptionModel_isCapturedOnceAndKeptAcrossResume() {
        val parakeet = SubtitleTranscriptionModels.PARAKEET_TDT_CTC_06B_JA_INT8.id
        val senseVoice = SubtitleTranscriptionModels.SENSE_VOICE_SMALL_INT8.id

        assertEquals(senseVoice, resolveTranscriptionModelId("", senseVoice))
        assertEquals(parakeet, resolveTranscriptionModelId(parakeet, senseVoice))
    }
}
