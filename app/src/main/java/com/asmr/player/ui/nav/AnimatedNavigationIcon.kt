package com.asmr.player.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.asmr.player.ui.theme.AsmrTheme
import kotlin.math.PI
import kotlin.math.sin

/** Shared by the bottom navigation and tablet rail; progress follows reversible pager gestures. */
@Composable
internal fun AnimatedNavigationIcon(
    icon: ImageVector,
    route: String,
    label: String?,
    selectionProgress: () -> Float,
    modifier: Modifier = Modifier,
) {
    val colors = AsmrTheme.colorScheme
    val outline = when (route) {
        Routes.Library -> Icons.Outlined.Home
        Routes.Search -> Icons.Outlined.Search
        Routes.HotListening -> Icons.Outlined.Whatshot
        "playlist_system/favorites" -> Icons.Outlined.FavoriteBorder
        "playlists" -> Icons.AutoMirrored.Outlined.QueueMusic
        "groups" -> Icons.Outlined.Folder
        "listening_calendar" -> Icons.Outlined.Route
        "settings" -> Icons.Outlined.Settings
        else -> icon
    }
    Box(
        modifier = modifier
            .graphicsLayer {
                // Read animation state in the layer, without remeasuring or recomposing each frame.
                val progress = selectionProgress().coerceIn(0f, 1f)
                val pulse = sin(PI.toFloat() * progress).coerceAtLeast(0f)
                val emphasis = if (route == "playlist_system/favorites") 0.16f else 0.07f
                val scale = 1f + 0.16f * progress + emphasis * pulse
                scaleX = scale
                scaleY = scale
                rotationZ = when (route) {
                    "settings" -> 60f * progress
                    Routes.Search -> -14f * pulse
                    Routes.HotListening -> 8f * pulse
                    "playlists" -> -8f * pulse
                    else -> 0f
                }
                translationY = when (route) {
                    Routes.Library, "groups", "listening_calendar" -> -size.height * 0.08f * pulse
                    else -> 0f
                }
            }
            .semantics {
                if (label != null) {
                    contentDescription = label
                    role = Role.Image
                }
            },
    ) {
        Icon(
            imageVector = outline,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.matchParentSize().graphicsLayer {
                alpha = 1f - selectionProgress().coerceIn(0f, 1f)
            },
        )
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.primaryStrong,
            modifier = Modifier.matchParentSize().graphicsLayer {
                alpha = selectionProgress().coerceIn(0f, 1f)
            },
        )
    }
}
