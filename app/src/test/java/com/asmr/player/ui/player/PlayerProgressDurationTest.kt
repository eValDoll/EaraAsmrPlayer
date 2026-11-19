package com.asmr.player.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.asmr.player.ui.createEaraComposeRule
import com.asmr.player.ui.theme.AsmrPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlayerProgressDurationTest {
    @get:Rule
    val composeRule = createEaraComposeRule()

    @Test
    fun tappingThreeQuartersUsesThePlayerTimelineWhenLibraryDurationIsTooLong() {
        val saved = ResolvedTrackDuration("local.wav", 120_000L)
        var requestedPositionMs = -1L
        composeRule.setContent {
            AsmrPlayerTheme {
                Box(Modifier.width(240.dp).testTag("progress")) {
                    PlayerProgress(
                        positionMs = 10_000L,
                        durationMs = progressDurationMs("local.wav", 60_000L, saved),
                        sliceUiState = SliceUiState(),
                        onSeekTo = { requestedPositionMs = it },
                        onScrubbingChanged = {},
                        onSelectSlice = {},
                        onLongPressSlice = {},
                        onUpdateSliceRange = { _, _, _ -> },
                        activeColor = Color.White,
                        inactiveColor = Color.Gray,
                    )
                }
            }
        }

        composeRule.onNodeWithTag("progress").performTouchInput {
            val inset = 8.dp.toPx()
            click(Offset(inset + (width - 2 * inset) * 0.75f, 16.dp.toPx()))
        }
        composeRule.runOnIdle { assertEquals(45_000L, requestedPositionMs) }
    }

    @Test
    fun unknownTimelineUsesOnlyTheCurrentTracksStoredDuration() {
        val saved = ResolvedTrackDuration("first.wav", 60_000L)
        assertEquals(60_000L, progressDurationMs("first.wav", 0L, saved))
        assertEquals(0L, progressDurationMs("second.wav", 0L, saved))
        assertEquals(0L, progressDurationMs("second.wav", 0L, null))
        assertEquals(90_000L, progressDurationMs("first.wav", 90_000L, saved))
    }
}
