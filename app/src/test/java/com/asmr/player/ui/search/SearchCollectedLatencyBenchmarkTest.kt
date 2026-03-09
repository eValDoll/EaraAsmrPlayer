package com.asmr.player.ui.search

import com.asmr.player.data.remote.NetworkHeaders
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.junit.Assume
import org.junit.Test
import java.net.URLEncoder
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

class SearchCollectedLatencyBenchmarkTest {
    private data class DlsiteItem(val index: Int, val rj: String, val title: String)
    private enum class CollectedResult { Collected, NotCollected, Unknown }
    private data class CheckDetail(
        val attempt: Int,
        val result: CollectedResult,
        val dtMs: Long
    )
    private data class CheckReport(
        val item: DlsiteItem,
        val final: CollectedResult,
        val totalDtMs: Long,
        val details: List<CheckDetail>
    )

    @Test
    fun benchmark_keywordDaShan_page2_30Items_eachWorkCollectedLatency() = runBlocking {
        val enabled =
            System.getProperty("asmrLatency") == "true" ||
                System.getProperty("asmr.latency") == "true" ||
                System.getenv("ASMR_LATENCY") == "true"
        Assume.assumeTrue(enabled)

        val keyword = "大山"
        val page = 2
        val order = "trend"

        val dlsiteStart = System.nanoTime()
        val items = fetchDlsiteSearchItems(keyword = keyword, page = page, order = order)
        val dlsiteDtMs = (System.nanoTime() - dlsiteStart) / 1_000_000L

        val client = OkHttpClient.Builder()
            .callTimeout(20, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        val sem = Semaphore(4)
        val reports = coroutineScope {
            items.map { item ->
                async(Dispatchers.IO) {
                    sem.withPermit {
                        checkAsmrOneCollectedWithRetry(client, item)
                    }
                }
            }.awaitAll()
        }.sortedBy { it.item.index }

        println("DLSite search keyword=\"$keyword\" page=$page order=$order dt=${dlsiteDtMs}ms items=${items.size}")
        println("Index\tRJ\tResult\tTotalMs\tAttempts\tTitle")
        reports.forEach { r ->
            val attempts = r.details.size
            println("${r.item.index}\t${r.item.rj}\t${r.final}\t${r.totalDtMs}\t$attempts\t${r.item.title}")
            r.details.forEach { d ->
                println("\t#${d.attempt}\t${d.result}\t${d.dtMs}ms")
            }
        }

        val dts = reports.map { it.totalDtMs }.sorted()
        val p50 = percentileNearestRank(dts, 50.0)
        val p95 = percentileNearestRank(dts, 95.0)
        val max = dts.lastOrNull() ?: 0L
        val okCount = reports.count { it.final == CollectedResult.Collected }
        val noCount = reports.count { it.final == CollectedResult.NotCollected }
        val unkCount = reports.count { it.final == CollectedResult.Unknown }
        println("Summary n=${reports.size} collected=$okCount notCollected=$noCount unknown=$unkCount p50=${p50}ms p95=${p95}ms max=${max}ms")
    }

    @Test
    fun benchmark_keywordDaShan_page2_twoPhase_showCollectedLatency() = runBlocking {
        val enabled =
            System.getProperty("asmrLatency") == "true" ||
                System.getProperty("asmr.latency") == "true" ||
                System.getenv("ASMR_LATENCY") == "true"
        Assume.assumeTrue(enabled)

        val keyword = "大山"
        val page = 2
        val order = "trend"

        val dlsiteStart = System.nanoTime()
        val items = fetchDlsiteSearchItems(keyword = keyword, page = page, order = order)
        val dlsiteDtMs = (System.nanoTime() - dlsiteStart) / 1_000_000L

        val client = OkHttpClient.Builder()
            .callTimeout(20, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        data class StageA(
            val item: DlsiteItem,
            val result: CollectedResult,
            val dtMs: Long,
            val shownAtMs: Long?
        )

        val startAll = System.nanoTime()
        val pending = ConcurrentLinkedQueue<DlsiteItem>()
        val semFast = Semaphore(4)
        val stageA = coroutineScope {
            items.map { item ->
                async(Dispatchers.IO) {
                    semFast.withPermit {
                        val t0 = System.nanoTime()
                        val result = runCatching { queryAsmrOneWorkCollected(client, item.rj) }.getOrNull() ?: CollectedResult.Unknown
                        val dt = (System.nanoTime() - t0) / 1_000_000L
                        val shownAt = if (result == CollectedResult.Collected) (System.nanoTime() - startAll) / 1_000_000L else null
                        if (result != CollectedResult.Collected) pending.add(item)
                        StageA(item = item, result = result, dtMs = dt, shownAtMs = shownAt)
                    }
                }
            }.awaitAll()
        }.sortedBy { it.item.index }

        val retryTargets = pending.toList()
        val semRetry = Semaphore(2)
        val stageBAppeared = coroutineScope {
            retryTargets.map { item ->
                async(Dispatchers.IO) {
                    semRetry.withPermit {
                        val report = checkAsmrOneCollectedWithRetry(client, item)
                        if (report.final == CollectedResult.Collected) {
                            val shownAt = (System.nanoTime() - startAll) / 1_000_000L
                            item.index to shownAt
                        } else null
                    }
                }
            }.awaitAll().filterNotNull().toMap()
        }

        println("Two-phase simulation keyword=\"$keyword\" page=$page order=$order dlsite=${dlsiteDtMs}ms items=${items.size}")
        println("Stage A (single attempt) - show collected immediately")
        println("Index\tRJ\tAResult\tADtMs\tShownAtMs\tTitle")
        stageA.forEach { r ->
            val shown = r.shownAtMs?.toString().orEmpty()
            println("${r.item.index}\t${r.item.rj}\t${r.result}\t${r.dtMs}\t$shown\t${r.item.title}")
        }
        if (stageBAppeared.isNotEmpty()) {
            println("Stage B (retry) - collected discovered later:")
            stageBAppeared.entries.sortedBy { it.key }.forEach { (idx, shownAt) ->
                val item = items.firstOrNull { it.index == idx }
                val rj = item?.rj.orEmpty()
                println("$idx\t$rj\tShownAtMs=$shownAt")
            }
        } else {
            println("Stage B (retry) - no additional collected discovered")
        }

        val shownTimes = stageA.mapNotNull { it.shownAtMs } + stageBAppeared.values
        val sortedShown = shownTimes.sorted()
        val p50Shown = percentileNearestRank(sortedShown, 50.0)
        val p95Shown = percentileNearestRank(sortedShown, 95.0)
        val maxShown = sortedShown.lastOrNull() ?: 0L
        println("Show-latency summary collectedShown=${shownTimes.size} p50=${p50Shown}ms p95=${p95Shown}ms max=${maxShown}ms")
    }

    private suspend fun fetchDlsiteSearchItems(keyword: String, page: Int, order: String): List<DlsiteItem> {
        return withContext(Dispatchers.IO) {
            val encodedKeyword = URLEncoder.encode(keyword.trim(), "UTF-8")
            val normalizedOrder = order.trim().ifBlank { "trend" }
            val o = URLEncoder.encode(normalizedOrder, "UTF-8")
            val p = page.coerceAtLeast(1)
            val modernBase =
                "https://www.dlsite.com/maniax/fsr/=/language/cn/sex_category%5B0%5D/male/work_category%5B0%5D/doujin/" +
                    "order%5B0%5D/$o/work_type_category%5B0%5D/audio/per_page/30/show_type/3/from/fsr.again"
            val modern = "$modernBase/keyword/$encodedKeyword/page/$p"
            val legacyBase =
                "https://www.dlsite.com/maniax/fsr/=/language/cn/sex_category%5B0%5D/male/work_category%5B0%5D/doujin/work_type_category%5B0%5D/audio/per_page/30/show_type/1/keyword/"
            val legacy = "$legacyBase$encodedKeyword/page/$p/without_order/1/order/$o"

            val urls = listOf(modern, legacy)
            var last: List<DlsiteItem> = emptyList()
            for (u in urls) {
                val doc = Jsoup.connect(u)
                    .userAgent(NetworkHeaders.USER_AGENT)
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8,ja;q=0.7")
                    .header("Cookie", "locale=zh_CN; adultchecked=1")
                    .ignoreHttpErrors(true)
                    .timeout(15000)
                    .get()
                val parsed = parseDlsiteSearchDoc(doc)
                if (parsed.isNotEmpty()) {
                    return@withContext parsed.take(30)
                }
                last = parsed
            }
            last.take(30)
        }
    }

    private fun parseDlsiteSearchDoc(doc: org.jsoup.nodes.Document): List<DlsiteItem> {
        val results = mutableListOf<DlsiteItem>()
        val container = doc.selectFirst("#search_result_list.loading_display_open")
            ?: doc.select("#search_result_list").lastOrNull()
        var items = container?.select("table tr")?.toList().orEmpty().filter { it.select("th").isEmpty() }
        if (items.isEmpty()) {
            items = container?.select("li")?.toList().orEmpty()
        }
        if (items.isEmpty()) {
            items = doc.select("#search_result_img_box li").toList()
        }
        var idx = 0
        for (item in items) {
            val titleTag = item.selectFirst(".work_name a") ?: continue
            val title = titleTag.text().trim()
            val link = titleTag.attr("href")
            val rj = Regex("""RJ\d+""", RegexOption.IGNORE_CASE).find(link)?.value?.uppercase().orEmpty()
            if (rj.isBlank()) continue
            results.add(DlsiteItem(index = idx, rj = rj, title = title))
            idx += 1
        }
        return results
    }

    private suspend fun checkAsmrOneCollectedWithRetry(client: OkHttpClient, item: DlsiteItem): CheckReport {
        val details = ArrayList<CheckDetail>(3)
        val startAll = System.nanoTime()
        var falseCount = 0
        repeat(3) { idx ->
            if (idx > 0) kotlinx.coroutines.delay(250L * idx)
            val t0 = System.nanoTime()
            val r = runCatching { queryAsmrOneWorkCollected(client, item.rj) }.getOrNull() ?: CollectedResult.Unknown
            val dt = (System.nanoTime() - t0) / 1_000_000L
            details.add(CheckDetail(attempt = idx + 1, result = r, dtMs = dt))
            when (r) {
                CollectedResult.Collected -> {
                    val totalDt = (System.nanoTime() - startAll) / 1_000_000L
                    return CheckReport(item = item, final = CollectedResult.Collected, totalDtMs = totalDt, details = details)
                }
                CollectedResult.NotCollected -> falseCount += 1
                CollectedResult.Unknown -> {}
            }
        }
        val final = if (falseCount == 3) CollectedResult.NotCollected else CollectedResult.Unknown
        val totalDt = (System.nanoTime() - startAll) / 1_000_000L
        return CheckReport(item = item, final = final, totalDtMs = totalDt, details = details)
    }

    private fun queryAsmrOneWorkCollected(client: OkHttpClient, rj: String): CollectedResult {
        val normalized = rj.trim().uppercase()
        if (!Regex("""RJ\d{6,}""").matches(normalized)) return CollectedResult.Unknown
        val digits = normalized.removePrefix("RJ")
        val digitsTrimmed = digits.trimStart('0').ifBlank { digits }
        val candidates = linkedSetOf(digits, digitsTrimmed).filter { it.isNotBlank() }
        if (candidates.isEmpty()) return CollectedResult.Unknown
        val base = "https://api.asmr-200.com/api/work/"
        for (workId in candidates) {
            val url = "$base$workId"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Eara-LatencyTest")
                .build()
            val (code, body) = client.newCall(req).execute().use { resp ->
                val b = resp.body?.string().orEmpty()
                resp.code to b
            }
            if (code == 404) continue
            if (code !in 200..299) return CollectedResult.Unknown
            if (body.isBlank()) return CollectedResult.Unknown
            val obj = runCatching { Gson().fromJson(body, Map::class.java) as? Map<*, *> }.getOrNull() ?: return CollectedResult.Unknown
            val sourceId = (obj["source_id"] as? String).orEmpty().trim().uppercase()
            val original = (obj["original_workno"] as? String).orEmpty().trim().uppercase()
            if (sourceId == normalized || original == normalized) return CollectedResult.Collected
            val editions = obj["language_editions"] as? List<*> ?: emptyList<Any>()
            val hitEdition = editions.any { e ->
                val em = e as? Map<*, *> ?: return@any false
                (em["workno"] as? String).orEmpty().trim().uppercase() == normalized
            }
            return if (hitEdition) CollectedResult.Collected else CollectedResult.Unknown
        }
        return CollectedResult.NotCollected
    }

    @Suppress("UNUSED")
    private fun queryAsmrOneSearchCollectedLegacy(client: OkHttpClient, rj: String): CollectedResult {
        val normalized = rj.trim().uppercase()
        if (!Regex("""RJ\d{6,}""").matches(normalized)) return CollectedResult.Unknown
        val url = "https://api.asmr-200.com/api/search/$normalized?page=1&order=create_date&sort=desc&pageSize=20&subtitle=0&includeTranslationWorks=true"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Eara-LatencyTest")
            .build()
        val body = client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return CollectedResult.Unknown
            resp.body?.string().orEmpty()
        }
        if (body.isBlank()) return CollectedResult.Unknown
        val obj = runCatching { Gson().fromJson(body, Map::class.java) as? Map<*, *> }.getOrNull() ?: return CollectedResult.Unknown
        val works = obj["works"] as? List<*> ?: return CollectedResult.Unknown
        val hit = works.any { w ->
            val m = w as? Map<*, *> ?: return@any false
            val sourceId = (m["source_id"] as? String).orEmpty().trim().uppercase()
            val original = (m["original_workno"] as? String).orEmpty().trim().uppercase()
            if (sourceId == normalized || original == normalized) return@any true
            val editions = m["language_editions"] as? List<*> ?: return@any false
            editions.any { e ->
                val em = e as? Map<*, *> ?: return@any false
                (em["workno"] as? String).orEmpty().trim().uppercase() == normalized
            }
        }
        return if (hit) CollectedResult.Collected else CollectedResult.NotCollected
    }

    private fun percentileNearestRank(sorted: List<Long>, percentile: Double): Long {
        if (sorted.isEmpty()) return 0L
        val n = sorted.size
        val rank = ceil(percentile / 100.0 * n).toInt().coerceIn(1, n)
        return sorted[rank - 1]
    }
}
