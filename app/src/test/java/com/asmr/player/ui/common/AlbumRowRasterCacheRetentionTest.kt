package com.asmr.player.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlbumRowRasterCacheRetentionTest {
    private val search = 1
    private val hotListening = 2

    @Test
    fun unvisitedPagesDoNotBecomeHiddenCacheTargets() {
        val retention = AlbumRowRasterCacheRetention().apply { onStart() }

        assertNull(retention.targetPage(null, transformIsIdentity = true))
        assertEquals(search, retention.targetPage(search, transformIsIdentity = true))
        // Evaluating a draw/layer policy cannot itself retain an unvisited page.
        assertNull(retention.targetPage(null, transformIsIdentity = true))
    }

    @Test
    fun searchSettingsSearchKeepsTheSameCachePermission() {
        val retention = AlbumRowRasterCacheRetention().apply {
            onStart()
            admit(search)
        }

        assertEquals(search, retention.targetPage(search, transformIsIdentity = true))
        retention.admit(null)
        assertEquals(search, retention.targetPage(null, transformIsIdentity = true))
        assertEquals(search, retention.targetPage(search, transformIsIdentity = true))
    }

    @Test
    fun movingPagerRetainsOldPageAndSettledNewListReplacesIt() {
        val retention = AlbumRowRasterCacheRetention().apply {
            onStart()
            admit(search)
        }

        // A moving Pager has no newly eligible page; its integer placement keeps the old layer.
        assertEquals(search, retention.targetPage(null, transformIsIdentity = true))
        // New admission takes effect immediately, without one frame allowing both pages.
        assertEquals(hotListening, retention.targetPage(hotListening, transformIsIdentity = true))
        retention.admit(hotListening)
        assertEquals(hotListening, retention.targetPage(null, transformIsIdentity = true))
    }

    @Test
    fun nonidentityAncestorDisablesRasterWithoutChangingTheRememberedPage() {
        val retention = AlbumRowRasterCacheRetention().apply {
            onStart()
            admit(search)
        }

        assertNull(retention.targetPage(search, transformIsIdentity = false))
        assertNull(retention.targetPage(null, transformIsIdentity = false))
        assertEquals(search, retention.targetPage(null, transformIsIdentity = true))
    }

    @Test
    fun stoppedWindowCannotRebuildHiddenPageOnStart() {
        val retention = AlbumRowRasterCacheRetention().apply {
            onStart()
            admit(search)
            onStop()
        }

        assertNull(retention.targetPage(search, transformIsIdentity = true))
        retention.admit(search)
        retention.onStart()
        assertNull(retention.targetPage(null, transformIsIdentity = true))
        assertEquals(hotListening, retention.targetPage(hotListening, transformIsIdentity = true))
    }

    @Test
    fun memoryTrimCannotImmediatelyReadmitCurrentOrHiddenPages() {
        val retention = AlbumRowRasterCacheRetention().apply {
            onStart()
            admit(search)
            onTrimMemory()
        }

        retention.admit(search)
        assertNull(retention.targetPage(search, transformIsIdentity = true))
        assertNull(retention.targetPage(null, transformIsIdentity = true))
        retention.onStop()
        retention.onStart()
        assertNull(retention.targetPage(null, transformIsIdentity = true))
        assertEquals(search, retention.targetPage(search, transformIsIdentity = true))
    }

    @Test
    fun recreatedOwnerHasNoRestoredCacheAdmission() {
        AlbumRowRasterCacheRetention().apply {
            onStart()
            admit(search)
            onStop()
        }
        val recreated = AlbumRowRasterCacheRetention().apply { onStart() }

        assertNull(recreated.targetPage(null, transformIsIdentity = true))
    }
}
