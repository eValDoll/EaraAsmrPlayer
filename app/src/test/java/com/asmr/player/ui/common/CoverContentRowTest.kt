package com.asmr.player.ui.common

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import com.asmr.player.ui.createEaraComposeRule
import com.asmr.player.ui.library.BalancedColumn
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CoverContentRowTest {
    @get:Rule
    val composeRule = createEaraComposeRule()

    @Test
    fun filledContent_usesCoverHeightWithAnUnboundedParent() {
        checkLayout(fill = true, itemMin = 80, parentMin = 0, parentMax = Constraints.Infinity,
            children = listOf(20), rowHeight = 100, contentHeight = 100, coverY = 0, contentY = 0)
    }

    @Test
    fun filledContent_honorsTheItemMinimumWithoutASecondMeasurement() {
        checkLayout(fill = true, itemMin = 140, parentMin = 0, parentMax = Constraints.Infinity,
            children = listOf(20), rowHeight = 140, contentHeight = 140, coverY = 20, contentY = 0)
    }

    @Test
    fun filledContent_honorsTheParentMinimumWithoutASecondMeasurement() {
        checkLayout(fill = true, itemMin = 140, parentMin = 160, parentMax = Constraints.Infinity,
            children = listOf(20), rowHeight = 160, contentHeight = 160, coverY = 30, contentY = 0)
    }

    @Test
    fun filledContent_clampsBelowTheCoverHeightAndMeasuresEachChildOnce() {
        checkLayout(fill = true, itemMin = 80, parentMin = 0, parentMax = 60,
            children = listOf(20), rowHeight = 60, contentHeight = 60, coverY = 0, contentY = 0)
    }

    @Test
    fun filledContent_keepsNaturallyTallerMetadataAndCentersTheCover() {
        checkLayout(fill = true, itemMin = 80, parentMin = 0, parentMax = Constraints.Infinity,
            children = listOf(80, 80), rowHeight = 160, contentHeight = 160, coverY = 30, contentY = 0)
    }

    @Test
    fun unfilledContent_centersInsideTheParentMinimumWithoutStretching() {
        checkLayout(fill = false, itemMin = 140, parentMin = 160, parentMax = Constraints.Infinity,
            children = listOf(20), rowHeight = 160, contentHeight = 20, coverY = 30, contentY = 70)
    }

    @Test
    fun unfilledContent_keepsTheOriginalItemMinimumAndCentering() {
        checkLayout(fill = false, itemMin = 140, parentMin = 0, parentMax = Constraints.Infinity,
            children = listOf(20), rowHeight = 140, contentHeight = 20, coverY = 20, contentY = 60)
    }

    @Test
    fun unfilledContent_respectsTheExistingMaximumWithoutMeasuringAgain() {
        checkLayout(fill = false, itemMin = 80, parentMin = 0, parentMax = 60,
            children = listOf(20), rowHeight = 60, contentHeight = 60, coverY = 0, contentY = 0)
    }

    private fun checkLayout(
        fill: Boolean,
        itemMin: Int,
        parentMin: Int,
        parentMax: Int,
        children: List<Int>,
        rowHeight: Int,
        contentHeight: Int,
        coverY: Int,
        contentY: Int,
    ) {
        val row = Measurement()
        val cover = Measurement()
        val content = Measurement()
        val childMeasureCounts = IntArray(children.size)
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Layout(content = {
                    CoverContentRow(
                        coverWidth = 100.dp,
                        minHeight = itemMin.dp,
                        spacing = 10.dp,
                        fillContentHeight = fill,
                        modifier = row.observe(),
                        cover = {
                            Layout(content = {}, modifier = cover.observe()) { _, constraints ->
                                layout(constraints.minWidth, constraints.minHeight) {}
                            }
                        },
                        content = {
                            // Exercise the same metadata layout used by AlbumItem, including
                            // its finite-height fill and unbounded-height wrap behavior.
                            BalancedColumn(modifier = content.observe()) {
                                children.forEachIndexed { index, height ->
                                    Layout(content = {}) { _, constraints ->
                                        childMeasureCounts[index]++
                                        layout(constraints.constrainWidth(1), constraints.constrainHeight(height)) {}
                                    }
                                }
                            }
                        },
                    )
                }, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
                    val placeable = measurables.single().measure(Constraints(
                        minWidth = 320, maxWidth = 320, minHeight = parentMin, maxHeight = parentMax,
                    ))
                    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, 0) }
                }
            }
        }
        composeRule.runOnIdle {
            assertEquals(IntSize(320, rowHeight), row.size)
            assertEquals(IntSize(100, 100), cover.size)
            assertEquals(IntSize(210, contentHeight), content.size)
            assertEquals(Offset(0f, coverY.toFloat()), cover.position - row.position)
            assertEquals(Offset(110f, contentY.toFloat()), content.position - row.position)
            assertEquals("The row must have one actual measure pass", 1, row.count)
            assertEquals("The cover must be measured once", 1, cover.count)
            assertEquals("Metadata must be measured once", 1, content.count)
            childMeasureCounts.forEach { assertEquals("Each metadata child must be measured once", 1, it) }
            assertEquals(parentMax, content.constraints.maxHeight)
            assertEquals(if (fill) contentHeight.coerceAtMost(maxOf(itemMin, 100, parentMin)) else 0,
                content.constraints.minHeight)
        }
    }

    private class Measurement {
        var count = 0
        var constraints = Constraints()
        var size = IntSize.Zero
        var position = Offset.Zero

        fun observe(): Modifier = Modifier.layout { measurable, constraints ->
            count++
            this@Measurement.constraints = constraints
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }.onGloballyPositioned {
            size = it.size
            position = it.positionInRoot()
        }
    }
}
