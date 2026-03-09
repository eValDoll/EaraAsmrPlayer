package com.asmr.player.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TrackKeyNormalizerTest {
    @Test
    fun normalizeTitle_stripsExtensionAndIndexPrefix() {
        val a = TrackKeyNormalizer.normalizeTitle("01_ Hello-World.mp3")
        val b = TrackKeyNormalizer.normalizeTitle("Hello World")
        assertEquals(b, a)
    }

    @Test
    fun buildKey_nameKey_respectsGroupWhenPresent() {
        val a = TrackKeyNormalizer.buildKey("Track A", "Disc 1", null)
        val b = TrackKeyNormalizer.buildKey("Track A", "Disc 2", null)
        assertNotEquals(a, b)
    }

    @Test
    fun buildKey_nameKey_noGroupMatches() {
        val a = TrackKeyNormalizer.buildKey("01 Track A", "", null)
        val b = TrackKeyNormalizer.buildKey("Track A", "", null)
        assertEquals(a, b)
    }

    @Test
    fun normalizeRelativePath_removesExtensionAndNormalizesSeparators() {
        val a = TrackKeyNormalizer.normalizeRelativePath("\\Disc 1\\01 Track A.MP3")
        assertEquals("disc 1/01 track a", a)
    }

    @Test
    fun subtitleMigration_matchingKey_findsLocalTarget() {
        val localId = 42L
        val localKey = TrackKeyNormalizer.buildKey("01 Track A", "Disc 1", null)
        val localKeyNoGroup = TrackKeyNormalizer.buildKey("01 Track A", "", null)
        val localMap = linkedMapOf(localKey to localId, localKeyNoGroup to localId)

        val onlineKey = TrackKeyNormalizer.buildKey("Track A", "Disc 1", null)
        val onlineKeyNoGroup = TrackKeyNormalizer.buildKey("Track A", "", null)
        val targetId = localMap[onlineKey] ?: localMap[onlineKeyNoGroup]

        assertEquals(localId, targetId)
    }
}
