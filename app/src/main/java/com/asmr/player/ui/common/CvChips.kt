package com.asmr.player.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.asmr.player.ui.theme.AsmrTheme

private fun parseCvNames(cvText: String): List<String> {
    return cvText
        .split(',', '，', '、', '/', '\n', ';', '；', '|')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
}

@Composable
fun CvChipsSingleLine(
    cvText: String,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
    onCvClick: ((String) -> Unit)? = null,
) {
    val cvs = remember(cvText) { parseCvNames(cvText) }
    if (cvs.isEmpty()) return

    Row(
        modifier = modifier
            .clipToBounds()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showLabel) {
            CvLabelChip()
        }
        cvs.forEach { cv ->
            CvValueChip(text = cv, onClick = { onCvClick?.invoke(cv) })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CvChipsFlow(
    cvText: String,
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(4.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(4.dp),
    showLabel: Boolean = true,
    onCvClick: ((String) -> Unit)? = null,
) {
    val cvs = remember(cvText) { parseCvNames(cvText) }
    if (cvs.isEmpty()) return

    FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement,
    ) {
        if (showLabel) {
            CvLabelChip()
        }
        cvs.forEach { cv ->
            CvValueChip(text = cv, onClick = { onCvClick?.invoke(cv) })
        }
    }
}

@Composable
private fun CvLabelChip() {
    val colorScheme = AsmrTheme.colorScheme
    Text(
        text = "CV",
        style = MaterialTheme.typography.labelSmall,
        color = colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clipToBounds()
            .background(colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

@Composable
private fun CvValueChip(
    text: String,
    onClick: (() -> Unit)? = null,
) {
    val colorScheme = AsmrTheme.colorScheme
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = colorScheme.primary.copy(alpha = 0.7f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .widthIn(max = 200.dp)
            .background(colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
    )
}
