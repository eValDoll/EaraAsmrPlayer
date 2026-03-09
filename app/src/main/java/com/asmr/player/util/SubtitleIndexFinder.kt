package com.asmr.player.util

class SubtitleIndexFinder(private val entries: List<SubtitleEntry>) {
    private val startMs: LongArray? = if (isNonDecreasingByStartMs(entries)) {
        LongArray(entries.size) { idx -> entries[idx].startMs }
    } else {
        null
    }

    private var lastIndex: Int = -1

    fun findActiveIndex(positionMs: Long): Int {
        val pos = positionMs.coerceAtLeast(0L)
        val starts = startMs ?: return linearIndexOfLast(entries, pos)
        if (starts.isEmpty()) return -1

        val li = lastIndex
        if (li in starts.indices) {
            val lastStart = starts[li]
            val nextStart = starts.getOrNull(li + 1)
            if (pos >= lastStart && (nextStart == null || pos < nextStart)) {
                return li
            }
            if (nextStart != null && pos >= nextStart) {
                var i = li + 1
                while (i + 1 < starts.size && pos >= starts[i + 1]) i++
                lastIndex = i
                return i
            }
        }

        val idx = upperBound(starts, pos) - 1
        lastIndex = idx
        return idx
    }
}

private fun isNonDecreasingByStartMs(entries: List<SubtitleEntry>): Boolean {
    var last = Long.MIN_VALUE
    for (i in entries.indices) {
        val v = entries[i].startMs
        if (v < last) return false
        last = v
    }
    return true
}

private fun linearIndexOfLast(entries: List<SubtitleEntry>, positionMs: Long): Int {
    var idx = -1
    for (i in entries.indices) {
        if (entries[i].startMs <= positionMs) idx = i
    }
    return idx
}

private fun upperBound(values: LongArray, target: Long): Int {
    var lo = 0
    var hi = values.size
    while (lo < hi) {
        val mid = (lo + hi) ushr 1
        if (values[mid] <= target) lo = mid + 1 else hi = mid
    }
    return lo
}
