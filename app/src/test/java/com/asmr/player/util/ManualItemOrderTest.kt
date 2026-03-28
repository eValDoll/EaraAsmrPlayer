package com.asmr.player.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ManualItemOrderTest {
    @Test
    fun moveKeyToStart_movesTargetToFront() {
        val items = listOf("a", "b", "c")

        val moved = ManualItemOrder.moveKeyToStart(items, "c") { it }

        assertEquals(listOf("c", "a", "b"), moved)
    }

    @Test
    fun moveKeyToEnd_movesTargetToBack() {
        val items = listOf("a", "b", "c")

        val moved = ManualItemOrder.moveKeyToEnd(items, "a") { it }

        assertEquals(listOf("b", "c", "a"), moved)
    }

    @Test
    fun reorderByKeys_returnsRequestedOrderWhenKeysMatch() {
        val items = listOf("a", "b", "c")

        val reordered = ManualItemOrder.reorderByKeys(items, listOf("b", "c", "a")) { it }

        assertEquals(listOf("b", "c", "a"), reordered)
    }
}
