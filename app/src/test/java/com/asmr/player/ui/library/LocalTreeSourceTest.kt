package com.asmr.player.ui.library

import com.asmr.player.domain.model.Album
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalTreeSourceTest {
    @Test
    fun differentImportAndDownloadRoots_createTwoSourceLayers() {
        val sources = localTreeSourcesForAlbum(
            Album(
                title = "作品",
                path = "/library/import/RJ12345678",
                localPath = "/library/import/RJ12345678",
                downloadPath = "/library/download/RJ12345678",
            ),
        )

        assertEquals(
            listOf(LocalTreeSourceKind.Imported, LocalTreeSourceKind.Downloaded),
            sources.map { it.kind },
        )
    }

    @Test
    fun samePhysicalRoot_isRepresentedOnlyOnce() {
        val sources = localTreeSourcesForAlbum(
            Album(
                title = "作品",
                path = "/library/RJ12345678",
                localPath = "/library/RJ12345678/.",
                downloadPath = "/library/RJ12345678",
            ),
        )

        assertEquals(1, sources.size)
        assertEquals(LocalTreeSourceKind.Imported, sources.single().kind)
    }
}
