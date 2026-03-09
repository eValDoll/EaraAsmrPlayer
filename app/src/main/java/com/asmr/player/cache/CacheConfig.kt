package com.asmr.player.cache

data class CacheConfig(
    val cacheVersion: String,
    val memoryMaxSizePercent: Double = 0.20,
    val diskMaxSizeBytes: Long = 200L * 1024 * 1024,
    val diskTtlMs: Long = 14L * 24 * 60 * 60 * 1000,
    val decodeParallelism: Int = 3,
    val logStats: Boolean = false
)

