package com.asmr.player.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.sqrt

data class ThemeTransitionRequest(
    val origin: Offset,
    val targetIsDark: Boolean,
    val targetPref: String
)

val LightBackground = Color(0xFFF0F4F8)
val DarkBackground = Color(0xFF121212)

val LocalThemeTransitionTrigger = staticCompositionLocalOf<((ThemeTransitionRequest) -> Unit)?> { null }

@Composable
fun ThemeCircularRevealOverlay(
    request: ThemeTransitionRequest,
    onAnimationEnd: () -> Unit
) {
    val targetColor = if (request.targetIsDark) DarkBackground else LightBackground

    val animationProgress = remember { Animatable(0f) }

    LaunchedEffect(request) {
        animationProgress.snapTo(0f)
        animationProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
        )
        onAnimationEnd()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val maxRadius = sqrt(
                size.width * size.width + size.height * size.height
            )
            val radius = maxRadius * animationProgress.value
            drawCircle(
                color = targetColor,
                radius = radius,
                center = request.origin
            )
        }
    }
}
