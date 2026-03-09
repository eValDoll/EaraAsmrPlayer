package com.asmr.player.data.remote

import com.asmr.player.data.repository.StatisticsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrafficStatsInterceptor @Inject constructor(
    private val statisticsRepository: StatisticsRepository
) : Interceptor {
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val requestSize = request.body?.contentLength()?.coerceAtLeast(0L) ?: 0L
        
        val response = chain.proceed(request)
        val responseSize = response.body?.contentLength()?.coerceAtLeast(0L) ?: 0L
        
        val totalBytes = requestSize + responseSize
        if (totalBytes > 0) {
            scope.launch {
                statisticsRepository.addNetworkTraffic(totalBytes)
            }
        }
        
        return response
    }
}
