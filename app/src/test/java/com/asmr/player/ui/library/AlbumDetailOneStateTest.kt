package com.asmr.player.ui.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumDetailOneStateTest {
    @Test
    fun asmrOneFailureMessage_isSilentForLocalLibraryDetail() {
        assertNull(albumDetailAsmrOneFailureMessage(isLocalLibraryDetail = true))
        assertEquals(
            com.asmr.player.util.ASMR_ONE_SITE_FAILURE_MESSAGE,
            albumDetailAsmrOneFailureMessage(isLocalLibraryDetail = false)
        )
    }

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
                hasResolvedInitialDlsiteTarget = false,
                isInitialRouteReady = true
            )
        )
    }

    @Test
    fun albumDetailOnlineLoadPlan_localUsesDlsiteOnlyAsAuthenticatedFallback() {
        assertEquals(
            AlbumDetailOnlineLoadPlan(),
            albumDetailOnlineLoadPlan(
                selectedTab = 0,
                hasResolvedInitialDlsiteTarget = false,
                isInitialRouteReady = true,
                hasValidLocalRj = false
            )
        )
        assertEquals(
            AlbumDetailOnlineLoadPlan(loadAsmrOne = true),
            albumDetailOnlineLoadPlan(
                selectedTab = 0,
                hasResolvedInitialDlsiteTarget = false,
                isInitialRouteReady = true,
                hasValidLocalRj = true,
                hasResolvedAsmrOneContent = false,
                hasAsmrOneTree = false,
                hasDlsitePlayCredentials = true
            )
        )
        assertEquals(
            AlbumDetailOnlineLoadPlan(loadAsmrOne = true),
            albumDetailOnlineLoadPlan(
                selectedTab = 0,
                hasResolvedInitialDlsiteTarget = false,
                isInitialRouteReady = true,
                hasValidLocalRj = true,
                hasResolvedAsmrOneContent = true,
                hasAsmrOneTree = false,
                hasDlsitePlayCredentials = false
            )
        )
        assertEquals(
            AlbumDetailOnlineLoadPlan(loadAsmrOne = true, loadDlsitePlay = true),
            albumDetailOnlineLoadPlan(
                selectedTab = 0,
                hasResolvedInitialDlsiteTarget = false,
                isInitialRouteReady = true,
                hasValidLocalRj = true,
                hasResolvedAsmrOneContent = true,
                hasAsmrOneTree = false,
                hasDlsitePlayCredentials = true
            )
        )
        assertEquals(
            AlbumDetailOnlineLoadPlan(loadAsmrOne = true),
            albumDetailOnlineLoadPlan(
                selectedTab = 0,
                hasResolvedInitialDlsiteTarget = false,
                isInitialRouteReady = true,
                hasValidLocalRj = true,
                hasResolvedAsmrOneContent = true,
                hasAsmrOneTree = true,
                hasDlsitePlayCredentials = true
            )
        )
    }

    @Test
    fun albumDetailOnlineLoadPlan_keepsDlsitePlayBehindResolvedTarget() {
        assertEquals(
            AlbumDetailOnlineLoadPlan(loadDlsite = true, loadDlsitePlay = false),
            albumDetailOnlineLoadPlan(
                selectedTab = 2,
                hasResolvedInitialDlsiteTarget = false,
                isInitialRouteReady = true
            )
        )
        assertEquals(
            AlbumDetailOnlineLoadPlan(loadDlsite = true, loadDlsitePlay = true),
            albumDetailOnlineLoadPlan(
                selectedTab = 2,
                hasResolvedInitialDlsiteTarget = true,
                isInitialRouteReady = true
            )
        )
    }

    @Test
    fun albumDetailOnlineLoadPlan_waitsForInitialRouteHydration() {
        assertEquals(
            AlbumDetailOnlineLoadPlan(),
            albumDetailOnlineLoadPlan(
                selectedTab = 1,
                hasResolvedInitialDlsiteTarget = false,
                isInitialRouteReady = false
            )
        )
        assertEquals(
            AlbumDetailOnlineLoadPlan(),
            albumDetailOnlineLoadPlan(
                selectedTab = 2,
                hasResolvedInitialDlsiteTarget = true,
                isInitialRouteReady = false
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
    fun albumHeaderDownloadEnabled_localRequiresRjAndAccessibleTree() {
        assertFalse(
            albumHeaderDownloadEnabled(
                selectedTab = 0,
                hasAsmrOneTree = false,
                hasDlsitePlayTree = false,
                hasResolvedInitialDlsiteTarget = true,
                hasValidLocalRj = true
            )
        )
        assertFalse(
            albumHeaderDownloadEnabled(
                selectedTab = 0,
                hasAsmrOneTree = true,
                hasDlsitePlayTree = false,
                hasResolvedInitialDlsiteTarget = false,
                hasValidLocalRj = false
            )
        )
        assertTrue(
            albumHeaderDownloadEnabled(
                selectedTab = 0,
                hasAsmrOneTree = false,
                hasDlsitePlayTree = true,
                hasResolvedInitialDlsiteTarget = false,
                hasValidLocalRj = true
            )
        )
        assertFalse(
            albumHeaderDownloadEnabled(
                selectedTab = 0,
                hasAsmrOneTree = false,
                hasDlsitePlayTree = true,
                hasResolvedInitialDlsiteTarget = false,
                hasValidLocalRj = true,
                hasDlsitePlayCredentials = false
            )
        )
        assertTrue(
            albumHeaderDownloadEnabled(
                selectedTab = 0,
                hasAsmrOneTree = true,
                hasDlsitePlayTree = false,
                hasResolvedInitialDlsiteTarget = false,
                hasValidLocalRj = true,
                hasDlsitePlayCredentials = false
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
