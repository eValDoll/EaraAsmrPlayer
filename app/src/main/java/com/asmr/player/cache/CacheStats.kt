package com.asmr.player.cache

import java.util.concurrent.atomic.AtomicLong

class CacheStats {
    private val memoryHits = AtomicLong(0L)
    private val memoryMisses = AtomicLong(0L)
    private val diskHits = AtomicLong(0L)
    private val diskMisses = AtomicLong(0L)
    private val networkFetches = AtomicLong(0L)
    private val decodeCount = AtomicLong(0L)

    fun onMemoryHit() = memoryHits.incrementAndGet()
    fun onMemoryMiss() = memoryMisses.incrementAndGet()
    fun onDiskHit() = diskHits.incrementAndGet()
    fun onDiskMiss() = diskMisses.incrementAndGet()
    fun onNetworkFetch() = networkFetches.incrementAndGet()
    fun onDecode() = decodeCount.incrementAndGet()

    fun snapshot(): Snapshot {
        val mh = memoryHits.get()
        val mm = memoryMisses.get()
        val dh = diskHits.get()
        val dm = diskMisses.get()
        return Snapshot(
            memoryHits = mh,
            memoryMisses = mm,
            diskHits = dh,
            diskMisses = dm,
            networkFetches = networkFetches.get(),
            decodeCount = decodeCount.get(),
            memoryHitRate = hitRate(mh, mm),
            diskHitRate = hitRate(dh, dm)
        )
    }

    data class Snapshot(
        val memoryHits: Long,
        val memoryMisses: Long,
        val diskHits: Long,
        val diskMisses: Long,
        val networkFetches: Long,
        val decodeCount: Long,
        val memoryHitRate: Double,
        val diskHitRate: Double
    )

    private fun hitRate(hit: Long, miss: Long): Double {
        val total = hit + miss
        return if (total <= 0L) 0.0 else hit.toDouble() / total.toDouble()
    }
}

