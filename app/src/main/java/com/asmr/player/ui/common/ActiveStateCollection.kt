package com.asmr.player.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.StateFlow

/**
 * 离屏但仍保留组合的页面冻结状态订阅；重新可见时直接以 StateFlow 当前值恢复。
 */
@Composable
fun <T> StateFlow<T>.collectAsStateWhileActive(isActive: Boolean): State<T> {
    return if (isActive) {
        collectAsState()
    } else {
        remember(this) { mutableStateOf(value) }
    }
}
