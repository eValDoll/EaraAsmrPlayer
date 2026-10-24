package com.asmr.player.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistPlaybackTrackerTest {
    @Test
    fun pausedRestoreAndFailedRequestNeverBecomeRecentPlayback() {
        val tracker = PlaylistPlaybackTracker()
        assertTrue(update(tracker, playing = false, position = 45_000L, force = true).isEmpty())
        assertTrue(update(tracker, playing = false, force = true, elapsed = 2_000L).isEmpty())

        val started = update(tracker, position = 45_000L, elapsed = 3_000L).single()
        assertEquals(45_000L, started.positionMs)
        assertEquals(7L, started.playlistId)
    }

    @Test
    fun pauseSavesActualPositionAndPeriodicWritesAreThrottled() {
        val tracker = PlaylistPlaybackTracker()
        update(tracker)
        assertTrue(update(tracker, position = 5_000L, elapsed = 6_000L).isEmpty())
        assertEquals(10_000L, update(tracker, position = 10_000L, elapsed = 11_000L).single().positionMs)
        assertEquals(
            11_234L,
            update(tracker, playing = false, position = 11_234L, elapsed = 12_000L, force = true).single().positionMs,
        )
    }

    @Test
    fun automaticTransitionFlushesTheFinishedTrackBeforeTheNewTrack() {
        val tracker = PlaylistPlaybackTracker()
        update(tracker)
        tracker.capturePreviousPosition(7L, "first", 60_000L, isCompleted = true)

        val writes = update(tracker, mediaId = "second", elapsed = 61_000L, force = true)

        assertEquals(listOf("first", "second"), writes.map { it.mediaId })
        assertEquals(60_000L, writes.first().positionMs)
        assertTrue(writes.first().isCompleted)
        assertEquals(0L, writes.last().positionMs)
        assertFalse(writes.last().isCompleted)
    }

    @Test
    fun switchingWhilePausedOnlySavesTheTrackThatActuallyPlayed() {
        val tracker = PlaylistPlaybackTracker()
        update(tracker)
        tracker.capturePreviousPosition(7L, "first", 12_345L, isCompleted = false)

        val writes = update(tracker, mediaId = "unplayed", playing = false, force = true)
        assertEquals(listOf("first"), writes.map { it.mediaId })
        assertEquals(12_345L, writes.single().positionMs)
        assertTrue(update(tracker, mediaId = "unplayed", playing = false, force = true).isEmpty())
    }

    @Test
    fun failedReplayOfTheSameTrackKeepsItsPreviousPositionUntilPlaybackActuallyStarts() {
        val tracker = PlaylistPlaybackTracker()
        update(tracker, position = 12_345L)
        tracker.invalidateCurrentRequest()
        tracker.capturePreviousPosition(7L, "first", 15_678L, isCompleted = false)

        val previous = update(tracker, position = 0L, playing = false, force = true).single()
        assertEquals(15_678L, previous.positionMs)
        assertTrue(update(tracker, position = 0L, playing = false, force = true).isEmpty())
        assertEquals(0L, update(tracker, position = 0L, playing = true).single().positionMs)
    }

    @Test
    fun globalPlaybackDoesNotContaminatePlaylistHistoryAndSameTrackInAnotherPlaylistIsIndependent() {
        val tracker = PlaylistPlaybackTracker()
        update(tracker, position = 1_234L)

        val leavingPlaylist = update(tracker, playlistId = null, mediaId = "outside", force = true)
        assertEquals(7L, leavingPlaylist.single().playlistId)
        assertEquals(1_234L, leavingPlaylist.single().positionMs)
        assertTrue(update(tracker, playlistId = null, mediaId = "outside", force = true).isEmpty())

        val otherPlaylist = update(tracker, playlistId = 8L, position = 2_345L)
        assertEquals(8L, otherPlaylist.single().playlistId)
        assertEquals("first", otherPlaylist.single().mediaId)
        assertEquals(2_345L, otherPlaylist.single().positionMs)
    }

    @Test
    fun completionRequiresReachingTheEndAndStartingAgainClearsCompletion() {
        val tracker = PlaylistPlaybackTracker()
        assertFalse(update(tracker, position = 59_000L).single().isCompleted)
        val completed = update(tracker, playing = false, ended = true, position = 60_000L, force = true).single()
        assertTrue(completed.isCompleted)

        val restarted = update(tracker, position = 0L, force = true).single()
        assertFalse(restarted.isCompleted)
        assertEquals(0L, restarted.positionMs)
    }

    @Test
    fun missingDurationRetainsKnownDurationAndInvalidPositionsAreBounded() {
        val tracker = PlaylistPlaybackTracker()
        assertEquals(0L, update(tracker, position = -1L).single().positionMs)
        val sample = update(tracker, duration = 0L, position = 90_000L, force = true).single()
        assertEquals(60_000L, sample.durationMs)
        assertEquals(60_000L, sample.positionMs)
        assertTrue(sample.isCompleted)
    }

    private fun update(
        tracker: PlaylistPlaybackTracker,
        playlistId: Long? = 7L,
        mediaId: String = "first",
        position: Long = 0L,
        duration: Long = 60_000L,
        playing: Boolean = true,
        ended: Boolean = false,
        force: Boolean = false,
        elapsed: Long = 1_000L,
    ) = tracker.update(
        playlistId, mediaId, position, duration, playing, ended, force, elapsed,
        nowEpochMs = 1_700_000_000_000L + elapsed,
    )
}
