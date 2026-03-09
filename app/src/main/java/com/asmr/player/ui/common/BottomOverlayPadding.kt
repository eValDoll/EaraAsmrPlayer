package com.asmr.player.ui.common

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

val LocalBottomOverlayPadding = staticCompositionLocalOf<Dp> { 0.dp }

fun PaddingValues.withAddedBottomPadding(extraBottom: Dp): PaddingValues = object : PaddingValues {
    override fun calculateLeftPadding(layoutDirection: LayoutDirection) =
        this@withAddedBottomPadding.calculateLeftPadding(layoutDirection)

    override fun calculateTopPadding() = this@withAddedBottomPadding.calculateTopPadding()

    override fun calculateRightPadding(layoutDirection: LayoutDirection) =
        this@withAddedBottomPadding.calculateRightPadding(layoutDirection)

    override fun calculateBottomPadding() =
        this@withAddedBottomPadding.calculateBottomPadding() + extraBottom
}
