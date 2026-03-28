package com.asmr.player.ui.nav

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.asmr.player.ui.player.MiniPlayer
import com.asmr.player.ui.player.MiniPlayerDisplayMode
import com.asmr.player.ui.theme.AsmrTheme
import kotlin.math.atan2
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

const val BottomNavBarTag = "bottomChromeNavBar"
const val BottomNavOverflowTag = "bottomChromeOverflow"

private val BottomChromeOverlayHeightCompact = 96.dp
private val BottomChromeOverlayHeightLarge = 108.dp
private val BottomNavBarHeight = 60.dp
private val BottomNavBarHeightLarge = 68.dp
private val BottomNavBarCornerRadius = 30.dp
private val BottomNavBarCornerRadiusLarge = 34.dp
private val BottomNavBorderWidth = 1.dp
private val BottomNavExpandedSlotCount = 5
private val BottomNavCollapsedWidth = 64.dp
private val BottomNavCollapsedWidthLarge = 76.dp
private val BottomNavChipSize = 44.dp
private val BottomNavChipSizeLarge = 50.dp
private val BottomNavItemSlotWidth = 48.dp
private val BottomNavItemSlotWidthLarge = 56.dp
private val BottomNavExpandedItemSpacing = 6.dp
private val BottomNavExpandedItemSpacingLarge = 8.dp
private val BottomNavExpandedHorizontalPadding = 8.dp
private val BottomNavExpandedHorizontalPaddingLarge = 10.dp
private val BottomNavOverflowPanelWidth = 56.dp
private val BottomNavOverflowPanelWidthLarge = 64.dp
private val BottomNavOverflowTopCapHeight = 20.dp
private val BottomNavOverflowTopCapHeightLarge = 22.dp
private val BottomNavOverflowShoulderLift = 8.dp
private val BottomNavOverflowShoulderLiftLarge = 10.dp
private val BottomNavOverflowNeckBottomOffset = 13.dp
private val BottomNavOverflowNeckBottomOffsetLarge = 15.dp
private val BottomNavOverflowLeftShoulderReach = 22.dp
private val BottomNavOverflowLeftShoulderReachLarge = 24.dp
private val BottomNavOverflowRightShoulderReach = 24.dp
private val BottomNavOverflowRightShoulderReachLarge = 26.dp
private val BottomNavOverflowHorizontalBias = 10.dp
private val BottomNavOverflowHorizontalBiasLarge = 12.dp
private val BottomNavOverflowTopContentPadding = 1.dp
private val BottomNavOverflowTopContentPaddingLarge = 2.dp
private val BottomNavOverflowBottomPadding = 12.dp
private val BottomNavOverflowBottomPaddingLarge = 14.dp
private val BottomNavOverflowItemSize = 40.dp
private val BottomNavOverflowItemSizeLarge = 46.dp
private val BottomNavOverflowItemSpacing = 10.dp
private val BottomNavOverflowItemSpacingLarge = 12.dp
private val BottomNavIconSize = 21.dp
private val BottomNavIconSizeLarge = 24.dp
private val BottomNavGlowExpandedSize = 44.dp
private val BottomNavGlowExpandedSizeLarge = 50.dp
private val BottomNavGlowCollapsedSize = 46.dp
private val BottomNavGlowCollapsedSizeLarge = 52.dp
private const val QuarterArcKappa = 0.55228475f
private const val BottomNavOverflowOutlineCollapseTailFraction = 0.18f

data class BottomChromeNavItem(
    val icon: ImageVector,
    val label: String,
    val route: String
)

data class BottomChromeNavLayout(
    val visibleItems: List<BottomChromeNavItem>,
    val overflowItems: List<BottomChromeNavItem>,
    val showsOverflow: Boolean
)

private data class BottomNavRailEntry(
    val item: BottomChromeNavItem,
    val width: Dp,
    val isOverflow: Boolean = false
)

private data class BottomChromeMetrics(
    val overlayHeight: Dp,
    val barHeight: Dp,
    val barCornerRadius: Dp,
    val collapsedWidth: Dp,
    val chipSize: Dp,
    val itemSlotWidth: Dp,
    val expandedItemSpacing: Dp,
    val expandedHorizontalPadding: Dp,
    val overflowPanelWidth: Dp,
    val overflowTopCapHeight: Dp,
    val overflowShoulderLift: Dp,
    val overflowNeckBottomOffset: Dp,
    val overflowLeftShoulderReach: Dp,
    val overflowRightShoulderReach: Dp,
    val overflowHorizontalBias: Dp,
    val overflowTopContentPadding: Dp,
    val overflowBottomPadding: Dp,
    val overflowItemSize: Dp,
    val overflowItemSpacing: Dp,
    val iconSize: Dp,
    val glowExpandedSize: Dp,
    val glowCollapsedSize: Dp
) {
    val preferredExpandedWidth: Dp
        get() = (expandedHorizontalPadding * 2) +
            (itemSlotWidth * BottomNavExpandedSlotCount) +
            (expandedItemSpacing * (BottomNavExpandedSlotCount - 1))
}

fun bottomChromeOverlayHeight(largeLayout: Boolean): Dp =
    if (largeLayout) BottomChromeOverlayHeightLarge else BottomChromeOverlayHeightCompact

private fun bottomChromeMetrics(largeLayout: Boolean): BottomChromeMetrics =
    if (largeLayout) {
        BottomChromeMetrics(
            overlayHeight = BottomChromeOverlayHeightLarge,
            barHeight = BottomNavBarHeightLarge,
            barCornerRadius = BottomNavBarCornerRadiusLarge,
            collapsedWidth = BottomNavCollapsedWidthLarge,
            chipSize = BottomNavChipSizeLarge,
            itemSlotWidth = BottomNavItemSlotWidthLarge,
            expandedItemSpacing = BottomNavExpandedItemSpacingLarge,
            expandedHorizontalPadding = BottomNavExpandedHorizontalPaddingLarge,
            overflowPanelWidth = BottomNavOverflowPanelWidthLarge,
            overflowTopCapHeight = BottomNavOverflowTopCapHeightLarge,
            overflowShoulderLift = BottomNavOverflowShoulderLiftLarge,
            overflowNeckBottomOffset = BottomNavOverflowNeckBottomOffsetLarge,
            overflowLeftShoulderReach = BottomNavOverflowLeftShoulderReachLarge,
            overflowRightShoulderReach = BottomNavOverflowRightShoulderReachLarge,
            overflowHorizontalBias = BottomNavOverflowHorizontalBiasLarge,
            overflowTopContentPadding = BottomNavOverflowTopContentPaddingLarge,
            overflowBottomPadding = BottomNavOverflowBottomPaddingLarge,
            overflowItemSize = BottomNavOverflowItemSizeLarge,
            overflowItemSpacing = BottomNavOverflowItemSpacingLarge,
            iconSize = BottomNavIconSizeLarge,
            glowExpandedSize = BottomNavGlowExpandedSizeLarge,
            glowCollapsedSize = BottomNavGlowCollapsedSizeLarge
        )
    } else {
        BottomChromeMetrics(
            overlayHeight = BottomChromeOverlayHeightCompact,
            barHeight = BottomNavBarHeight,
            barCornerRadius = BottomNavBarCornerRadius,
            collapsedWidth = BottomNavCollapsedWidth,
            chipSize = BottomNavChipSize,
            itemSlotWidth = BottomNavItemSlotWidth,
            expandedItemSpacing = BottomNavExpandedItemSpacing,
            expandedHorizontalPadding = BottomNavExpandedHorizontalPadding,
            overflowPanelWidth = BottomNavOverflowPanelWidth,
            overflowTopCapHeight = BottomNavOverflowTopCapHeight,
            overflowShoulderLift = BottomNavOverflowShoulderLift,
            overflowNeckBottomOffset = BottomNavOverflowNeckBottomOffset,
            overflowLeftShoulderReach = BottomNavOverflowLeftShoulderReach,
            overflowRightShoulderReach = BottomNavOverflowRightShoulderReach,
            overflowHorizontalBias = BottomNavOverflowHorizontalBias,
            overflowTopContentPadding = BottomNavOverflowTopContentPadding,
            overflowBottomPadding = BottomNavOverflowBottomPadding,
            overflowItemSize = BottomNavOverflowItemSize,
            overflowItemSpacing = BottomNavOverflowItemSpacing,
            iconSize = BottomNavIconSize,
            glowExpandedSize = BottomNavGlowExpandedSize,
            glowCollapsedSize = BottomNavGlowCollapsedSize
        )
    }

fun bottomChromeNavItems(): List<BottomChromeNavItem> = listOf(
    BottomChromeNavItem(Icons.Default.Home, "本地库", Routes.Library),
    BottomChromeNavItem(Icons.Default.Search, "在线搜索", Routes.Search),
    BottomChromeNavItem(Icons.Default.Favorite, "我的收藏", "playlist_system/favorites"),
    BottomChromeNavItem(Icons.AutoMirrored.Filled.QueueMusic, "我的列表", "playlists"),
    BottomChromeNavItem(Icons.Default.Folder, "我的分组", "groups"),
    BottomChromeNavItem(Icons.Default.Download, "下载管理", "downloads"),
    BottomChromeNavItem(Icons.Default.Settings, "设置", "settings"),
    BottomChromeNavItem(Icons.Default.Person, "DLsite 登录", "dlsite_login")
)

fun isPrimaryRoute(route: String?): Boolean {
    if (route.isNullOrBlank()) return false
    return route in setOf(
        Routes.Library,
        Routes.Search,
        "playlist_system/favorites",
        "playlists",
        "groups",
        "downloads",
        "settings",
        "dlsite_login"
    )
}

fun resolvePrimaryRoute(
    currentRoute: String?,
    lastPrimaryRoute: String?,
    playlistSystemType: String? = null
): String {
    return when {
        currentRoute == Routes.Library -> Routes.Library
        currentRoute == Routes.Search -> Routes.Search
        currentRoute == "playlists" -> "playlists"
        currentRoute == "groups" -> "groups"
        currentRoute == "downloads" -> "downloads"
        currentRoute == "settings" -> "settings"
        currentRoute == "dlsite_login" -> "dlsite_login"
        currentRoute == "playlist_system/{type}" && playlistSystemType == "favorites" -> "playlist_system/favorites"
        currentRoute == "playlist/{playlistId}/{playlistName}" -> "playlists"
        currentRoute == "group/{groupId}/{groupName}" -> "groups"
        currentRoute?.startsWith("group_picker") == true -> "groups"
        currentRoute == "library_filter" -> Routes.Library
        currentRoute?.startsWith("album_detail") == true -> lastPrimaryRoute ?: Routes.Library
        currentRoute?.startsWith("playlist_picker") == true -> lastPrimaryRoute ?: Routes.Library
        else -> lastPrimaryRoute ?: Routes.Library
    }
}

fun resolvePrimaryPagerRoutes(
    navItems: List<BottomChromeNavItem>,
    activeRoute: String,
    preferredPinnedRoute: String? = null
): List<String> {
    if (navItems.isEmpty()) return emptyList()

    val metrics = bottomChromeMetrics(largeLayout = false)
    val layout = computeVisibleNavItems(
        allItems = navItems,
        activeRoute = activeRoute,
        availableWidth = metrics.preferredExpandedWidth,
        metrics = metrics,
        preferredPinnedRoute = preferredPinnedRoute,
        maxVisibleItems = BottomNavExpandedSlotCount - 1
    )
    return (layout.visibleItems + layout.overflowItems)
        .map { it.route }
        .distinct()
}

private fun computeVisibleNavItems(
    allItems: List<BottomChromeNavItem>,
    activeRoute: String,
    availableWidth: Dp,
    metrics: BottomChromeMetrics = bottomChromeMetrics(largeLayout = false),
    preferredPinnedRoute: String? = null,
    maxVisibleItems: Int? = null
): BottomChromeNavLayout {
    if (allItems.isEmpty()) {
        return BottomChromeNavLayout(emptyList(), emptyList(), showsOverflow = false)
    }

    val slotWidth = metrics.itemSlotWidth.value
    val slotSpacing = metrics.expandedItemSpacing.value
    val horizontalPadding = (metrics.expandedHorizontalPadding * 2).value
    val width = availableWidth.value.coerceAtLeast(slotWidth + horizontalPadding)
    fun requiredWidth(slotCount: Int): Float {
        if (slotCount <= 0) return horizontalPadding
        return horizontalPadding +
            (slotWidth * slotCount) +
            (slotSpacing * (slotCount - 1))
    }

    val maxWithoutOverflow = (1..allItems.size)
        .lastOrNull { requiredWidth(it) <= width }
        ?.coerceAtLeast(1)
        ?: 1
    if (allItems.size <= maxWithoutOverflow) {
        return BottomChromeNavLayout(
            visibleItems = allItems,
            overflowItems = emptyList(),
            showsOverflow = false
        )
    }

    val maxVisible = ((1..allItems.size)
        .lastOrNull { requiredWidth(it) <= width }
        ?: 1) - 1
    
    val resolvedMaxVisible = maxVisible
        .coerceAtLeast(1)
        .coerceAtMost(allItems.size - 1)
        .let { computed -> maxVisibleItems?.let { computed.coerceAtMost(it) } ?: computed }
    val activeItem = allItems.firstOrNull { it.route == activeRoute } ?: allItems.first()
    val fixedVisibleCount = (resolvedMaxVisible - 1).coerceAtLeast(0)
    val fixedVisibleItems = allItems.take(fixedVisibleCount)
    val defaultPinnedItem = allItems.getOrNull(fixedVisibleCount) ?: activeItem
    val preferredPinnedItem = allItems.firstOrNull { it.route == preferredPinnedRoute }
        ?.takeIf { it !in fixedVisibleItems }
    val slotItem = preferredPinnedItem
        ?: activeItem.takeIf { it !in fixedVisibleItems }
        ?: defaultPinnedItem
    val visible = fixedVisibleItems
        .plus(slotItem)
        .take(resolvedMaxVisible)
        .distinct()
        .toMutableList()
    if (visible.size < resolvedMaxVisible) {
        allItems.forEach { item ->
            if (visible.size >= resolvedMaxVisible) return@forEach
            if (item !in visible) {
                visible += item
            }
        }
    }
    val overflow = allItems.filterNot { it in visible }

    return BottomChromeNavLayout(
        visibleItems = visible,
        overflowItems = overflow,
        showsOverflow = overflow.isNotEmpty()
    )
}

private fun computeOverflowHeadroom(
    itemCount: Int,
    metrics: BottomChromeMetrics
): Dp {
    if (itemCount <= 0) return 0.dp
    return metrics.overflowShoulderLift +
        metrics.overflowTopCapHeight +
        metrics.overflowTopContentPadding +
        metrics.overflowItemSize * itemCount +
        metrics.overflowItemSpacing * (itemCount - 1) +
        metrics.overflowBottomPadding
}

private fun computeOverflowPanelLeftPx(
    surfaceWidth: Float,
    requestedCenterX: Float,
    panelWidth: Float
): Float {
    if (!surfaceWidth.isFinite() || !requestedCenterX.isFinite()) return Float.NaN
    return (requestedCenterX - (panelWidth / 2f))
        .coerceIn(4f, surfaceWidth - panelWidth - 4f)
}

private fun computeOverflowPanelCenterPx(
    surfaceWidth: Float,
    requestedCenterX: Float,
    panelWidth: Float
): Float {
    val panelLeft = computeOverflowPanelLeftPx(
        surfaceWidth = surfaceWidth,
        requestedCenterX = requestedCenterX,
        panelWidth = panelWidth
    )
    if (!panelLeft.isFinite()) return Float.NaN
    return panelLeft + (panelWidth / 2f)
}

private fun computeOverflowOutlineProgress(
    canShowOverflow: Boolean,
    overflowExpanded: Boolean,
    overflowRevealProgress: Float
): Float {
    if (!canShowOverflow) return 0f
    if (overflowExpanded) return 1f
    if (overflowRevealProgress <= 0f) return 0f
    if (overflowRevealProgress >= BottomNavOverflowOutlineCollapseTailFraction) return 1f

    val normalizedProgress =
        (overflowRevealProgress / BottomNavOverflowOutlineCollapseTailFraction).coerceIn(0f, 1f)
    return normalizedProgress * normalizedProgress * (3f - (2f * normalizedProgress))
}

private fun buildBottomNavRailEntries(
    layout: BottomChromeNavLayout,
    metrics: BottomChromeMetrics
): List<BottomNavRailEntry> {
    if (layout.visibleItems.isEmpty()) return emptyList()

    val entries = layout.visibleItems
        .map { item ->
            BottomNavRailEntry(
                item = item,
                width = metrics.itemSlotWidth
            )
        }
        .toMutableList()

    if (layout.showsOverflow) {
        entries += BottomNavRailEntry(
            item = BottomChromeNavItem(Icons.Default.MoreHoriz, "...", BottomNavOverflowTag),
            width = metrics.itemSlotWidth,
            isOverflow = true
        )
    }

    return entries
}

private fun computeBottomNavRailOffsets(
    entries: List<BottomNavRailEntry>,
    metrics: BottomChromeMetrics
): List<Dp> {
    if (entries.isEmpty()) return emptyList()

    val offsets = ArrayList<Dp>(entries.size)
    var currentOffset = metrics.expandedHorizontalPadding
    entries.forEachIndexed { index, entry ->
        offsets += currentOffset
        currentOffset += entry.width
        if (index != entries.lastIndex) {
            currentOffset += metrics.expandedItemSpacing
        }
    }
    return offsets
}

private fun computeBottomNavRailShift(
    entries: List<BottomNavRailEntry>,
    offsets: List<Dp>,
    activeRoute: String,
    currentWidth: Dp,
    metrics: BottomChromeMetrics
): Dp {
    val activeIndex = entries.indexOfFirst { !it.isOverflow && it.item.route == activeRoute }
        .takeIf { it >= 0 }
        ?: return 0.dp
    val activeLeft = offsets.getOrNull(activeIndex) ?: return 0.dp
    val activeWidth = entries[activeIndex].width
    val activeRight = activeLeft + activeWidth
    val collapsedActiveLeft = ((metrics.collapsedWidth - activeWidth) / 2f).coerceAtLeast(0.dp)
    val collapsedShift = activeLeft - collapsedActiveLeft
    if (collapsedShift == 0.dp) return 0.dp

    val moveStartWidth = maxOf(
        (activeRight + metrics.expandedHorizontalPadding).value,
        metrics.collapsedWidth.value + abs((collapsedActiveLeft - activeLeft).value)
    )
    val moveRange = (moveStartWidth - metrics.collapsedWidth.value).coerceAtLeast(1f)
    val progress = ((moveStartWidth - currentWidth.value) / moveRange).coerceIn(0f, 1f)
    return collapsedShift * progress
}

private fun computeBottomNavEntryVisibility(
    entryLeft: Dp,
    entryWidth: Dp,
    currentWidth: Dp,
    keepVisible: Boolean
): Float {
    if (keepVisible) return 1f

    val visibleLeft = maxOf(entryLeft.value, 0f)
    val visibleRight = minOf((entryLeft + entryWidth).value, currentWidth.value)
    val visibleWidth = (visibleRight - visibleLeft).coerceAtLeast(0f)
    val fadeThreshold = minOf(entryWidth.value * 0.38f, 14f)
    return ((visibleWidth - fadeThreshold) / (entryWidth.value - fadeThreshold))
        .coerceIn(0f, 1f)
}

@Composable
fun BottomChrome(
    activeRoute: String,
    selectionProgresses: Map<String, Float> = mapOf(activeRoute to 1f),
    preferredPinnedRoute: String? = null,
    miniPlayerVisible: Boolean,
    miniPlayerDisplayMode: MiniPlayerDisplayMode,
    onMiniPlayerDisplayModeChange: (MiniPlayerDisplayMode) -> Unit,
    onOpenNowPlaying: () -> Unit,
    onOpenQueue: () -> Unit,
    onNavigate: (String) -> Unit,
    largeLayout: Boolean = false,
    modifier: Modifier = Modifier,
    navItems: List<BottomChromeNavItem> = bottomChromeNavItems(),
    overflowExpanded: Boolean = false,
    onOverflowExpandedChange: (Boolean) -> Unit = {},
    onOverflowProtectedBoundsChange: (List<Rect>) -> Unit = {}
) {
    BoxWithConstraints(modifier = modifier) {
        val metrics = remember(largeLayout) { bottomChromeMetrics(largeLayout) }
        val navExpanded = !miniPlayerVisible || miniPlayerDisplayMode == MiniPlayerDisplayMode.CoverOnly
        val miniCollapsedWidth = if (largeLayout) 76.dp else 64.dp
        val expandedNavWidthLimit = maxWidth.coerceAtMost(metrics.preferredExpandedWidth)
        val miniWidthTarget = when {
            !miniPlayerVisible -> 0.dp
            miniPlayerDisplayMode == MiniPlayerDisplayMode.Expanded ->
                (maxWidth - metrics.collapsedWidth - 8.dp).coerceAtLeast(if (largeLayout) 244.dp else 204.dp)
            else -> miniCollapsedWidth
        }
        val navWidthTarget = when {
            !miniPlayerVisible -> expandedNavWidthLimit
            navExpanded -> (maxWidth - miniWidthTarget - 8.dp)
                .coerceAtLeast(if (largeLayout) 108.dp else 92.dp)
                .coerceAtMost(expandedNavWidthLimit)
            else -> metrics.collapsedWidth
        }
        val miniWidth by animateDpAsState(
            targetValue = miniWidthTarget,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "bottomChromeMiniWidth"
        )
        val navWidth by animateDpAsState(
            targetValue = navWidthTarget,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "bottomChromeNavWidth"
        )
        val maxVisibleItems = when {
            !navExpanded -> 1
            else -> BottomNavExpandedSlotCount - 1
        }
        val chromeArrangement = Arrangement.spacedBy(
            if (largeLayout) 10.dp else 8.dp,
            alignment = Alignment.CenterHorizontally
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { clip = false },
            horizontalArrangement = chromeArrangement,
            verticalAlignment = Alignment.Bottom
        ) {
            BottomNavigationPill(
                navItems = navItems,
                activeRoute = activeRoute,
                selectionProgresses = selectionProgresses,
                preferredPinnedRoute = preferredPinnedRoute,
                expanded = navExpanded,
                availableWidth = navWidthTarget,
                currentWidth = navWidth,
                metrics = metrics,
                maxVisibleItems = maxVisibleItems,
                overflowExpanded = overflowExpanded,
                onOverflowExpandedChange = onOverflowExpandedChange,
                onExpandRequest = { onMiniPlayerDisplayModeChange(MiniPlayerDisplayMode.CoverOnly) },
                onNavigate = onNavigate,
                onOverflowProtectedBoundsChange = onOverflowProtectedBoundsChange,
                modifier = Modifier.width(navWidth)
            )

            if (miniPlayerVisible) {
                MiniPlayer(
                    displayMode = miniPlayerDisplayMode,
                    onDisplayModeChange = onMiniPlayerDisplayModeChange,
                    onOpenNowPlaying = onOpenNowPlaying,
                    onOpenQueue = onOpenQueue,
                    largeLayout = largeLayout,
                    modifier = Modifier.width(miniWidth.coerceAtLeast(if (largeLayout) 84.dp else 72.dp))
                )
            }
        }
    }
}

@Composable
private fun BottomNavigationPill(
    navItems: List<BottomChromeNavItem>,
    activeRoute: String,
    selectionProgresses: Map<String, Float>,
    preferredPinnedRoute: String? = null,
    expanded: Boolean,
    availableWidth: Dp,
    currentWidth: Dp = availableWidth,
    metrics: BottomChromeMetrics = bottomChromeMetrics(largeLayout = false),
    maxVisibleItems: Int? = null,
    overflowExpanded: Boolean = false,
    onOverflowExpandedChange: (Boolean) -> Unit = {},
    onExpandRequest: () -> Unit = {},
    onNavigate: (String) -> Unit,
    onOverflowProtectedBoundsChange: (List<Rect>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    LaunchedEffect(expanded) {
        if (!expanded && overflowExpanded) {
            onOverflowExpandedChange(false)
        }
    }

    BottomNavigationPillSurface(
        navItems = navItems,
        activeRoute = activeRoute,
        selectionProgresses = selectionProgresses,
        preferredPinnedRoute = preferredPinnedRoute,
        expanded = expanded,
        availableWidth = availableWidth,
        currentWidth = currentWidth,
        metrics = metrics,
        maxVisibleItems = maxVisibleItems,
        overflowExpanded = overflowExpanded,
        onOverflowExpandedChange = onOverflowExpandedChange,
        onExpandRequest = onExpandRequest,
        onNavigate = onNavigate,
        onOverflowProtectedBoundsChange = onOverflowProtectedBoundsChange,
        modifier = modifier
    )
}

@Composable
private fun BottomNavigationPillSurface(
    navItems: List<BottomChromeNavItem>,
    activeRoute: String,
    selectionProgresses: Map<String, Float>,
    preferredPinnedRoute: String?,
    expanded: Boolean,
    availableWidth: Dp,
    currentWidth: Dp,
    metrics: BottomChromeMetrics,
    maxVisibleItems: Int? = null,
    overflowExpanded: Boolean = false,
    onOverflowExpandedChange: (Boolean) -> Unit = {},
    onExpandRequest: () -> Unit,
    onNavigate: (String) -> Unit,
    onOverflowProtectedBoundsChange: (List<Rect>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colorScheme = AsmrTheme.colorScheme
    val density = LocalDensity.current
    val resolvedSelectionProgresses = remember(activeRoute, selectionProgresses) {
        if (selectionProgresses.isEmpty()) {
            mapOf(activeRoute to 1f)
        } else {
            selectionProgresses.mapValues { (_, value) -> value.coerceIn(0f, 1f) }
        }
    }
    val layoutFocusRoute = remember(expanded, activeRoute, resolvedSelectionProgresses) {
        if (expanded) {
            activeRoute
        } else {
            resolvedSelectionProgresses
                .maxByOrNull { (_, progress) -> progress }
                ?.takeIf { it.value > 0.001f }
                ?.key
                ?: activeRoute
        }
    }
    val layoutPreferredPinnedRoute = if (expanded) preferredPinnedRoute else layoutFocusRoute
    val containerColor = colorScheme.primarySoft
        .copy(alpha = if (colorScheme.isDark) 0.10f else 0.14f)
        .compositeOver(colorScheme.surface)
    val borderColor = if (colorScheme.isDark) {
        Color.White.copy(alpha = 0.14f)
    } else {
        colorScheme.primaryStrong.copy(alpha = 0.14f)
    }
    val activeItem = navItems.firstOrNull { it.route == layoutFocusRoute } ?: navItems.firstOrNull()
    var lastExpandedWidth by remember(navItems, metrics) {
        mutableStateOf(metrics.preferredExpandedWidth)
    }
    var lastExpandedMaxVisibleItems by remember(navItems) {
        mutableStateOf(BottomNavExpandedSlotCount - 1)
    }
    if (expanded) {
        SideEffect {
            lastExpandedWidth = availableWidth
            lastExpandedMaxVisibleItems = maxVisibleItems ?: (BottomNavExpandedSlotCount - 1)
        }
    }
    val expandedReferenceWidth = if (expanded) availableWidth else lastExpandedWidth
    val expandedReferenceMaxVisibleItems = if (expanded) maxVisibleItems else lastExpandedMaxVisibleItems
    val expandedLayout = remember(
        navItems,
        layoutFocusRoute,
        layoutPreferredPinnedRoute,
        expandedReferenceWidth,
        expandedReferenceMaxVisibleItems
    ) {
        computeVisibleNavItems(
            allItems = navItems,
            activeRoute = layoutFocusRoute,
            availableWidth = expandedReferenceWidth,
            metrics = metrics,
            preferredPinnedRoute = layoutPreferredPinnedRoute,
            maxVisibleItems = expandedReferenceMaxVisibleItems
        )
    }
    val motionLayout = expandedLayout.takeIf { it.visibleItems.isNotEmpty() }
        ?: BottomChromeNavLayout(
            visibleItems = listOfNotNull(activeItem),
            overflowItems = emptyList(),
            showsOverflow = false
        )
    val overflowSelectionProgress = expandedLayout.overflowItems
        .maxOfOrNull { item -> resolvedSelectionProgresses[item.route] ?: 0f }
        ?: 0f
    val motionEntries = buildBottomNavRailEntries(motionLayout, metrics)
    val motionOffsets = computeBottomNavRailOffsets(motionEntries, metrics)
    val railShift = computeBottomNavRailShift(
        entries = motionEntries,
        offsets = motionOffsets,
        activeRoute = layoutFocusRoute,
        currentWidth = currentWidth,
        metrics = metrics
    )
    val canShowOverflow = expanded && expandedLayout.showsOverflow
    val reservedOverflowHeadroom = if (canShowOverflow) {
        computeOverflowHeadroom(expandedLayout.overflowItems.size, metrics)
    } else {
        0.dp
    }
    val overflowHeadroom by animateDpAsState(
        targetValue = if (canShowOverflow && overflowExpanded) reservedOverflowHeadroom else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "bottomNavOverflowHeadroom"
    )
    val overflowRevealProgress by animateFloatAsState(
        targetValue = if (canShowOverflow && overflowExpanded) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "bottomNavOverflowProgress"
    )
    var overflowAnchorCenterX by remember { mutableFloatStateOf(Float.NaN) }
    var containerBoundsInRoot by remember { mutableStateOf<Rect?>(null) }
    val overflowPanelWidthPx = with(density) { metrics.overflowPanelWidth.toPx() }
    val overflowAnchorBiasPx = with(density) { metrics.overflowHorizontalBias.toPx() }
    val overflowLiftPx = with(density) { 16.dp.toPx() }
    val currentWidthPx = with(density) { currentWidth.toPx() }
    val barHeightPx = with(density) { metrics.barHeight.toPx() }
    val overflowHeadroomPx = with(density) { overflowHeadroom.toPx() }
    val overflowLeftShoulderReachPx = with(density) { metrics.overflowLeftShoulderReach.toPx() }
    val overflowRightShoulderReachPx = with(density) { metrics.overflowRightShoulderReach.toPx() }
    val requestedOverflowCenterX = if (canShowOverflow) {
        overflowAnchorCenterX + overflowAnchorBiasPx
    } else {
        Float.NaN
    }
    val overflowPanelLeftPx = computeOverflowPanelLeftPx(
        surfaceWidth = currentWidthPx,
        requestedCenterX = requestedOverflowCenterX,
        panelWidth = overflowPanelWidthPx
    )
    val overflowPanelCenterPx = computeOverflowPanelCenterPx(
        surfaceWidth = currentWidthPx,
        requestedCenterX = requestedOverflowCenterX,
        panelWidth = overflowPanelWidthPx
    )
    val overflowProtectedBounds = remember(
        containerBoundsInRoot,
        currentWidthPx,
        barHeightPx,
        overflowPanelLeftPx,
        overflowPanelWidthPx,
        overflowHeadroom,
        overflowRevealProgress,
        overflowLeftShoulderReachPx,
        overflowRightShoulderReachPx
    ) {
        computeBottomNavOverflowProtectedBounds(
            containerBoundsInRoot = containerBoundsInRoot,
            surfaceWidth = currentWidthPx,
            barHeight = barHeightPx,
            overflowPanelLeft = overflowPanelLeftPx,
            overflowPanelWidth = overflowPanelWidthPx,
            overflowHeadroom = overflowHeadroomPx,
            overflowRevealProgress = overflowRevealProgress,
            leftShoulderReach = overflowLeftShoulderReachPx,
            rightShoulderReach = overflowRightShoulderReachPx
        )
    }
    val overflowOutlineProgress = computeOverflowOutlineProgress(
        canShowOverflow = canShowOverflow,
        overflowExpanded = overflowExpanded,
        overflowRevealProgress = overflowRevealProgress
    )
    val interactionBlocked =
        abs(currentWidth.value - availableWidth.value) > 0.5f ||
            (overflowRevealProgress > 0.01f && overflowRevealProgress < 0.99f)

    SideEffect {
        onOverflowProtectedBoundsChange(overflowProtectedBounds)
    }

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = metrics.barHeight)
            .height(metrics.barHeight + overflowHeadroom)
            .onGloballyPositioned { coordinates ->
                containerBoundsInRoot = coordinates.boundsInRoot()
            }
            .testTag(BottomNavBarTag)
            .semantics {
                stateDescription = if (expanded) "expanded" else "collapsed"
            }
            .graphicsLayer { clip = false }
            .drawBehind {
                val outline = buildBottomNavContainerPath(
                    width = size.width,
                    height = size.height,
                    barHeight = metrics.barHeight.toPx(),
                    barRadius = metrics.barCornerRadius.toPx(),
                    overflowAnchorX = overflowPanelCenterPx,
                    overflowHeadroom = overflowHeadroomPx,
                    overflowPanelWidth = overflowPanelWidthPx,
                    overflowTopCapHeight = metrics.overflowTopCapHeight.toPx(),
                    overflowShoulderLift = metrics.overflowShoulderLift.toPx(),
                    overflowNeckBottomOffset = metrics.overflowNeckBottomOffset.toPx(),
                    leftShoulderReach = metrics.overflowLeftShoulderReach.toPx(),
                    rightShoulderReach = metrics.overflowRightShoulderReach.toPx(),
                    overflowShapeProgress = overflowOutlineProgress
                )
                drawPath(path = outline, color = containerColor)
                drawPath(
                    path = outline,
                    color = borderColor,
                    style = Stroke(
                        width = BottomNavBorderWidth.toPx(),
                        join = StrokeJoin.Round,
                        cap = StrokeCap.Round
                    )
                )
            }
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(metrics.barHeight)
                .graphicsLayer { clip = true }
        ) {
            motionEntries.forEachIndexed { index, entry ->
                val itemOffset = motionOffsets.getOrNull(index)?.minus(railShift) ?: 0.dp
                val selectedProgress = if (entry.isOverflow) {
                    maxOf(overflowSelectionProgress, overflowRevealProgress)
                } else {
                    resolvedSelectionProgresses[entry.item.route] ?: 0f
                }
                val entryVisibility = computeBottomNavEntryVisibility(
                    entryLeft = itemOffset,
                    entryWidth = entry.width,
                    currentWidth = currentWidth,
                    keepVisible = selectedProgress > 0.001f
                )
                val isFullyVisible =
                    itemOffset.value >= -0.5f &&
                        (itemOffset + entry.width).value <= currentWidth.value + 0.5f
                val slotModifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = itemOffset)
                    .width(entry.width)
                    .height(metrics.chipSize)
                    .graphicsLayer { alpha = entryVisibility }
                    .let { base ->
                        if (entry.isOverflow) {
                            base.onPlaced { coordinates ->
                                overflowAnchorCenterX =
                                    coordinates.boundsInParent().left + (coordinates.size.width / 2f)
                            }
                        } else {
                            base
                        }
                    }
                Box(
                    modifier = slotModifier,
                    contentAlignment = Alignment.Center
                ) {
                    BottomNavItemChip(
                        item = entry.item,
                        selectedProgress = selectedProgress,
                        collapsed = !expanded,
                        metrics = metrics,
                        enabled = if (entry.isOverflow) {
                            canShowOverflow && isFullyVisible
                        } else {
                            selectedProgress > 0.001f || isFullyVisible
                        },
                        modifier = if (entry.isOverflow) {
                            Modifier.testTag(BottomNavOverflowTag)
                        } else {
                            Modifier
                        },
                        onClick = {
                            if (!expanded && !entry.isOverflow) {
                                onExpandRequest()
                            } else if (entry.isOverflow) {
                                if (canShowOverflow) {
                                    onOverflowExpandedChange(!overflowExpanded)
                                }
                            } else {
                                onNavigate(entry.item.route)
                            }
                        }
                    )
                }
            }
        }

        if (
            canShowOverflow &&
            overflowRevealProgress > 0.01f &&
            overflowPanelLeftPx.isFinite()
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset {
                        IntOffset(
                            x = overflowPanelLeftPx.roundToInt(),
                            y = 0
                        )
                    }
                    .graphicsLayer {
                        alpha = overflowRevealProgress
                        translationY = (1f - overflowRevealProgress) * overflowLiftPx
                    }
                    .width(metrics.overflowPanelWidth)
                    .padding(
                        top = metrics.overflowTopCapHeight + metrics.overflowTopContentPadding,
                        bottom = metrics.overflowBottomPadding
                    )
                    .testTag("overflowIconStack"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(metrics.overflowItemSpacing)
            ) {
                expandedLayout.overflowItems.forEach { item ->
                    val selectedProgress = resolvedSelectionProgresses[item.route] ?: 0f
                    val entryColor = lerp(
                        colorScheme.surfaceVariant.copy(alpha = if (colorScheme.isDark) 0.28f else 0.68f),
                        colorScheme.primarySoft.copy(alpha = if (colorScheme.isDark) 0.44f else 0.76f),
                        selectedProgress
                    )
                    Box(
                        modifier = Modifier
                            .size(metrics.overflowItemSize)
                            .testTag("overflowIcon:${item.route}")
                            .background(entryColor, CircleShape)
                            .clickable {
                                onOverflowExpandedChange(false)
                                onNavigate(item.route)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            modifier = Modifier.size(metrics.iconSize),
                            tint = lerp(colorScheme.textSecondary, colorScheme.primaryStrong, selectedProgress)
                        )
                    }
                }
            }
        }

        if (interactionBlocked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
            )
        }
    }
}

private fun computeBottomNavOverflowProtectedBounds(
    containerBoundsInRoot: Rect?,
    surfaceWidth: Float,
    barHeight: Float,
    overflowPanelLeft: Float,
    overflowPanelWidth: Float,
    overflowHeadroom: Float,
    overflowRevealProgress: Float,
    leftShoulderReach: Float,
    rightShoulderReach: Float
): List<Rect> {
    val bounds = containerBoundsInRoot ?: return emptyList()
    if (!bounds.left.isFinite() || !bounds.top.isFinite() || !bounds.right.isFinite() || !bounds.bottom.isFinite()) {
        return emptyList()
    }

    val resolvedWidth = minOf(surfaceWidth, bounds.width).coerceAtLeast(0f)
    if (resolvedWidth <= 0.5f || barHeight <= 0.5f) return emptyList()

    val protectedBounds = mutableListOf<Rect>()
    protectedBounds += Rect(
        left = bounds.left,
        top = (bounds.bottom - barHeight).coerceAtLeast(bounds.top),
        right = bounds.left + resolvedWidth,
        bottom = bounds.bottom
    )

    if (
        overflowRevealProgress > 0.01f &&
        overflowHeadroom > 0.5f &&
        overflowPanelLeft.isFinite()
    ) {
        protectedBounds += Rect(
            left = (bounds.left + overflowPanelLeft - leftShoulderReach).coerceAtLeast(bounds.left),
            top = bounds.top,
            right = (bounds.left + overflowPanelLeft + overflowPanelWidth + rightShoulderReach)
                .coerceAtMost(bounds.left + resolvedWidth),
            bottom = (bounds.top + overflowHeadroom).coerceAtMost(bounds.bottom)
        )
    }

    return protectedBounds.filter { it.width > 0.5f && it.height > 0.5f }
}

private fun buildBottomNavContainerPath(
    width: Float,
    height: Float,
    barHeight: Float,
    barRadius: Float,
    overflowAnchorX: Float,
    overflowHeadroom: Float,
    overflowPanelWidth: Float,
    overflowTopCapHeight: Float,
    overflowShoulderLift: Float,
    overflowNeckBottomOffset: Float,
    leftShoulderReach: Float,
    rightShoulderReach: Float,
    overflowShapeProgress: Float = 1f
): Path {
    val barTop = height - barHeight
    val bottom = height
    val right = width
    val basePath = Path().apply {
        addRoundRect(
            RoundRect(
                left = 0f,
                top = barTop,
                right = right,
                bottom = bottom,
                topLeftCornerRadius = CornerRadius(barRadius, barRadius),
                topRightCornerRadius = CornerRadius(barRadius, barRadius),
                bottomLeftCornerRadius = CornerRadius(barRadius, barRadius),
                bottomRightCornerRadius = CornerRadius(barRadius, barRadius)
            )
        )
    }
    val resolvedShapeProgress = overflowShapeProgress.coerceIn(0f, 1f)
    val resolvedOverflowHeadroom = overflowHeadroom * resolvedShapeProgress
    val resolvedOverflowPanelWidth = overflowPanelWidth * resolvedShapeProgress
    val resolvedOverflowTopCapHeight = overflowTopCapHeight * resolvedShapeProgress
    val resolvedOverflowShoulderLift = overflowShoulderLift * resolvedShapeProgress
    val resolvedOverflowNeckBottomOffset = overflowNeckBottomOffset * resolvedShapeProgress
    val resolvedLeftShoulderReach = leftShoulderReach * resolvedShapeProgress
    val resolvedRightShoulderReach = rightShoulderReach * resolvedShapeProgress
    if (
        !overflowAnchorX.isFinite() ||
        resolvedShapeProgress <= 0.001f ||
        resolvedOverflowHeadroom <= 1f ||
        resolvedOverflowPanelWidth <= 1f
    ) {
        return basePath
    }

    val panelHalf = resolvedOverflowPanelWidth / 2f
    val stemLeft =
        (overflowAnchorX - panelHalf).coerceIn(4f, width - resolvedOverflowPanelWidth - 4f)
    val stemRight = stemLeft + resolvedOverflowPanelWidth
    val actualCenterX = stemLeft + panelHalf
    val topY = (barTop - resolvedOverflowHeadroom).coerceAtLeast(0f)
    val topCapHeight = minOf(resolvedOverflowTopCapHeight, resolvedOverflowHeadroom)
    val capBottomY = topY + topCapHeight
    val neckBottomY = (barTop - resolvedOverflowNeckBottomOffset)
        .coerceAtLeast(capBottomY)
        .coerceAtMost(barTop - 2f)
    val shoulderInset = (barTop - neckBottomY).coerceAtLeast(1f)
    val shoulderJoinRadius = minOf(
        shoulderInset,
        resolvedLeftShoulderReach.coerceAtLeast(1f),
        maxOf(resolvedOverflowShoulderLift, 2f)
    )
    val leftJoinStartY = barTop - shoulderJoinRadius
    val leftJoinEndX = (stemLeft - shoulderJoinRadius).coerceAtLeast(barRadius + 4f)
    val rightCornerStartX = right - barRadius
    val bottomCornerStartY = bottom - barRadius
    val domeControlX = panelHalf * 0.58f
    val domeControlY = topCapHeight * 0.56f
    val rightArcRect = Rect(
        left = right - (barRadius * 2f),
        top = barTop,
        right = right,
        bottom = barTop + (barRadius * 2f)
    )
    val rightArcCenterX = right - barRadius
    val rightArcCenterY = barTop + barRadius
    val desiredRightJoinX = (stemRight + resolvedRightShoulderReach).coerceAtMost(right - 1f)
    val rightJoinsArc = desiredRightJoinX > rightCornerStartX
    val rightJoinX: Float
    val rightJoinY: Float
    val rightJoinTangentX: Float
    val rightJoinTangentY: Float
    val rightJoinAngleDegrees: Float
    if (rightJoinsArc) {
        val clampedJoinX = desiredRightJoinX.coerceIn(rightCornerStartX, right - 1f)
        val dx = clampedJoinX - rightArcCenterX
        val dy = -sqrt(((barRadius * barRadius) - (dx * dx)).coerceAtLeast(0f))
        val tangentLength = sqrt((dx * dx) + (dy * dy)).coerceAtLeast(1f)
        rightJoinX = clampedJoinX
        rightJoinY = rightArcCenterY + dy
        rightJoinTangentX = dy / tangentLength
        rightJoinTangentY = -dx / tangentLength
        rightJoinAngleDegrees = Math.toDegrees(
            atan2(
                (rightJoinY - rightArcCenterY).toDouble(),
                (rightJoinX - rightArcCenterX).toDouble()
            )
        ).toFloat()
    } else {
        rightJoinX = desiredRightJoinX.coerceAtMost(rightCornerStartX)
        rightJoinY = barTop
        rightJoinTangentX = -1f
        rightJoinTangentY = 0f
        rightJoinAngleDegrees = -90f
    }
    val rightJoinEndY = barTop - shoulderJoinRadius
    val rightCurveStartHandle = minOf(
        maxOf((rightJoinX - stemRight) * 0.36f, 6f),
        barRadius * 0.7f
    )
    val rightCurveEndHandle = minOf(
        maxOf((rightJoinY - rightJoinEndY) * 0.72f, shoulderJoinRadius),
        barRadius * 0.86f
    )

    return Path().apply {
        moveTo(barRadius, bottom)
        lineTo(rightCornerStartX, bottom)
        quadraticBezierTo(right, bottom, right, bottomCornerStartY)
        lineTo(right, barTop + barRadius)
        arcTo(
            rect = rightArcRect,
            startAngleDegrees = 0f,
            sweepAngleDegrees = rightJoinAngleDegrees,
            forceMoveTo = false
        )
        if (!rightJoinsArc) {
            lineTo(rightJoinX, barTop)
        }
        cubicTo(
            rightJoinX + (rightJoinTangentX * rightCurveStartHandle),
            rightJoinY + (rightJoinTangentY * rightCurveStartHandle),
            stemRight,
            rightJoinEndY + rightCurveEndHandle,
            stemRight,
            rightJoinEndY
        )
        lineTo(stemRight, capBottomY)
        cubicTo(
            stemRight,
            topY + domeControlY,
            actualCenterX + domeControlX,
            topY,
            actualCenterX,
            topY
        )
        cubicTo(
            actualCenterX - domeControlX,
            topY,
            stemLeft,
            topY + domeControlY,
            stemLeft,
            capBottomY
        )
        lineTo(stemLeft, leftJoinStartY)
        cubicTo(
            stemLeft,
            leftJoinStartY + (shoulderJoinRadius * QuarterArcKappa),
            leftJoinEndX + (shoulderJoinRadius * QuarterArcKappa),
            barTop,
            leftJoinEndX,
            barTop
        )
        lineTo(barRadius, barTop)
        quadraticBezierTo(0f, barTop, 0f, barTop + barRadius)
        lineTo(0f, bottomCornerStartY)
        quadraticBezierTo(0f, bottom, barRadius, bottom)
        close()
    }
}

@Composable
private fun BottomNavItemChip(
    item: BottomChromeNavItem,
    selectedProgress: Float,
    collapsed: Boolean,
    metrics: BottomChromeMetrics,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = AsmrTheme.colorScheme
    val resolvedSelectedProgress = selectedProgress.coerceIn(0f, 1f)
    val activeContainer = colorScheme.primarySoft.copy(alpha = if (colorScheme.isDark) 0.52f else 0.95f)
    val inactiveContainer = if (colorScheme.isDark) {
        colorScheme.surfaceVariant.copy(alpha = 0.52f)
    } else {
        colorScheme.surfaceVariant.copy(alpha = 0.82f)
    }
    val contentColor = lerp(colorScheme.textSecondary, colorScheme.primaryStrong, resolvedSelectedProgress)
    val containerColor = lerp(inactiveContainer, activeContainer, resolvedSelectedProgress)
    val scale = 1f + (0.04f * resolvedSelectedProgress)
    val glowAlpha = resolvedSelectedProgress
    val glowSize by animateDpAsState(
        targetValue = if (collapsed) metrics.glowCollapsedSize else metrics.glowExpandedSize,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "bottomNavItemGlowSize"
    )
    val glowColor = colorScheme.primaryStrong.copy(alpha = if (colorScheme.isDark) 0.22f else 0.18f)

    Box(
        modifier = modifier
            .size(metrics.chipSize)
            .testTag("bottomNavItem:${item.route}"),
        contentAlignment = Alignment.Center
    ) {
        if (glowAlpha > 0.001f) {
            Box(
                modifier = Modifier
                    .size(glowSize)
                    .graphicsLayer { alpha = glowAlpha }
                    .background(glowColor, CircleShape)
            )
        }
        ElevatedCard(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .size(metrics.chipSize)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            shape = CircleShape,
            colors = CardDefaults.elevatedCardColors(
                containerColor = containerColor,
                contentColor = contentColor
            ),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
        ) {
            Box(
                modifier = Modifier.size(metrics.chipSize),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    modifier = Modifier.size(metrics.iconSize),
                    tint = contentColor
                )
            }
        }
    }
}
