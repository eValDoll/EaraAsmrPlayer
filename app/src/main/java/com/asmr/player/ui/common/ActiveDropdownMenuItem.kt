package com.asmr.player.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ActiveDropdownMenuItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.onSurface
) {
    DropdownMenuItem(
        text = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (selected) activeColor else inactiveColor,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier
                        .padding(horizontal = 4.dp, vertical = 3.dp)
                        .activeWavyUnderline(selected, activeColor)
                )
            }
        },
        onClick = onClick,
        colors = MenuDefaults.itemColors(
            textColor = if (selected) activeColor else inactiveColor
        ),
        modifier = modifier
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .semantics {
                this.selected = selected
                stateDescription = if (selected) "当前选项" else "可选项"
            }
    )
}

private fun Modifier.activeWavyUnderline(active: Boolean, color: Color): Modifier {
    if (!active) return this
    return drawBehind {
        val strokeWidth = 2.5.dp.toPx()
        val amplitude = 1.8.dp.toPx()
        val wavelength = 7.dp.toPx()
        val baselineY = size.height - strokeWidth / 2f
        val path = Path().apply { moveTo(0f, baselineY) }
        var x = 0f
        while (x < size.width) {
            path.relativeQuadraticTo(
                wavelength / 4f,
                -amplitude,
                wavelength / 2f,
                0f
            )
            path.relativeQuadraticTo(
                wavelength / 4f,
                amplitude,
                wavelength / 2f,
                0f
            )
            x += wavelength
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}
