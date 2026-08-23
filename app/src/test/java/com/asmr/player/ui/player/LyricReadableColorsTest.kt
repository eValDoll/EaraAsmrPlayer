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
}
