package com.asmr.player.ui.common.reorderable

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset

interface DragCancelledAnimation {
    suspend fun dragCancelled(position: ItemPosition, offset: Offset)
    val position: ItemPosition?
    val offset: Offset
}

class NoDragCancelledAnimation : DragCancelledAnimation {
    override suspend fun dragCancelled(position: ItemPosition, offset: Offset) = Unit
    override val position: ItemPosition? = null
    override val offset: Offset = Offset.Zero
}

class SpringDragCancelledAnimation(
    private val stiffness: Float = Spring.StiffnessMediumLow
) : DragCancelledAnimation {
    private val animatable = Animatable(Offset.Zero, OffsetVectorConverter)

    override val offset: Offset
        get() = animatable.value

    override var position by mutableStateOf<ItemPosition?>(null)
        private set

    override suspend fun dragCancelled(position: ItemPosition, offset: Offset) {
        this.position = position
        animatable.snapTo(offset)
        animatable.animateTo(
            targetValue = Offset.Zero,
            animationSpec = spring(
                stiffness = stiffness,
                visibilityThreshold = Offset(1f, 1f)
            )
        )
        this.position = null
    }
}

private val OffsetVectorConverter = TwoWayConverter<Offset, AnimationVector2D>(
    convertToVector = { offset -> AnimationVector2D(offset.x, offset.y) },
    convertFromVector = { vector -> Offset(vector.v1, vector.v2) }
)
