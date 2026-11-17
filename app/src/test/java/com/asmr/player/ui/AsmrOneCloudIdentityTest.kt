package com.asmr.player.ui

import com.asmr.player.data.remote.api.AsmrCloudWork
import com.asmr.player.data.repository.mergeCloudWorks
import com.asmr.player.ui.asmrone.cloudPlaylistId
import org.junit.Assert.*
import org.junit.Test

class AsmrOneCloudIdentityTest {
    @Test fun signedMediaUrlsAndSourceNumbersAreNotMembershipIdentity() {
        val japanese = AsmrCloudWork(101, "日文", "RJ123456")
        val chinese = AsmrCloudWork(202, "中文", "RJ654321")
        val merged = mergeCloudWorks(listOf(japanese, japanese, chinese), listOf(japanese to false))
        assertEquals(listOf(202L), merged.map { it.id })
        assertEquals("RJ654321", merged.single().toAlbum().rjCode)
        assertEquals(202, merged.single().toAlbum().asmrOneWorkId)
    }
    @Test fun playlistLinksAcceptOnlyOfficialPagesAndExactUuids() {
        val id = "620db9dd-f5ad-48b0-a10a-d46bb046bf76"
        assertEquals(id, cloudPlaylistId(id))
        assertEquals(id, cloudPlaylistId("https://asmr.one/playlist?id=$id"))
        assertEquals(id, cloudPlaylistId("https://asmr-200.com/playlist/$id"))
        assertNull(cloudPlaylistId("https://evil.example/playlist?id=$id"))
        assertNull(cloudPlaylistId("https://asmr.one.evil.example/playlist?id=$id"))
        assertNull(cloudPlaylistId("https://asmr.one/work?id=$id"))
        assertNull(cloudPlaylistId("1-1-1-1-1"))
    }
}
