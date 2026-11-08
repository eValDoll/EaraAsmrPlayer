package com.asmr.player.ui.common

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.collect

// 未启用的页面不增加图层；启用后的开关只由行图层读取。
internal val LocalAlbumRowRasterCacheAllowed = staticCompositionLocalOf<State<Boolean>?> { null }

@Stable
internal class AlbumRowRasterCacheRetention {
    var enabled by mutableStateOf(false)
        private set
    private var retainedPage by mutableStateOf<Int?>(null)

    fun onStart() {
        enabled = true
    }

    fun onStop() {
        enabled = false
        retainedPage = null
    }

    fun onTrimMemory() {
        enabled = false
        retainedPage = null
    }

    fun admit(eligiblePage: Int?) {
        if (enabled && eligiblePage != null) retainedPage = eligiblePage
    }

    fun targetPage(eligiblePage: Int?, transformIsIdentity: Boolean): Int? {
        if (!enabled || !transformIsIdentity) return null
        return eligiblePage ?: retainedPage
    }
}

@Composable
internal fun rememberAlbumRowRasterCacheTarget(
    eligiblePage: () -> Int?,
    transformIsIdentity: () -> Boolean,
): State<Int?> {
    val retention = remember { AlbumRowRasterCacheRetention() }
    val application = LocalContext.current.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentEligiblePage = rememberUpdatedState(eligiblePage)
    val currentTransformIsIdentity = rememberUpdatedState(transformIsIdentity)

    DisposableEffect(application, lifecycleOwner, retention) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> retention.onStart()
                Lifecycle.Event.ON_STOP -> retention.onStop()
                else -> Unit
            }
        }
        val callbacks = object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: Configuration) = Unit
            override fun onLowMemory() = retention.onTrimMemory()
            override fun onTrimMemory(level: Int) = retention.onTrimMemory()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        application.registerComponentCallbacks(callbacks)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            retention.onStart()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            application.unregisterComponentCallbacks(callbacks)
            retention.onStop()
        }
    }

    LaunchedEffect(retention) {
        snapshotFlow {
            if (retention.enabled && currentTransformIsIdentity.value()) {
                currentEligiblePage.value()
            } else null
        }.collect(retention::admit)
    }
    return remember(retention) {
        derivedStateOf(structuralEqualityPolicy()) {
            // 只选一个列表页；首次进入仍在原时机启用，离开到设置页时保留已有行层。
            retention.targetPage(currentEligiblePage.value(), currentTransformIsIdentity.value())
        }
    }
}

@Composable
internal fun rememberAlbumRowRasterCachePolicy(allowed: () -> Boolean): State<Boolean> {
    val currentRule = rememberUpdatedState(allowed)
    return remember { derivedStateOf(structuralEqualityPolicy()) { currentRule.value() } }
}

@Composable
internal fun AlbumRowRasterCacheScope(
    transformIsIdentity: () -> Boolean,
    content: @Composable () -> Unit,
) {
    val parentAllowed = LocalAlbumRowRasterCacheAllowed.current
    if (parentAllowed == null) {
        content()
        return
    }
    val allowed = rememberAlbumRowRasterCachePolicy {
        parentAllowed.value && transformIsIdentity()
    }
    CompositionLocalProvider(LocalAlbumRowRasterCacheAllowed provides allowed, content = content)
}
