package com.asmr.player.util

object ManualItemOrder {
    fun <T> move(items: List<T>, fromIndex: Int, toIndex: Int): List<T> {
        if (items.isEmpty()) return items
        if (fromIndex !in items.indices || toIndex !in items.indices || fromIndex == toIndex) {
            return items
        }
        return items.toMutableList().apply {
            add(toIndex, removeAt(fromIndex))
        }
    }

    fun <T, K> moveKeyToStart(
        items: List<T>,
        targetKey: K,
        keySelector: (T) -> K
    ): List<T> {
        val fromIndex = items.indexOfFirst { keySelector(it) == targetKey }
        if (fromIndex <= 0) return items
        return move(items, fromIndex = fromIndex, toIndex = 0)
    }

    fun <T, K> moveKeyToEnd(
        items: List<T>,
        targetKey: K,
        keySelector: (T) -> K
    ): List<T> {
        val fromIndex = items.indexOfFirst { keySelector(it) == targetKey }
        if (fromIndex < 0 || fromIndex == items.lastIndex) return items
        return move(items, fromIndex = fromIndex, toIndex = items.lastIndex)
    }

    fun <T, K> reorderByKeys(
        current: List<T>,
        orderedKeys: List<K>,
        keySelector: (T) -> K
    ): List<T> {
        if (current.isEmpty()) return current
        val distinctKeys = orderedKeys.distinct()
        if (distinctKeys.size != current.size) return current
        val currentByKey = current.associateBy(keySelector)
        val reordered = distinctKeys.mapNotNull { currentByKey[it] }
        return if (reordered.size == current.size) reordered else current
    }

    fun <T> reindex(
        items: List<T>,
        orderUpdater: (T, Int) -> T
    ): List<T> = items.mapIndexed { index, item ->
        orderUpdater(item, index)
    }
}
