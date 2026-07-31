package com.asmr.player.ui.common

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class LazyListCompositionPrefetchTest {

    @Test
    fun prefetchIndices_includeForwardAndBackwardItemsWithinBounds() {
        assertArrayEquals(
            intArrayOf(10, 11, 12, 13, 4),
            resolveCompositionPrefetchIndices(
                firstVisibleIndex = 5,
                lastVisibleIndex = 9,
                totalItemCount = 20,
                forwardItemCount = 4,
                backwardItemCount = 1,
            ),
        )
        assertArrayEquals(
            intArrayOf(3, 4),
            resolveCompositionPrefetchIndices(
                firstVisibleIndex = 0,
                lastVisibleIndex = 2,
                totalItemCount = 5,
                forwardItemCount = 4,
                backwardItemCount = 1,
            ),
        )
    }
}
