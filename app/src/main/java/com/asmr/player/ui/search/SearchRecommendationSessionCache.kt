package com.asmr.player.ui.search

import com.asmr.player.data.remote.api.AsmrOneRecommendationResponse
import com.asmr.player.data.remote.api.normalizeRecommendationRjs
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchRecommendationSessionCache @Inject constructor() {
    private val lock = Any()
    private var cachedSession: CachedRecommendationSession? = null

    internal fun read(
        seedRjs: List<String>,
        excludeRjs: List<String>,
        nowElapsedMs: Long = monotonicNowMs()
    ): AsmrOneRecommendationResponse? = synchronized(lock) {
        val cached = cachedSession ?: return@synchronized null
        val elapsedMs = nowElapsedMs - cached.savedAtElapsedMs
        if (
            cached.key != recommendationSessionKey(seedRjs, excludeRjs) ||
            elapsedMs < 0L ||
            elapsedMs >= RECOMMENDATION_SESSION_CACHE_TTL_MS
        ) {
            if (elapsedMs < 0L || elapsedMs >= RECOMMENDATION_SESSION_CACHE_TTL_MS) {
                cachedSession = null
            }
            return@synchronized null
        }
        cached.response.snapshot()
    }

    internal fun write(
        seedRjs: List<String>,
        excludeRjs: List<String>,
        response: AsmrOneRecommendationResponse,
        nowElapsedMs: Long = monotonicNowMs()
    ) {
        val key = recommendationSessionKey(seedRjs, excludeRjs)
        if (key.seedRjs.isEmpty()) return
        synchronized(lock) {
            cachedSession = CachedRecommendationSession(
                key = key,
                response = response.snapshot(),
                savedAtElapsedMs = nowElapsedMs
            )
        }
    }
}

private data class RecommendationSessionKey(
    val seedRjs: List<String>,
    val excludeRjs: List<String>
)

private data class CachedRecommendationSession(
    val key: RecommendationSessionKey,
    val response: AsmrOneRecommendationResponse,
    val savedAtElapsedMs: Long
)

private fun recommendationSessionKey(
    seedRjs: List<String>,
    excludeRjs: List<String>
): RecommendationSessionKey = RecommendationSessionKey(
    seedRjs = normalizeRecommendationRjs(seedRjs, MAX_RECOMMENDATION_CACHE_SEEDS),
    excludeRjs = normalizeRecommendationRjs(excludeRjs, MAX_RECOMMENDATION_CACHE_EXCLUDES)
)

private fun AsmrOneRecommendationResponse.snapshot(): AsmrOneRecommendationResponse = copy(
    items = items?.toList(),
    matchedSeedRjs = matchedSeedRjs?.toList(),
    unmatchedSeedRjs = unmatchedSeedRjs?.toList()
)

private fun monotonicNowMs(): Long = System.nanoTime() / 1_000_000L

private const val MAX_RECOMMENDATION_CACHE_SEEDS = 20
private const val MAX_RECOMMENDATION_CACHE_EXCLUDES = 200
internal const val RECOMMENDATION_SESSION_CACHE_TTL_MS = 30 * 60 * 1_000L
