package com.asmr.player.cache

import kotlinx.coroutines.sync.Semaphore

internal class ImageLoadGate(
    remotePermits: Int,
    localPermits: Int,
) {
    private val remoteSemaphore = Semaphore(remotePermits)
    private val localSemaphore = Semaphore(localPermits)

    suspend fun <T> withPermit(model: Any, block: suspend () -> T): T {
        val semaphore = if (isRemoteImageModel(model)) {
            remoteSemaphore
        } else {
            localSemaphore
        }
        semaphore.acquire()
        return try {
            block()
        } finally {
            semaphore.release()
        }
    }
}

internal fun isRemoteImageModel(model: Any): Boolean {
    val data = (model as? CacheImageModel)?.data ?: model
    val value = data as? String ?: return false
    return value.startsWith("http://", ignoreCase = true) ||
        value.startsWith("https://", ignoreCase = true)
}
