package com.asmr.player.cache

data class CachePolicy(
    val readMemory: Boolean,
    val writeMemory: Boolean,
    val readDisk: Boolean,
    val writeDisk: Boolean
) {
    companion object {
        val DEFAULT = CachePolicy(readMemory = true, writeMemory = true, readDisk = true, writeDisk = true)
        val CACHE_WARMUP = CachePolicy(readMemory = true, writeMemory = true, readDisk = true, writeDisk = false)
    }
}

