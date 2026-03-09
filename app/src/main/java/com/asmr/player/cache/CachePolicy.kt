package com.asmr.player.cache

data class CachePolicy(
    val readMemory: Boolean,
    val writeMemory: Boolean,
    val readDisk: Boolean,
    val writeDisk: Boolean
) {
    companion object {
        val DEFAULT = CachePolicy(readMemory = true, writeMemory = true, readDisk = true, writeDisk = true)
        val MEMORY_ONLY = CachePolicy(readMemory = true, writeMemory = true, readDisk = false, writeDisk = false)
        val DISK_ONLY = CachePolicy(readMemory = false, writeMemory = false, readDisk = true, writeDisk = true)
        val NO_CACHE = CachePolicy(readMemory = false, writeMemory = false, readDisk = false, writeDisk = false)
    }
}

