package com.asmr.player.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LazyListPreloaderTest {
    @Test
    fun preloadLeadCount_expandsWithVisibleItemsWhileScrolling() {
        assertEquals(
            16,
            resolveLazyListPreloadLeadCount(
                visibleItemCount = 6,
                preloadNext = 24,
                preloadNextWhileScrolling = 16,
                isScrolling = true
            )
        )
    }

    @Test
    fun preloadLeadCount_keepsIdleWindowWiderThanScrollingWindow() {
        assertEquals(
            24,
            resolveLazyListPreloadLeadCount(
                visibleItemCount = 6,
                preloadNext = 24,
                preloadNextWhileScrolling = 16,
                isScrolling = false
            )
        )
    }

    @Test
    fun preloadLeadCount_usesConfiguredMinimumForTinyViewports() {
        assertEquals(
            16,
            resolveLazyListPreloadLeadCount(
                visibleItemCount = 1,
                preloadNext = 24,
                preloadNextWhileScrolling = 16,
                isScrolling = true
            )
        )
        assertEquals(
            24,
            resolveLazyListPreloadLeadCount(
                visibleItemCount = 1,
                preloadNext = 24,
                preloadNextWhileScrolling = 16,
                isScrolling = false
            )
        )
    }

    @Test
    fun preloadRange_startsAfterLastVisibleItemAndClipsAtEnd() {
        assertEquals(
            5..20,
            resolveLazyListPreloadRange(
                lastVisibleIndex = 4,
                visibleItemCount = 6,
                itemCount = 30,
                preloadNext = 24,
                preloadNextWhileScrolling = 16,
                isScrolling = true
            )
        )
        assertEquals(
            8..9,
            resolveLazyListPreloadRange(
                lastVisibleIndex = 7,
                visibleItemCount = 6,
                itemCount = 10,
                preloadNext = 24,
                preloadNextWhileScrolling = 16,
                isScrolling = false
            )
        )
    }

    @Test
    fun preloadRange_returnsNullWhenThereAreNoAheadItems() {
        assertNull(
            resolveLazyListPreloadRange(
                lastVisibleIndex = 9,
                visibleItemCount = 6,
                itemCount = 10,
                preloadNext = 24,
                preloadNextWhileScrolling = 16,
                isScrolling = true
            )
        )
    }
}
