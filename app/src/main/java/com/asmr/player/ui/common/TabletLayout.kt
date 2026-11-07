package com.asmr.player.ui.common

import android.os.Build
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class TabletLayout(
    val isTablet: Boolean = false,
    val width: Dp = 0.dp,
    val height: Dp = 0.dp,
    val embedded: Boolean = false,
    val fontScale: Float = 1f,
) {
    val landscape: Boolean get() = isTablet && width > height
    val twoPane: Boolean get() = landscape && width >= 720.dp * fontScale.coerceAtLeast(1f) && height >= 400.dp && !embedded
    val horizontalPadding: Dp get() = if (!isTablet) 0.dp else if (width >= 1200.dp) 24.dp else 16.dp
    val masterWidth: Dp get() = (width * 0.27f).coerceIn(240.dp, 300.dp)
}

val LocalTabletLayout = staticCompositionLocalOf { TabletLayout() }

@Composable
fun rememberIsTabletDevice(): Boolean {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val activity = remember(context) { context.findActivity() }
    return remember(configuration.smallestScreenWidthDp, configuration.screenWidthDp, configuration.screenHeightDp, density, activity) {
        configuration.smallestScreenWidthDp >= 600 ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && activity != null &&
                activity.windowManager.maximumWindowMetrics.bounds.let { bounds ->
                    minOf(bounds.width(), bounds.height()) / density >= 600f
                })
    }
}

/** 让每个页面按自己的实际约束布局，避免导航栏、分屏和嵌入详情重复占位。 */
@Composable
fun TabletPage(
    modifier: Modifier = Modifier,
    embedded: Boolean = LocalTabletLayout.current.embedded,
    content: @Composable BoxScope.() -> Unit,
) {
    val parent = LocalTabletLayout.current
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier.fillMaxSize()) {
        val layout = remember(parent.isTablet, maxWidth, maxHeight, embedded, fontScale) {
            TabletLayout(parent.isTablet, maxWidth, maxHeight, embedded, fontScale)
        }
        CompositionLocalProvider(LocalTabletLayout provides layout) { content() }
    }
}
