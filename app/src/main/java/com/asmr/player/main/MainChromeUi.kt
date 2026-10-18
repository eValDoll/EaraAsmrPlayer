package com.asmr.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.asmr.player.ui.common.AsmrOneSiteSelector
import com.asmr.player.ui.common.SiteStatusTestRow
import com.asmr.player.ui.drawer.DrawerStatusViewModel

import androidx.compose.ui.text.font.FontWeight
import com.asmr.player.ui.theme.AsmrTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.animation.*


import com.asmr.player.ui.common.AppVolumeHearingWarningDialog
import com.asmr.player.ui.common.AppVolumeWarningSessionState
import com.asmr.player.ui.common.rememberProtectedAppVolumeChangeState
import com.asmr.player.ui.common.AudioOutputRouteIcon
import com.asmr.player.service.AudioOutputRouteKind

import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.asmr.player.playback.AppVolume
import com.asmr.player.ui.common.AppVolumeVerticalSlider

@Composable
internal fun HardwareVolumeOverlay(
    volumePercent: Int,
    audioOutputRouteKind: AudioOutputRouteKind,
    onVolumeChange: (Int) -> Unit,
    onToggleMute: () -> Unit,
    onInteractionActiveChanged: (Boolean) -> Unit,
    warningSessionState: AppVolumeWarningSessionState,
    modifier: Modifier = Modifier
) {
    val colorScheme = AsmrTheme.colorScheme
    val accentColor = colorScheme.primary
    val protectedVolumeChangeState = rememberProtectedAppVolumeChangeState(
        warningSessionState = warningSessionState,
        onApplyVolumeChange = onVolumeChange
    )

    Box(
        modifier = modifier
            .padding(12.dp)
            .graphicsLayer { clip = false }
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = colorScheme.surface.copy(alpha = if (colorScheme.isDark) 0.92f else 0.96f),
            contentColor = colorScheme.onSurface,
            shadowElevation = if (colorScheme.isDark) 0.dp else 10.dp,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .width(88.dp)
                    .padding(horizontal = 14.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AudioOutputRouteIcon(
                    routeKind = audioOutputRouteKind,
                    isMuted = volumePercent == 0,
                    tint = accentColor,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable(onClick = onToggleMute)
                )
                AppVolumeVerticalSlider(
                    valuePercent = volumePercent,
                    onValueChange = { nextPercent, source ->
                        protectedVolumeChangeState.requestVolumeChange(
                            currentPercent = volumePercent,
                            targetPercent = nextPercent,
                            source = source
                        )
                    },
                    accentColor = accentColor,
                    onInteractionActiveChanged = onInteractionActiveChanged
                )
                Text(
                    text = "${AppVolume.clampPercent(volumePercent)}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
    AppVolumeHearingWarningDialog(state = protectedVolumeChangeState)
}

@Stable
internal class PersistedBooleanState(
    initial: Boolean,
    private val save: (Boolean) -> Unit
) : MutableState<Boolean> {
    private var backing by mutableStateOf(initial)

    override var value: Boolean
        get() = backing
        set(value) {
            if (backing == value) return
            backing = value
            save(value)
        }

    override fun component1(): Boolean = value

    override fun component2(): (Boolean) -> Unit = { value = it }

    fun updateFromStore(value: Boolean) {
        backing = value
    }
}

@Composable
internal fun DrawerNavCardItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = AsmrTheme.colorScheme
    val isDark = colorScheme.isDark
    val unselectedColor = if (isDark) Color(0xFF1E1E1E) else Color(0xFFF3F4F6)
    val selectedColor = if (isDark) Color(0xFF2A2A2A) else Color.White
    val containerColor = if (selected) selectedColor else unselectedColor
    val selectedContentColor = colorScheme.primaryStrong
    val contentColor = if (selected) selectedContentColor else colorScheme.textPrimary
    val elevation = if (selected || isDark) 0.dp else 2.dp
    val shape = RoundedCornerShape(18.dp)

    ElevatedCard(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isDark) {
                    Modifier.border(
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                        shape = shape
                    )
                } else Modifier
            ),
        shape = shape,
        colors = CardDefaults.elevatedCardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = elevation)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val iconTint = if (selected) selectedContentColor else colorScheme.textSecondary
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(containerColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = iconTint
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
                maxLines = 1
            )
        }
    }
}

@Composable
internal fun DrawerSiteStatusFooter(
    viewModel: DrawerStatusViewModel,
    modifier: Modifier = Modifier
) {
    val dlsite by viewModel.dlsite.collectAsStateWithLifecycle()
    val asmr by viewModel.asmr.collectAsStateWithLifecycle()
    val site by viewModel.asmrOneSite.collectAsStateWithLifecycle()
    val japaneseAsmr by viewModel.japaneseAsmr.collectAsStateWithLifecycle()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SiteStatusTestRow(
            name = "dlsite.com",
            status = dlsite,
            onTest = { viewModel.testDlsite() }
        )

        SiteStatusTestRow(
            status = asmr,
            onTest = { viewModel.testAsmrOne() },
            nameContent = {
                AsmrOneSiteSelector(
                    selectedSite = site,
                    onSiteSelected = viewModel::setAsmrOneSite
                )
            }
        )
        SiteStatusTestRow(
            name = "Japanese ASMR",
            status = japaneseAsmr,
            onTest = viewModel::testJapaneseAsmr
        )
    }
}
