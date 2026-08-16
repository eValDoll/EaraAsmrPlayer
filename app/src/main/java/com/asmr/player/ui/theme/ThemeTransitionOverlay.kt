package com.asmr.player.ui.theme

import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.Window
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.math.sqrt
import kotlin.coroutines.resume

data class ThemeTransitionRequest(
    val origin: Offset,
    val oldContentBitmap: ImageBitmap?,
    val oldBackgroundColor: Color,
    val targetIsDark: Boolean,
    val token: Long
)

data class ThemeTransitionTriggerRequest(
    val origin: Offset,
    val targetPref: String
)

val LocalThemeTransitionTrigger = staticCompositionLocalOf<((ThemeTransitionTriggerRequest) -> Unit)?> { null }

@Composable
fun ThemeCircularRevealOverlay(
    request: ThemeTransitionRequest,
    targetReady: Boolean,
    onAnimationEnd: () -> Unit
) {
    val animationProgress = remember { Animatable(0f) }
    var overlaySize by remember { mutableStateOf(Size.Zero) }

    LaunchedEffect(request, targetReady) {
        animationProgress.snapTo(0f)
        if (!targetReady) return@LaunchedEffect
        // 等新主题完成一次组合与绘制后再揭开旧画面，避免底层主题尚未切换时
        // 动画先跑出一小段，随后整块 GPU 图层突然换色。
        withFrameNanos { }
        animationProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing)
        )
        onAnimationEnd()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { overlaySize = Size(it.width.toFloat(), it.height.toFloat()) }
        ) {
            if (overlaySize == Size.Zero) return@Canvas

            val maxRadius = sqrt(
                overlaySize.width * overlaySize.width + overlaySize.height * overlaySize.height
            )
            val radius = maxRadius * animationProgress.value

            val rectPath = Path().apply {
                addRect(androidx.compose.ui.geometry.Rect(0f, 0f, overlaySize.width, overlaySize.height))
            }
            val circlePath = Path().apply {
                addOval(
                    androidx.compose.ui.geometry.Rect(
                        request.origin.x - radius,
                        request.origin.y - radius,
                        request.origin.x + radius,
                        request.origin.y + radius
                    )
                )
            }

            val outsidePath = Path().apply {
                op(rectPath, circlePath, PathOperation.Difference)
            }

            clipPath(outsidePath) {
                val bitmap = request.oldContentBitmap
                if (bitmap != null && bitmap.width > 0 && bitmap.height > 0) {
                    drawImage(
                        image = bitmap,
                        dstOffset = androidx.compose.ui.unit.IntOffset.Zero,
                        dstSize = androidx.compose.ui.unit.IntSize(
                            size.width.toInt(),
                            size.height.toInt()
                        )
                    )
                } else {
                    drawRect(request.oldBackgroundColor)
                }
            }
        }
    }
}

/**
 * 捕获窗口最终合成后的画面，确保 Compose graphicsLayer、离屏混合和阴影也进入主题过渡快照。
 * Android 8 以下或 PixelCopy 失败时退回 View.draw，主题切换仍可继续完成。
 */
internal suspend fun captureThemeTransitionBitmap(window: Window): ImageBitmap? {
    val decorView = window.decorView
    val width = decorView.width
    val height = decorView.height
    if (width <= 0 || height <= 0) return null

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val result = try {
            requestWindowPixelCopy(window, bitmap)
        } catch (_: Exception) {
            PixelCopy.ERROR_UNKNOWN
        }
        if (result == PixelCopy.SUCCESS) {
            return bitmap.asImageBitmap()
        }
        bitmap.recycle()
    }

    return runCatching {
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            decorView.draw(android.graphics.Canvas(bitmap))
        }.asImageBitmap()
    }.getOrNull()
}

@RequiresApi(Build.VERSION_CODES.O)
private suspend fun requestWindowPixelCopy(window: Window, bitmap: Bitmap): Int {
    return suspendCancellableCoroutine { continuation ->
        PixelCopy.request(
            window,
            bitmap,
            { result ->
                if (continuation.isActive) continuation.resume(result)
            },
            Handler(Looper.getMainLooper())
        )
    }
}
