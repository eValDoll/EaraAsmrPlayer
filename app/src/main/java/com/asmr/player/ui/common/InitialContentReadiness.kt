package com.asmr.player.ui.common

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

/** 只等待源流的真实首发；不订阅数据，也不把 StateFlow 的默认回放值当成加载完成。 */
internal class InitialContentReadiness {
    private val sources = mutableListOf<Source>()

    fun registerSource(): Source = Source().also(sources::add)

    fun <T> track(upstream: Flow<T>): Flow<T> = registerSource().track(upstream)

    suspend fun awaitReady() {
        sources.forEach { it.awaitReady() }
    }

    class Source internal constructor() {
        private val ready = CompletableDeferred<Unit>()

        fun <T> track(upstream: Flow<T>): Flow<T> = upstream.onEach {
            ready.complete(Unit)
        }

        internal suspend fun awaitReady() {
            ready.await()
        }
    }
}
