package com.asmr.player.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AlbumCoverShadowLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun listShadow_keepsContentAtTheOriginalBounds() {
        checkBounds(coverSize = 124.dp, outset = 36.dp, fixedSize = true)
    }

    @Test
    fun darkGridShadow_keepsBoundsWithFractionalPixelOutsets() {
        checkBounds(coverSize = 200.dp, outset = 58.65.dp, fixedSize = true)
    }

    @Test
    fun shadowCache_doesNotAddItsPaddingToWrapContentSize() {
        checkBounds(coverSize = 124.dp, outset = 46.5.dp, fixedSize = false)
    }

    private fun checkBounds(coverSize: Dp, outset: Dp, fixedSize: Boolean) {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1.5f)) {
                Box(
                    modifier = Modifier
                        .then(if (fixedSize) Modifier.size(coverSize) else Modifier)
                        .testTag("shadowBounds")
                        .cacheAlbumCoverShadow(outset)
                ) {
                    Box(
                        modifier = (if (fixedSize) Modifier.fillMaxSize() else Modifier.size(coverSize))
                            .testTag("contentBounds")
                    )
                }
            }
        }
        val outer = composeRule.onNodeWithTag("shadowBounds").getUnclippedBoundsInRoot()
        val content = composeRule.onNodeWithTag("contentBounds").getUnclippedBoundsInRoot()
        assertEquals(coverSize.value, (outer.right - outer.left).value, 0.01f)
        assertEquals(coverSize.value, (outer.bottom - outer.top).value, 0.01f)
        assertEquals(outer, content)
    }
}
