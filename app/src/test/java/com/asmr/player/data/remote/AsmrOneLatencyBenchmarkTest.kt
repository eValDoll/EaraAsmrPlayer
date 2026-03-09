package com.asmr.player.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assume
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

class AsmrOneLatencyBenchmarkTest {
    @Test
    fun benchmark_searchRj01491538_30Rounds() {
        Assume.assumeTrue(System.getProperty("asmr.latency") == "true")

        val url = "https://api.asmr.one/api/search/RJ01491538?page=1&order=release&sort=desc"
        val client = OkHttpClient.Builder()
            .callTimeout(30, TimeUnit.SECONDS)
            .build()

        val times = ArrayList<Long>(30)
        var okCount = 0
        repeat(30) {
            val start = System.nanoTime()
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Eara-LatencyTest")
                .build()
            val ok = runCatching {
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use false
                    val body = resp.body?.string().orEmpty()
                    body.contains("\"source_id\":\"RJ01491538\"", ignoreCase = true)
                }
            }.getOrDefault(false)
            if (ok) okCount += 1
            val dt = (System.nanoTime() - start) / 1_000_000L
            times.add(dt.coerceAtLeast(0L))
        }

        val sorted = times.sorted()
        val p50 = percentileNearestRank(sorted, 50.0)
        val p95 = percentileNearestRank(sorted, 95.0)
        val max = sorted.lastOrNull() ?: 0L
        println("RJ01491538 search benchmark n=30 ok=$okCount p50=${p50}ms p95=${p95}ms max=${max}ms")
    }

    private fun percentileNearestRank(sorted: List<Long>, percentile: Double): Long {
        if (sorted.isEmpty()) return 0L
        val n = sorted.size
        val rank = ceil(percentile / 100.0 * n).toInt().coerceIn(1, n)
        return sorted[rank - 1]
    }
}

