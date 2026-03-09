package com.asmr.player.data.repository

import com.asmr.player.data.local.db.dao.TrackSliceDao
import com.asmr.player.data.local.db.entities.TrackSliceEntity
import com.asmr.player.domain.model.Slice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackSliceRepository @Inject constructor(
    private val trackSliceDao: TrackSliceDao
) {
    fun observeSlices(trackMediaId: String): Flow<List<Slice>> {
        return trackSliceDao.observeSlices(trackMediaId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun getSlicesOnce(trackMediaId: String): List<Slice> {
        return trackSliceDao.getSlicesOnce(trackMediaId).map { it.toDomain() }
    }

    suspend fun appendSlice(trackMediaId: String, startMs: Long, endMs: Long): Long {
        val normalized = normalize(startMs = startMs, endMs = endMs)
        val overlap = trackSliceDao.hasOverlap(
            trackMediaId = trackMediaId,
            startMs = normalized.first,
            endMs = normalized.second,
            excludeId = -1L
        )
        if (overlap) throw SliceOverlapException()
        val now = System.currentTimeMillis()
        return trackSliceDao.upsert(
            TrackSliceEntity(
                trackMediaId = trackMediaId,
                startMs = normalized.first,
                endMs = normalized.second,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun updateSliceRange(id: Long, startMs: Long, endMs: Long) {
        val normalized = normalize(startMs = startMs, endMs = endMs)
        val existing = trackSliceDao.getById(id) ?: return
        val overlap = trackSliceDao.hasOverlap(
            trackMediaId = existing.trackMediaId,
            startMs = normalized.first,
            endMs = normalized.second,
            excludeId = id
        )
        if (overlap) throw SliceOverlapException()
        trackSliceDao.updateRange(id = id, startMs = normalized.first, endMs = normalized.second)
    }

    suspend fun deleteSlice(id: Long) {
        trackSliceDao.deleteById(id)
    }

    suspend fun clearTrack(trackMediaId: String) {
        trackSliceDao.deleteByTrack(trackMediaId)
    }
}

private fun TrackSliceEntity.toDomain(): Slice {
    return Slice(
        id = id,
        startMs = startMs,
        endMs = endMs
    )
}

private fun normalize(startMs: Long, endMs: Long): Pair<Long, Long> {
    val start = startMs.coerceAtLeast(0L)
    val end = endMs.coerceAtLeast(0L)
    require(end > start) { "Slice end must be greater than start." }
    return start to end
}
