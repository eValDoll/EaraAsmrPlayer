package com.asmr.player.cache

data class CacheConfig(
    val cacheVersion: String,
    val memoryMaxSizePercent: Double = 0.20,
    val diskMaxSizeBytes: Long = AppCacheLimits.imageMaxSizeBytes(AppCacheLimits.DefaultSizeMb),
    val diskTtlMs: Long = 14L * 24 * 60 * 60 * 1000,
    val decodeParallelism: Int = 3,
    val loadParallelism: Int = 3,
    val preloadParallelism: Int = 3,
    val logStats: Boolean = false
)

