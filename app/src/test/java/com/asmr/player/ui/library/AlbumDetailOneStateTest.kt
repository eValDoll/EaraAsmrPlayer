package com.asmr.player.ui.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumDetailOneStateTest {
    @Test
    fun shouldShowAsmrOneDirectoryLoading_dependsOnOneStateOnly() {
        assertTrue(
            shouldShowAsmrOneDirectoryLoading(
                isAwaitingAsmrOneLoad = true,
                isLoadingAsmrOne = false,
                hasAsmrOneTree = false,
                hasDirectoryBrowser = false
            )
        )
        assertTrue(
            shouldShowAsmrOneDirectoryLoading(
                isAwaitingAsmrOneLoad = false,
                isLoadingAsmrOne = false,
                hasAsmrOneTree = true,
                hasDirectoryBrowser = false
            )
        )
        assertFalse(
            shouldShowAsmrOneDirectoryLoading(
                isAwaitingAsmrOneLoad = false,
                isLoadingAsmrOne = false,
                hasAsmrOneTree = false,
                hasDirectoryBrowser = false
            )
        )
    }

    @Test
    fun albumDetailOnlineLoadPlan_loadsOneWithoutWaitingForDlsiteTarget() {
        assertEquals(
            AlbumDetailOnlineLoadPlan(loadDlsite = true, loadAsmrOne = true),
            albumDetailOnlineLoadPlan(
                selectedTab = 1,
                hasResolvedInitialDlsiteTarget = false
            )
        )
    }

    @Test
    fun albumDetailOnlineLoadPlan_keepsDlsitePlayBehindResolvedTarget() {
        assertEquals(
            AlbumDetailOnlineLoadPlan(loadDlsite = true, loadDlsitePlay = false),
            albumDetailOnlineLoadPlan(
                selectedTab = 2,
                hasResolvedInitialDlsiteTarget = false
            )
        )
        assertEquals(
            AlbumDetailOnlineLoadPlan(loadDlsite = true, loadDlsitePlay = true),
            albumDetailOnlineLoadPlan(
                selectedTab = 2,
                hasResolvedInitialDlsiteTarget = true
            )
        )
    }

    @Test
    fun shouldShowDlsitePlayDirectoryLoading_waitsForInitialTargetBeforeEmptyState() {
        assertTrue(
            shouldShowDlsitePlayDirectoryLoading(
                isAwaitingInitialTarget = true,
                hasResolvedDlsitePlayContent = false,
                isLoadingDlsitePlay = false,
                hasDlsitePlayTree = false,
                hasDirectoryBrowser = false
            )
        )
    }

    @Test
    fun shouldShowDlsitePlayDirectoryLoading_stopsAfterEmptyLoadCompletes() {
        assertFalse(
            shouldShowDlsitePlayDirectoryLoading(
                isAwaitingInitialTarget = false,
                hasResolvedDlsitePlayContent = true,
                isLoadingDlsitePlay = false,
                hasDlsitePlayTree = false,
                hasDirectoryBrowser = false
            )
        )
    }

    @Test
    fun shouldShowDlsitePlayDirectoryLoading_waitsForDirectoryIndex() {
        assertTrue(
            shouldShowDlsitePlayDirectoryLoading(
                isAwaitingInitialTarget = false,
                hasResolvedDlsitePlayContent = true,
                isLoadingDlsitePlay = false,
                hasDlsitePlayTree = true,
                hasDirectoryBrowser = false
            )
        )
    }
}
