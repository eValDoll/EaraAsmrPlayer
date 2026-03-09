package com.asmr.player.ui.sidepanel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.unit.dp
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.ui.theme.AsmrTheme
import org.junit.Rule
import org.junit.Test

class RecentAlbumsListInitialRenderAndroidTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun recentAlbumsList_withNonEmptyItems_rendersTitles() {
        val items = listOf(
            RecentAlbumUiItem(
                album = AlbumEntity(
                    id = 1L,
                    title = "Album 1",
                    path = "/a"
                ),
                resume = null
            ),
            RecentAlbumUiItem(
                album = AlbumEntity(
                    id = 2L,
                    title = "Album 2",
                    path = "/b"
                ),
                resume = null
            )
        )

        composeRule.setContent {
            AsmrTheme {
                Box(modifier = Modifier.width(360.dp).height(600.dp)) {
                    RecentAlbumsList(
                        items = items,
                        onOpenAlbum = {},
                        onResumePlay = {}
                    )
                }
            }
        }

        composeRule.onNodeWithText("Album 1").assertExists()
        composeRule.onNodeWithText("Album 2").assertExists()
    }
}

