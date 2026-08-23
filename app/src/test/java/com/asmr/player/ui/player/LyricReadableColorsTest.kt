package com.asmr.player.ui.player

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricReadableColorsTest {

    @Test
    fun lyricTextColorFollowsAppThemeWithoutBackdropSampling() {
        assertEquals(Color.White, themedLyricTextColor(isDark = true))
        assertEquals(Color.Black, themedLyricTextColor(isDark = false))
    }

    @Test
    fun lyricsPageInactiveTextUsesReadableFadedThemeForeground() {
        assertEquals(
            Color.White,
            themedInactiveLyricTextColor(
                isDark = true,
                useReadablePageInactiveText = true
            )
        )
        assertEquals(
            Color.Black,
            themedInactiveLyricTextColor(
                isDark = false,
                useReadablePageInactiveText = true
            )
        )
        assertEquals(
            Color.Black.copy(alpha = 0.58f),
            themedInactiveLyricTextColor(
                isDark = false,
                useReadablePageInactiveText = false
            )
        )
        assertEquals(0.76f, inactiveLyricLineAlpha(isDark = true), 0f)
        assertEquals(0.72f, inactiveLyricLineAlpha(isDark = false), 0f)
    }
}
