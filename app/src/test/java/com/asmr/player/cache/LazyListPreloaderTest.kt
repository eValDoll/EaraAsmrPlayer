package com.asmr.player.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LazyListPreloaderTest {
    @Test
    fun preloadLeadCount_keepsScrollingWindowBoundedToOneViewport() {
        assertEquals(
            8,
            resolveLazyListPreloadLeadCount(
                visibleItemCount = 6,
                preloadNext = 24,
                preloadNextWhileScrolling = 8,
                isScrolling = true,
            )
        )
        assertEquals(
            12,
            resolveLazyListPreloadLeadCount(
                visibleItemCount = 12,
                preloadNext = 24,
                preloadNextWhileScrolling = 8,
                isScrolling = true,
            )
        )
    }

    @Test
    fun preloadLeadCount_keepsIdleWindowWider() {
        assertEquals(
            24,
            resolveLazyListPreloadLeadCount(
                visibleItemCount = 6,
                preloadNext = 24,
                preloadNextWhileScrolling = 8,
                isScrolling = false,
            )
        )
    }

    @Test
    fun preloadRange_prefetchesAfterLastVisibleItemWhenScrollingForward() {
        assertEquals(
            5..12,
            resolveLazyListPreloadRange(
                firstVisibleIndex = 0,
                lastVisibleIndex = 4,
                visibleItemCount = 5,
                itemCount = 30,
                preloadNext = 24,
                preloadNextWhileScrolling = 8,
                isScrolling = true,
                direction = LazyListPreloadDirection.Forward,
            )
        )
    }

    @Test
    fun preloadRange_prefetchesBeforeFirstVisibleItemWhenScrollingBackward() {
        assertEquals(
            2..9,
            resolveLazyListPreloadRange(
                firstVisibleIndex = 10,
                lastVisibleIndex = 15,
                visibleItemCount = 6,
                itemCount = 30,
                preloadNext = 24,
                preloadNextWhileScrolling = 8,
                isScrolling = true,
                direction = LazyListPreloadDirection.Backward,
            )
        )
    }

    @Test
    fun preloadRange_clipsAtDatasetEdges() {
        assertEquals(
            8..9,
            resolveLazyListPreloadRange(
                firstVisibleIndex = 3,
                lastVisibleIndex = 7,
                visibleItemCount = 5,
                itemCount = 10,
                preloadNext = 24,
                preloadNextWhileScrolling = 8,
                isScrolling = false,
                direction = LazyListPreloadDirection.Forward,
            )
        )
        assertNull(
            resolveLazyListPreloadRange(
                firstVisibleIndex = 0,
                lastVisibleIndex = 4,
                visibleItemCount = 5,
                itemCount = 10,
                preloadNext = 24,
                preloadNextWhileScrolling = 8,
                isScrolling = true,
                direction = LazyListPreloadDirection.Backward,
            )
        )
    }

    @Test
    fun preloadDirection_followsVisibleWindowMovement() {
        assertEquals(
            LazyListPreloadDirection.Forward,
            resolveLazyListPreloadDirection(
                previousFirstVisibleIndex = 4,
                previousLastVisibleIndex = 9,
                firstVisibleIndex = 5,
                lastVisibleIndex = 10,
            )
        )
        assertEquals(
            LazyListPreloadDirection.Backward,
            resolveLazyListPreloadDirection(
                previousFirstVisibleIndex = 5,
                previousLastVisibleIndex = 10,
                firstVisibleIndex = 4,
                lastVisibleIndex = 9,
            )
        )
    }

    @Test
    fun preloadDirection_usesTrailingEdgeAndRetainsLastDirectionWhenStable() {
        assertEquals(
            LazyListPreloadDirection.Backward,
            resolveLazyListPreloadDirection(
                previousFirstVisibleIndex = 5,
                previousLastVisibleIndex = 10,
                firstVisibleIndex = 5,
                lastVisibleIndex = 9,
                previousDirection = LazyListPreloadDirection.Forward,
            )
        )
        assertEquals(
            LazyListPreloadDirection.Backward,
            resolveLazyListPreloadDirection(
                previousFirstVisibleIndex = 5,
                previousLastVisibleIndex = 10,
                firstVisibleIndex = 5,
                lastVisibleIndex = 10,
                previousDirection = LazyListPreloadDirection.Backward,
            )
        )
    }
}
