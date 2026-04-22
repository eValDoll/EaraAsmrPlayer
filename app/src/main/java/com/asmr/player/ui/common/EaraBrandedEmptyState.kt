package com.asmr.player.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                        .size(96.dp)
                        .background(
                            color = colorScheme.primary.copy(alpha = if (colorScheme.isDark) 0.12f else 0.08f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = sectionIcon,
                        contentDescription = null,
                        tint = colorScheme.primary.copy(alpha = if (colorScheme.isDark) 0.7f else 0.6f),
                        modifier = Modifier.size(48.dp)
                    )
                }

                Spacer(modifier = Modifier.size(24.dp))

                Text(
                    text = sectionTitle,
                    style = MaterialTheme.typography.labelLarge,
                    color = colorScheme.primary.copy(alpha = if (colorScheme.isDark) 0.8f else 0.7f)
                )

                Spacer(modifier = Modifier.size(12.dp))

                Text(
                    text = headline,
                    style = MaterialTheme.typography.titleLarge,
                    color = colorScheme.textPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.size(8.dp))

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.textSecondary,
                    textAlign = TextAlign.Center
                )

                if (footer != null) {
                    Spacer(modifier = Modifier.size(24.dp))
                    footer()
                }
            }
        }
    }
}
