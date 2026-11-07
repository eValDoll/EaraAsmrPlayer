package com.asmr.player.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.asmr.player.R
import com.asmr.player.ui.common.EaraBrand
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.asmr.player.ui.common.StableWindowInsets
import com.asmr.player.ui.common.resolveMainPageBackgroundColor
import com.asmr.player.ui.theme.AsmrTheme

val TabletNavigationRailWidth = 88.dp

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun TabletNavigationRail(
    activeRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AsmrTheme.colorScheme
    val navItems = remember { bottomChromeNavItems() }
    Column(
        modifier.semantics { testTagsAsResourceId = true }.width(TabletNavigationRailWidth).fillMaxHeight()
            .shadow(elevation = 6.dp, clip = false)
            .background(resolveMainPageBackgroundColor(colors))
            .drawBehind {
                if (colors.isDark) {
                    val edgeWidth = 1.dp.toPx()
                    drawRect(
                        color = colors.onSurface.copy(alpha = 0.18f),
                        topLeft = Offset(size.width - edgeWidth, 0f),
                        size = Size(edgeWidth, size.height),
                    )
                }
            }
            .windowInsetsPadding(StableWindowInsets.statusBars.union(StableWindowInsets.navigationBars).only(WindowInsetsSides.Vertical))
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EaraBrand(
            appName = stringResource(R.string.app_name),
            tint = colors.primaryStrong,
            stacked = true,
            modifier = Modifier.testTag("tabletBrand"),
        )
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth()
                .verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Bottom),
        ) {
            navItems.forEach { item ->
                RailItem(item.label, item.icon, activeRoute == item.route) { onNavigate(item.route) }
            }
        }
    }
}

@Composable
private fun RailItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val colors = AsmrTheme.colorScheme
    val tint = if (selected) colors.primaryStrong else colors.textSecondary
    Column(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .testTag("tabletNavigation:$label")
            .semantics { this.selected = selected }
            .clickable(role = Role.Tab, onClick = onClick)
            .drawBehind {
                if (selected) drawRoundRect(
                    tint, topLeft = Offset(size.width - 3.dp.toPx(), (size.height - 24.dp.toPx()) / 2),
                    size = Size(3.dp.toPx(), 24.dp.toPx()), cornerRadius = CornerRadius(1.5.dp.toPx()),
                )
            }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Text(label, color = tint,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 14.sp),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
