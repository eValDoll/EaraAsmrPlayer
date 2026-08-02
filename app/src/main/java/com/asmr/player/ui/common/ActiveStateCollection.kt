package com.asmr.player.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 离屏但仍保留组合的页面冻结状态订阅；重新可见时直接以 StateFlow 当前值恢复。
 */
@Composable
fun <T> StateFlow<T>.collectAsStateWhileActive(isActive: Boolean): State<T> {
    // State 容器在 active 切换时保持同一实例，避免二级页面进出时替换组合分支、
    // 连带让整张底层页面重新组合。协程仍会在离屏时取消，不会继续收集或转换数据。
    val retained = remember(this) { mutableStateOf(value) }
    LaunchedEffect(this, isActive) {
        if (!isActive) return@LaunchedEffect
        retained.value = value
        collect { latest -> retained.value = latest }
    }
    return retained
}

/**
 * 稳定激活信号版本：调用方只更新同一个 State 的值，页面组合无需把 active Boolean
 * 作为参数重新传递。离屏切换只取消或启动收集协程。
 */
@Composable
fun <T> StateFlow<T>.collectAsStateWhileActive(isActive: State<Boolean>): State<T> {
    val retained = remember(this) { mutableStateOf(value) }
    LaunchedEffect(this, isActive) {
        snapshotFlow { isActive.value }
            .distinctUntilChanged()
            .collectLatest { active ->
                if (!active) return@collectLatest
                retained.value = value
                this@collectAsStateWhileActive.collect { latest ->
                    retained.value = latest
                }
            }
    }
    return retained
}
