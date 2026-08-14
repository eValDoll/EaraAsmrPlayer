package com.asmr.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardRjNavigationTest {
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
