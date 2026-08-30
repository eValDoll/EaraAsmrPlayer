package com.asmr.player.ui.library

import com.asmr.player.ui.common.ImagePreviewItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImagePreviewSupportTest {

    @Test
    fun buildDirectoryImagePreviewRequest_collectsOnlyCurrentDirectoryImagesInOrder() {
        val files = listOf(
            directoryFile(path = "disc1/cover.jpg", title = "cover", type = TreeFileType.Image, absolutePath = "/album/disc1/cover.jpg"),
            directoryFile(path = "disc1/track1.mp3", title = "track1", type = TreeFileType.Audio, absolutePath = "/album/disc1/track1.mp3"),
            directoryFile(path = "disc1/page02.png", title = "page02", type = TreeFileType.Image, absolutePath = "/album/disc1/page02.png")
        )

        val result = buildDirectoryImagePreviewRequest(
            files = files,
            clickedPath = "disc1/page02.png",
            toPreviewItem = { file ->
                ImagePreviewItem(
                    key = file.path,
                    title = file.title,
                    imageModel = file.absolutePath,
                    openPathOrUrl = file.absolutePath
                )
            }
        )

        requireNotNull(result)
        assertEquals(listOf("disc1/cover.jpg", "disc1/page02.png"), result.items.map { it.key })
        assertEquals(1, result.initialIndex)
    }

    @Test
    fun buildDirectoryImagePreviewRequest_returnsNullWhenClickedFileIsNotInImageSet() {
        val files = listOf(
            directoryFile(path = "disc1/cover.jpg", title = "cover", type = TreeFileType.Image, absolutePath = "/album/disc1/cover.jpg")
        )

        val result = buildDirectoryImagePreviewRequest(
            files = files,
            clickedPath = "disc1/missing.png",
            toPreviewItem = { file ->
                ImagePreviewItem(
                    key = file.path,
                    title = file.title,
                    imageModel = file.absolutePath,
                    openPathOrUrl = file.absolutePath
                )
            }
        )

        assertNull(result)
    }

    @Test
    fun buildGalleryImagePreviewRequest_preservesGalleryOrderAndSelectedIndex() {
        val urls = listOf(
            "https://img.example/1.jpg",
            "https://img.example/2.jpg",
            "https://img.example/3.jpg"
        )

        val result = buildGalleryImagePreviewRequest(
            galleryItems = urls.map { url ->
                ImagePreviewItem(
                    key = url,
                    title = url.substringAfterLast('/'),
                    imageModel = url,
                    openPathOrUrl = url
                )
            },
            clickedKey = "https://img.example/2.jpg"
        )

        requireNotNull(result)
        assertEquals(urls, result.items.map { it.key })
        assertEquals(1, result.initialIndex)
    }

    private fun directoryFile(
        path: String,
        title: String,
        type: TreeFileType,
        absolutePath: String
    ): DirectoryFileItem {
        return DirectoryFileItem(
            path = path,
            title = title,
            fileType = type,
            isPlayable = false,
            absolutePath = absolutePath,
            url = absolutePath
        )
    }
}
