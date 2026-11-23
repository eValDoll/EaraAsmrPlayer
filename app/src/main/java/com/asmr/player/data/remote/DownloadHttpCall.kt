package com.asmr.player.data.remote

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import okhttp3.Call
import okhttp3.Response

/** Keep cancellation connected to the socket while reading or writing a request on an IO worker. */
internal suspend fun <T> Call.useDownloadResponse(read: suspend (Response) -> T): T = coroutineScope {
    val cancelSocket = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
        try {
            awaitCancellation()
        } finally {
            if (!this@coroutineScope.isActive) this@useDownloadResponse.cancel()
        }
    }
    try {
        execute().use { read(it) }
    } finally {
        cancelSocket.cancel()
    }
}
