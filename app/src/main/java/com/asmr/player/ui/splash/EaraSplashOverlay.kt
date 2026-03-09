package com.asmr.player.ui.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.core.tween
import com.asmr.player.R
import kotlinx.coroutines.delay

private enum class SplashPhase {
    Entering,
    Waiting,
    Exiting
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
fun EaraSplashOverlay(
    isReady: Boolean,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    var phase by remember { mutableStateOf(SplashPhase.Entering) }
    var canExit by remember { mutableStateOf(false) }
    var finishedCalled by remember { mutableStateOf(false) }

    val infinite = rememberInfiniteTransition(label = "eara_splash")
    val breathe by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath"
    )

    LaunchedEffect(Unit) {
        canExit = false
        phase = SplashPhase.Waiting
        delay(240)
        canExit = true
    }

    LaunchedEffect(isReady, canExit, phase) {
        if (!isReady || !canExit) return@LaunchedEffect
        if (phase == SplashPhase.Exiting) return@LaunchedEffect
        phase = SplashPhase.Exiting
    }

    val transition = updateTransition(targetState = phase, label = "splash_phase")
    val overlayAlpha by transition.animateFloat(
        transitionSpec = {
            if (targetState == SplashPhase.Exiting) tween(durationMillis = 180, easing = LinearOutSlowInEasing)
            else tween(durationMillis = 0)
        },
        label = "overlay_alpha"
    ) { state ->
        if (state == SplashPhase.Exiting) 0f else 1f
    }
    val logoAlpha by transition.animateFloat(
        transitionSpec = {
            if (initialState == SplashPhase.Entering && targetState == SplashPhase.Waiting) {
                tween(durationMillis = 360, easing = LinearOutSlowInEasing)
            } else {
                tween(durationMillis = 0)
            }
        },
        label = "logo_alpha"
    ) { state ->
        if (state == SplashPhase.Entering) 0f else 1f
    }
    val logoBaseScale by transition.animateFloat(
        transitionSpec = {
            if (initialState == SplashPhase.Entering && targetState == SplashPhase.Waiting) {
                tween(durationMillis = 520, easing = FastOutSlowInEasing)
            } else {
                tween(durationMillis = 0)
            }
        },
        label = "logo_scale"
    ) { state ->
        if (state == SplashPhase.Entering) 0.78f else 1f
    }
    val dotAlpha by transition.animateFloat(
        transitionSpec = {
            if (initialState == SplashPhase.Entering && targetState == SplashPhase.Waiting) {
                tween(durationMillis = 220, easing = LinearOutSlowInEasing)
            } else {
                tween(durationMillis = 0)
            }
        },
        label = "dot_alpha"
    ) { state ->
        if (state == SplashPhase.Entering) 0f else 1f
    }
    val dotScale by transition.animateFloat(
        transitionSpec = {
            if (initialState == SplashPhase.Entering && targetState == SplashPhase.Waiting) {
                tween(durationMillis = 520, easing = FastOutSlowInEasing)
            } else {
                tween(durationMillis = 0)
            }
        },
        label = "dot_scale"
    ) { state ->
        if (state == SplashPhase.Entering) 0.28f else 1f
    }
    val glowIn by transition.animateFloat(
        transitionSpec = {
            if (initialState == SplashPhase.Entering && targetState == SplashPhase.Waiting) {
                tween(durationMillis = 420, easing = LinearOutSlowInEasing)
            } else {
                tween(durationMillis = 0)
            }
        },
        label = "glow_in"
    ) { state ->
        if (state == SplashPhase.Entering) 0f else 1f
    }

    LaunchedEffect(phase, overlayAlpha) {
        if (phase != SplashPhase.Exiting) return@LaunchedEffect
        if (overlayAlpha > 0f) return@LaunchedEffect
        if (!finishedCalled) {
            finishedCalled = true
            onFinished()
        }
    }

    val bg = Color(0xFF07070B)
    val waitBreath = if (phase == SplashPhase.Waiting && !isReady) breathe else 0f
    val breathScale = 1f + 0.03f * waitBreath
    val glowAlpha = glowIn * (0.28f + 0.18f * waitBreath)
    val glowBrush = remember { Brush.radialGradient(colors = listOf(Color.White, Color.Transparent)) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bg)
            .graphicsLayer(alpha = overlayAlpha, compositingStrategy = CompositingStrategy.ModulateAlpha)
            .pointerInteropFilter { true },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(320.dp)
                .graphicsLayer(alpha = glowAlpha * 0.7f * 0.22f)
                .background(glowBrush, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(220.dp)
                .graphicsLayer(alpha = glowAlpha * 0.18f)
                .background(glowBrush, CircleShape)
        )

        Box(
            modifier = Modifier
                .size(90.dp)
                .graphicsLayer(alpha = dotAlpha * 0.55f, scaleX = dotScale, scaleY = dotScale)
                .background(Color.White, CircleShape)
                .blur(30.dp)
        )

        Icon(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .size(96.dp)
                .graphicsLayer(
                    alpha = logoAlpha,
                    scaleX = logoBaseScale * breathScale,
                    scaleY = logoBaseScale * breathScale
                )
        )
    }
}
