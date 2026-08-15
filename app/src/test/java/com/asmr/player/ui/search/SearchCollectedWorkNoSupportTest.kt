package com.asmr.player.ui.search

import com.asmr.player.data.remote.api.AsmrOneCollectedSearchItem
import com.asmr.player.data.remote.api.Circle
import com.asmr.player.data.remote.api.WorkDetailsResponse
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchCollectedWorkNoSupportTest {
    @Test
    fun backendBJMappingIsAcceptedAsCollectedWorkNumber() {
        val item = Gson().fromJson(
            """{"workId":100000062,"rj":"BJ02370869","title":"作品"}""",
            AsmrOneCollectedSearchItem::class.java
        )

        val album = item.toCollectedAlbum()

        assertEquals("BJ02370869", album.workId)
        assertEquals("BJ02370869", album.rjCode)
        assertEquals(100000062, album.asmrOneWorkId)
    }

    @Test
    fun missingBackendWorkNumberFallsBackToAsmrOneDetailsSourceId() {
        val item = AsmrOneCollectedSearchItem(
            workId = 100000062,
            title = "【ASMR】测试作品"
        )
        val details = WorkDetailsResponse(
            id = 100000062,
            source_id = "BJ02370869",
            title = "【ASMR】测试作品",
            circle = Circle("测试社团"),
            vas = emptyList(),
            tags = emptyList(),
            duration = 0,
            mainCoverUrl = "",
            dl_count = 0,
            price = 0
        )

        val album = item.toCollectedAlbum(details.resolvedWorkNo())

        assertEquals("BJ02370869", album.workId)
        assertEquals("BJ02370869", album.rjCode)
        assertEquals(true, album.hasAsmrOne)
        assertEquals(100000062, album.asmrOneWorkId)
    }

    @Test
    fun optionalWorkNumberCancellationDoesNotCancelSearchCaller() = runBlocking {
        val workNo = resolveOptionalCollectedWorkNo(timeoutMs = 1_000L) {
            throw CancellationException("optional request canceled")
        }

        assertEquals("", workNo)
    }
}
