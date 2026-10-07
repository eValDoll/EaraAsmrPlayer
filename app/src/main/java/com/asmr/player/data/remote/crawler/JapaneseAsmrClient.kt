package com.asmr.player.data.remote.crawler

import com.asmr.player.data.remote.api.AsmrOneAvailabilityApi
import com.asmr.player.data.remote.api.AsmrOneTrackNodeResponse
import com.asmr.player.domain.model.CollectedSearchSource
import com.asmr.player.util.DlsiteWorkNo
import javax.inject.Inject
import javax.inject.Singleton

enum class AlbumResourceSource(val label: String) {
    AsmrOne("asmr.one"), JapaneseAsmr("Japanese ASMR")
}

data class JapaneseAsmrWork(val pageUrl: String, val tree: List<AsmrOneTrackNodeResponse>)

@Singleton
class JapaneseAsmrClient @Inject constructor(private val api: AsmrOneAvailabilityApi) {
    suspend fun load(workNo: String): JapaneseAsmrWork {
        val rj = DlsiteWorkNo.normalizeWorkNo(workNo, minimumDigits = 6)
        require(rj.isNotBlank())
        val result = api.getTrackTreeByRj(rj, CollectedSearchSource.JapaneseAsmr)
        return JapaneseAsmrWork(result.pageUrl.orEmpty(), result.trackTree.orEmpty())
    }
}

internal fun isJapaneseAsmrAudioName(name: String): Boolean =
    name.substringAfterLast('.', "").lowercase() in setOf("mp3", "m4a", "flac", "opus")
