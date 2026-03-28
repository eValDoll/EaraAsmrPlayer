package com.asmr.player.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.asmr.player.ui.theme.AsmrTheme

internal const val EARA_EMPTY_STATE_TAG = "earaEmptyState"

@Composable
fun EaraBrandedEmptyState(
    sectionTitle: String,
    headline: String,
    description: String,
    sectionIcon: ImageVector,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    footer: (@Composable () -> Unit)? = null
) {
    val colorScheme = AsmrTheme.colorScheme
    val panelShape = RoundedCornerShape(36.dp)
    val panelBorder = colorScheme.primary.copy(alpha = if (colorScheme.isDark) 0.26f else 0.14f)
    val panelGradient = Brush.linearGradient(
        colors = listOf(
            colorScheme.surface.copy(alpha = if (colorScheme.isDark) 0.98f else 0.94f),
            colorScheme.primarySoft.copy(alpha = if (colorScheme.isDark) 0.22f else 0.14f)
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(EARA_EMPTY_STATE_TAG),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 440.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(196.dp)
                        .clip(panelShape)
                        .background(panelGradient, panelShape)
                        .border(width = 1.dp, color = panelBorder, shape = panelShape)
                        .padding(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(
                                color = colorScheme.primary.copy(alpha = if (colorScheme.isDark) 0.10f else 0.08f)
                            )
                    )
                    Icon(
                        imageVector = sectionIcon,
                        contentDescription = null,
                        tint = colorScheme.primary.copy(alpha = if (colorScheme.isDark) 0.18f else 0.14f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(60.dp)
                    )
                    EaraLogoLoadingIndicator(
                        size = 92.dp,
                        tint = colorScheme.primary,
                        trackColor = colorScheme.primary.copy(alpha = if (colorScheme.isDark) 0.18f else 0.12f),
                        glowColor = colorScheme.primarySoft,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                Spacer(modifier = Modifier.size(20.dp))

                Surface(
                    color = colorScheme.primary.copy(alpha = if (colorScheme.isDark) 0.16f else 0.10f),
                    contentColor = colorScheme.primary,
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, panelBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = sectionIcon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = sectionTitle,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                Spacer(modifier = Modifier.size(18.dp))

                Text(
                    text = headline,
                    style = MaterialTheme.typography.titleLarge,
                    color = colorScheme.textPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.size(10.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.textSecondary,
                    textAlign = TextAlign.Center
                )
                if (footer != null) {
                    Spacer(modifier = Modifier.size(18.dp))
                    footer()
                }
            }
        }
    }
}
