package com.asmr.player.ui.common

import com.asmr.player.domain.model.Album
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumCoverHelpersTest {
    @Test
    fun albumStableKey_prefersRjOrWorkId() {
        val album = Album(
            title = "Title",
            path = "",
            rjCode = "RJ123456",
            coverUrl = "https://example.com/a.jpg",
            circle = "Circle",
            cv = "CV"
        )

        assertEquals("RJ123456", albumStableKey(album))
        assertEquals(
            albumStableKey(album),
            albumStableKey(album.copy(id = 99L, title = "Other", circle = "Other Circle", cv = "Other CV"))
        )
    }

    @Test
    fun albumStableKey_usesFallbackIdentityWhenNoRjOrWorkId() {
        val album = Album(
            title = "Title",
            path = "",
            coverUrl = "https://example.com/a.jpg",
            circle = "Circle",
            cv = "CV"
        )
        val sameIdentityDifferentId = album.copy(id = 42L, description = "Something else")
        val changedTitle = album.copy(title = "Different")

        assertEquals(albumStableKey(album), albumStableKey(sameIdentityDifferentId))
        assertNotEquals(albumStableKey(album), albumStableKey(changedTitle))
    }

    @Test
    fun albumStableKey_keepsAsmrOneIdentityWhileWorkNumberIsBeingResolved() {
        val unresolved = Album(
            title = "作品",
            path = "",
            asmrOneWorkId = 100000062
        )
        val resolved = unresolved.copy(workId = "BJ02370869", rjCode = "BJ02370869")

        assertEquals("asmr-one:100000062", albumStableKey(unresolved))
        assertEquals(albumStableKey(unresolved), albumStableKey(resolved))
    }

    @Test
    fun shouldFadeInCover_disablesWhileScrolling() {
        assertTrue(shouldFadeInCover(false))
        assertFalse(shouldFadeInCover(true))
        assertFalse(shouldFadeInCover(true, baseEnabled = false))
    }
}
