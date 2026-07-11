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
                hasResolvedAsmrOneContent = false,
                isLoadingAsmrOne = false,
                hasAsmrOneTree = false,
                hasDirectoryBrowser = false
            )
        )
        assertTrue(
            shouldShowAsmrOneDirectoryLoading(
                isAwaitingAsmrOneLoad = false,
                hasResolvedAsmrOneContent = true,
                isLoadingAsmrOne = false,
                hasAsmrOneTree = true,
                hasDirectoryBrowser = false
            )
        )
        assertFalse(
            shouldShowAsmrOneDirectoryLoading(
                isAwaitingAsmrOneLoad = false,
                hasResolvedAsmrOneContent = true,
                isLoadingAsmrOne = true,
                hasAsmrOneTree = true,
                hasDirectoryBrowser = true
            )
        )
        assertFalse(
            shouldShowAsmrOneDirectoryLoading(
                isAwaitingAsmrOneLoad = false,
                hasResolvedAsmrOneContent = true,
                isLoadingAsmrOne = false,
                hasAsmrOneTree = false,
                hasDirectoryBrowser = false
            )
        )
    }

    @Test
    fun shouldShowAsmrOneDirectoryLoading_waitsUntilFallbackCompletes() {
        assertTrue(
            shouldShowAsmrOneDirectoryLoading(
                isAwaitingAsmrOneLoad = false,
                hasResolvedAsmrOneContent = false,
                isLoadingAsmrOne = false,
                hasAsmrOneTree = false,
                hasDirectoryBrowser = false
            )
        )
    }

    @Test
    fun shouldShowAsmrOneDirectoryLoading_ignoresEmptyDirectoryBrowserBeforeResolved() {
        assertTrue(
            shouldShowAsmrOneDirectoryLoading(
                isAwaitingAsmrOneLoad = false,
                hasResolvedAsmrOneContent = false,
                isLoadingAsmrOne = false,
                hasAsmrOneTree = false,
                hasDirectoryBrowser = true
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
    fun canUseAsmrOneOnlineTreeActions_ignoresDlsiteTargetState() {
        assertTrue(
            canUseAsmrOneOnlineTreeActions(
                selectedTab = 1,
                hasAsmrOneTree = true
            )
        )
        assertFalse(
            canUseAsmrOneOnlineTreeActions(
                selectedTab = 1,
                hasAsmrOneTree = false
            )
        )
        assertFalse(
            canUseAsmrOneOnlineTreeActions(
                selectedTab = 2,
                hasAsmrOneTree = true
            )
        )
    }

    @Test
    fun albumHeaderDownloadEnabled_enablesOneWhenTreeLoadedBeforeDlsiteTarget() {
        assertTrue(
            albumHeaderDownloadEnabled(
                selectedTab = 1,
                hasAsmrOneTree = true,
                hasDlsitePlayTree = false,
                hasResolvedInitialDlsiteTarget = false
            )
        )
    }

    @Test
    fun albumHeaderDownloadEnabled_keepsDlsitePlayBehindResolvedTarget() {
        assertFalse(
            albumHeaderDownloadEnabled(
                selectedTab = 2,
                hasAsmrOneTree = true,
                hasDlsitePlayTree = true,
                hasResolvedInitialDlsiteTarget = false
            )
        )
        assertTrue(
            albumHeaderDownloadEnabled(
                selectedTab = 2,
                hasAsmrOneTree = false,
                hasDlsitePlayTree = true,
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

    @Test
    fun shouldShowDlsitePlayDirectoryLoading_keepsVisibleTreeDuringBackgroundLoad() {
        assertFalse(
            shouldShowDlsitePlayDirectoryLoading(
                isAwaitingInitialTarget = false,
                hasResolvedDlsitePlayContent = true,
                isLoadingDlsitePlay = true,
                hasDlsitePlayTree = true,
                hasDirectoryBrowser = true
            )
        )
    }
}
