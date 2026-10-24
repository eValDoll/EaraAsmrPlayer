package com.asmr.player

import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardRjNavigationTest {
    @Test
    fun entryInspection_checksColdEntryOnceAfterResumeAndFocus() {
        val inspection = ClipboardEntryInspection()

        assertFalse(inspection.consumeIfReady(true, true, Lifecycle.State.STARTED))
        inspection.onLifecycleEvent(Lifecycle.Event.ON_RESUME)
        assertFalse(inspection.consumeIfReady(true, false, Lifecycle.State.RESUMED))
        assertTrue(inspection.consumeIfReady(true, true, Lifecycle.State.RESUMED))
        assertFalse(inspection.consumeIfReady(true, true, Lifecycle.State.RESUMED))
    }

    @Test
    fun entryInspection_doesNotCheckAgainAfterInAppCopyOrWindowFocusChange() {
        val inspection = ClipboardEntryInspection()
        assertTrue(inspection.consumeIfReady(true, true, Lifecycle.State.RESUMED))

        assertFalse(inspection.consumeIfReady(true, false, Lifecycle.State.RESUMED))
        val inAppCopyEvent = clipboardEventKey("RJ123456", timestampMillis = 100L)
        assertFalse(inspection.consumeIfReady(true, true, Lifecycle.State.RESUMED))
        assertFalse(
            shouldShowClipboardRjPrompt(
                detectedRj = "RJ123456",
                eventKey = inAppCopyEvent,
                lastHandledEventKey = inAppCopyEvent,
                currentPromptEventKey = null
            )
        )
    }

    @Test
    fun entryInspection_doesNotTreatTemporaryPauseAsBackgroundEntry() {
        val inspection = ClipboardEntryInspection()
        assertTrue(inspection.consumeIfReady(true, true, Lifecycle.State.RESUMED))

        inspection.onLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        assertFalse(inspection.consumeIfReady(true, false, Lifecycle.State.STARTED))
        inspection.onLifecycleEvent(Lifecycle.Event.ON_RESUME)

        assertFalse(inspection.consumeIfReady(true, true, Lifecycle.State.RESUMED))
    }

    @Test
    fun entryInspection_checksOnceAfterReturningFromBackground() {
        val inspection = ClipboardEntryInspection()
        assertTrue(inspection.consumeIfReady(true, true, Lifecycle.State.RESUMED))

        inspection.onLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        inspection.onLifecycleEvent(Lifecycle.Event.ON_STOP)
        assertFalse(inspection.consumeIfReady(true, false, Lifecycle.State.CREATED))
        inspection.onLifecycleEvent(Lifecycle.Event.ON_START)
        assertFalse(inspection.consumeIfReady(true, true, Lifecycle.State.STARTED))
        inspection.onLifecycleEvent(Lifecycle.Event.ON_RESUME)
        assertFalse(inspection.consumeIfReady(true, false, Lifecycle.State.RESUMED))

        assertTrue(inspection.consumeIfReady(true, true, Lifecycle.State.RESUMED))
        assertFalse(inspection.consumeIfReady(true, true, Lifecycle.State.RESUMED))
    }

    @Test
    fun entryInspection_waitsWhileDisabledWithoutRearmingConsumedEntry() {
        val inspection = ClipboardEntryInspection()

        assertFalse(inspection.consumeIfReady(false, true, Lifecycle.State.RESUMED))
        assertTrue(inspection.consumeIfReady(true, true, Lifecycle.State.RESUMED))
        assertFalse(inspection.consumeIfReady(false, true, Lifecycle.State.RESUMED))
        assertFalse(inspection.consumeIfReady(true, true, Lifecycle.State.RESUMED))
    }

    @Test
    fun extractClipboardRjCode_returnsFirstCode() {
        assertEquals(
            "RJ123456",
            extractClipboardRjCode("先看 rj123456，之后再看 RJ999999")
        )
    }

    @Test
    fun extractClipboardRjCode_supportsDlsiteUrl() {
        assertEquals(
            "RJ01522140",
            extractClipboardRjCode("https://www.dlsite.com/maniax/work/=/product_id/RJ01522140.html")
        )
    }

    @Test
    fun extractClipboardRjCode_supportsBooksAndProfessionalWorkNumbers() {
        assertEquals("BJ02370869", extractClipboardRjCode("复制 bj02370869 后打开"))
        assertEquals("VJ01005620", extractClipboardRjCode("https://www.dlsite.com/pro/work/=/product_id/VJ01005620.html"))
    }

    @Test
    fun extractClipboardRjCode_returnsBlankWithoutCode() {
        assertEquals("", extractClipboardRjCode("普通剪贴板内容"))
    }

    @Test
    fun clipboardEventKey_distinguishesSameContentCopiedAgain() {
        val firstCopy = clipboardEventKey("RJ123456", timestampMillis = 100L)
        val secondCopy = clipboardEventKey("RJ123456", timestampMillis = 200L)

        assertFalse(firstCopy == secondCopy)
    }

    @Test
    fun clipboardEventKey_keepsSameCopyEventStable() {
        val firstRead = clipboardEventKey("RJ123456", timestampMillis = 100L)
        val secondRead = clipboardEventKey("RJ123456", timestampMillis = 100L)

        assertEquals(firstRead, secondRead)
    }

    @Test
    fun clipboardEventKey_legacyGenerationDistinguishesSameContentCopiedAgain() {
        val firstCopy = clipboardEventKey("RJ123456", timestampMillis = 0L, legacyChangeGeneration = 1L)
        val secondCopy = clipboardEventKey("RJ123456", timestampMillis = 0L, legacyChangeGeneration = 2L)

        assertFalse(firstCopy == secondCopy)
    }

    @Test
    fun shouldShowClipboardRjPrompt_rejectsAlreadyHandledCopyEvent() {
        assertFalse(
            shouldShowClipboardRjPrompt(
                detectedRj = "RJ123456",
                eventKey = "event-1",
                lastHandledEventKey = "event-1",
                currentPromptEventKey = null
            )
        )
    }

    @Test
    fun shouldShowClipboardRjPrompt_rejectsCopyEventAlreadyBeingPrompted() {
        assertFalse(
            shouldShowClipboardRjPrompt(
                detectedRj = "RJ123456",
                eventKey = "event-1",
                lastHandledEventKey = null,
                currentPromptEventKey = "event-1"
            )
        )
    }

    @Test
    fun shouldShowClipboardRjPrompt_acceptsSameRjFromNewCopyEvent() {
        assertTrue(
            shouldShowClipboardRjPrompt(
                detectedRj = "RJ123456",
                eventKey = "event-2",
                lastHandledEventKey = "event-1",
                currentPromptEventKey = null
            )
        )
    }

    @Test
    fun shouldShowClipboardRjPrompt_acceptsNewRjFromNewCopyEvent() {
        assertTrue(
            shouldShowClipboardRjPrompt(
                detectedRj = "RJ654321",
                eventKey = "event-2",
                lastHandledEventKey = "event-1",
                currentPromptEventKey = null
            )
        )
    }
}
