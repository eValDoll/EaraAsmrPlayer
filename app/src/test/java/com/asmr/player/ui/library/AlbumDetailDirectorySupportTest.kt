package com.asmr.player.ui.library

import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumDetailDirectorySupportTest {

    @Test
    fun buildBreadcrumbSegments_preservesHierarchyOrder() {
        val result = buildBreadcrumbSegments("disc1/cd2/finale")

        assertEquals(listOf("disc1", "cd2", "finale"), result.map { it.label })
        assertEquals(
            listOf("disc1", "disc1/cd2", "disc1/cd2/finale"),
            result.map { it.path }
        )
    }

    @Test
    fun folderPathPrefixes_returnsEachParentPrefix() {
        assertEquals(
            listOf("disc1", "disc1/cd2", "disc1/cd2/finale"),
            folderPathPrefixes("disc1/cd2/finale")
        )
    }
}
