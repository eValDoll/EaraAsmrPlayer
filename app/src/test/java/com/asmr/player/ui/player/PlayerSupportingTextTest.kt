package com.asmr.player.ui.player

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.asmr.player.data.settings.LyricsPageSettings
import com.asmr.player.data.settings.PlayerBackgroundMode
import com.asmr.player.ui.createEaraComposeRule
import com.asmr.player.ui.theme.AsmrPlayerTheme
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.ui.theme.LocalAsmrColorScheme
import com.asmr.player.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28], qualifiers = "w500dp-h900dp-mdpi")
class PlayerSupportingTextTest {
    @get:Rule
    val compose = createEaraComposeRule()

    @Test
    fun noLyricsText_usesSameNeutralColorInPreviewAndExpandedLayouts() {
        val surfaceCase = mutableStateOf(surfaceCases.first())
        val expanded = mutableStateOf(false)
        compose.setContent {
            PlayerTextTheme(surfaceCase.value) {
                val colors = rememberLyricReadableColors(accentColor = AsmrTheme.colorScheme.primary)
                Box(modifier = Modifier.fillMaxWidth().height(240.dp)) {
                    if (expanded.value) {
                        NowPlayingLyricsSurface(
                            isLandscape = false,
                            playbackPositionMs = 0L,
                            lyrics = emptyList(),
                            lyricColors = colors,
                            spectrumColor = Color.Magenta,
                            lyricsPageSettings = LyricsPageSettings(),
                            onSeekTo = {},
                        )
                    } else {
                        NowPlayingLyricsPreview(
                            lyrics = emptyList(),
                            currentPosition = 0L,
                            onOpenLyrics = {},
                            colors = colors,
                            emptyText = "暂无歌词",
                        )
                    }
                }
            }
        }

        surfaceCases.forEach { case ->
            listOf(false, true).forEach { useExpandedLayout ->
                compose.runOnIdle {
                    surfaceCase.value = case
                    expanded.value = useExpandedLayout
                }
                assertTextColor("暂无歌词", case.expectedTextColor)
            }
        }
    }

    @Test
    fun progressTimes_followActualSurfaceModeInsteadOfGlobalThemeOrAccent() {
        val surfaceCase = mutableStateOf(surfaceCases.first())
        compose.setContent {
            PlayerTextTheme(surfaceCase.value) {
                PlayerProgress(
                    positionMs = 5_000L,
                    durationMs = 20_000L,
                    sliceUiState = SliceUiState(),
                    onSeekTo = {},
                    onScrubbingChanged = {},
                    onSelectSlice = {},
                    onLongPressSlice = {},
                    onUpdateSliceRange = { _, _, _ -> },
                    activeColor = Color.Magenta,
                    inactiveColor = Color.Green,
                )
            }
        }

        surfaceCases.forEach { case ->
            compose.runOnIdle { surfaceCase.value = case }
            assertTextColor("00:05", case.expectedTextColor)
            assertTextColor("00:20", case.expectedTextColor)
        }
    }

    private fun assertTextColor(text: String, expected: Color) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals("$text 应使用不透明中性色", expected, layouts.single().layoutInput.style.color)
    }

    @Composable
    private fun PlayerTextTheme(case: SurfaceCase, content: @Composable () -> Unit) {
        AsmrPlayerTheme(mode = case.globalMode) {
            val base = AsmrTheme.colorScheme.copy(
                primary = case.primary,
                onPrimary = case.onPrimary,
                textSecondary = Color.Magenta,
                textTertiary = Color.Green,
            )
            val surface = playerSurfaceColors(base, case.backgroundMode, coverEnabled = false)
            CompositionLocalProvider(LocalAsmrColorScheme provides surface, content = content)
        }
    }

    private data class SurfaceCase(
        val globalMode: ThemeMode,
        val backgroundMode: PlayerBackgroundMode,
        val primary: Color,
        val onPrimary: Color,
        val expectedTextColor: Color,
    )

    private val surfaceCases = listOf(
        SurfaceCase(ThemeMode.Light, PlayerBackgroundMode.Solid, Color.Magenta, Color.White, Color.Black),
        SurfaceCase(ThemeMode.Dark, PlayerBackgroundMode.Solid, Color.Blue, Color.White, Color.White),
        SurfaceCase(ThemeMode.Light, PlayerBackgroundMode.Flowing, Color.Red, Color.Black, Color.White),
        SurfaceCase(ThemeMode.Dark, PlayerBackgroundMode.Theme, Color(0xFF29B6F6), Color.Black, Color.Black),
    )
}
