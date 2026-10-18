package com.asmr.player.ui.nav

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.asmr.player.ui.common.consumeTapThrough
import com.asmr.player.ui.player.MiniPlayer
import com.asmr.player.ui.player.MiniPlayerDisplayMode
import com.asmr.player.ui.theme.AsmrTheme
import kotlin.math.abs

const val BottomNavBarTag = "bottomChromeNavBar"
const val BottomNavOverflowTag = "bottomChromeOverflow"
const val BottomChromeMiniPlayerTag = "bottomChromeMiniPlayer"

private val BottomChromeOverlayHeightCompact = 96.dp
private val BottomChromeOverlayHeightLarge = 108.dp
private val BottomNavBarHeight = 60.dp
private val BottomNavBarHeightLarge = 68.dp
private val BottomNavBarCornerRadius = 30.dp
private val BottomNavBarCornerRadiusLarge = 34.dp
private val BottomNavBorderWidth = 1.dp
private const val BottomNavExpandedSlotCount = 5
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
private val BottomNavIconSize = 21.dp
private val BottomNavIconSizeLarge = 24.dp
private val BottomNavGlowExpandedSize = 44.dp
private val BottomNavGlowExpandedSizeLarge = 50.dp
private val BottomNavGlowCollapsedSize = 46.dp
private val BottomNavGlowCollapsedSizeLarge = 52.dp
private const val BottomNavPageToggleGroupSize = BottomNavExpandedSlotCount - 1
private const val BottomChromeMinCompactScale = 0.72f
internal val BottomChromeMaxGroupWidthCompact = 560.dp
internal val BottomChromeMaxGroupWidthLarge = 680.dp

internal fun bottomChromeWidthLimit(availableWidth: Dp, largeLayout: Boolean): Dp {
    val maximumWidth = if (largeLayout) {
        BottomChromeMaxGroupWidthLarge
    } else {
        BottomChromeMaxGroupWidthCompact
    }
    return availableWidth.coerceAtMost(maximumWidth)
}

internal fun bottomChromeMiniPlayerExpandedWidth(
    chromeWidthLimit: Dp,
    collapsedNavWidth: Dp,
    chromeSpacing: Dp,
    minimumWidth: Dp,
    expandedNavWidth: Dp
): Dp = (chromeWidthLimit - collapsedNavWidth - chromeSpacing)
    .coerceAtLeast(minimumWidth)
    .coerceAtMost(expandedNavWidth)

data class BottomChromeNavItem(
    val icon: ImageVector,
    val label: String,
    val route: String
)

private data class BottomNavRailEntry(
    val item: BottomChromeNavItem,
    val width: Dp,
    val isGroupToggle: Boolean = false
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
    val iconSize: Dp,
    val glowExpandedSize: Dp,
    val glowCollapsedSize: Dp
) {
    val preferredExpandedWidth: Dp
        get() = (expandedHorizontalPadding * 2) +
            (itemSlotWidth * BottomNavExpandedSlotCount) +
            (expandedItemSpacing * (BottomNavExpandedSlotCount - 1))

    fun scaled(scale: Float): BottomChromeMetrics {
        val resolvedScale = scale.coerceIn(BottomChromeMinCompactScale, 1f)
        if (resolvedScale >= 0.999f) return this
        return copy(
            overlayHeight = overlayHeight * resolvedScale,
            barHeight = barHeight * resolvedScale,
            barCornerRadius = barCornerRadius * resolvedScale,
            collapsedWidth = collapsedWidth * resolvedScale,
            chipSize = chipSize * resolvedScale,
            itemSlotWidth = itemSlotWidth * resolvedScale,
            expandedItemSpacing = expandedItemSpacing * resolvedScale,
            expandedHorizontalPadding = expandedHorizontalPadding * resolvedScale,
            iconSize = iconSize * resolvedScale,
            glowExpandedSize = glowExpandedSize * resolvedScale,
            glowCollapsedSize = glowCollapsedSize * resolvedScale
        )
    }
}

fun bottomChromeOverlayHeight(largeLayout: Boolean): Dp =
    if (largeLayout) BottomChromeOverlayHeightLarge else BottomChromeOverlayHeightCompact

internal fun resolveBottomNavItemContainerColor(
    activeContainer: Color,
    selectedProgress: Float
): Color {
    val resolvedSelectedProgress = selectedProgress.coerceIn(0f, 1f)
    if (resolvedSelectedProgress <= 0f) return Color.Transparent
    return activeContainer.copy(alpha = activeContainer.alpha * resolvedSelectedProgress)
}

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
            iconSize = BottomNavIconSize,
            glowExpandedSize = BottomNavGlowExpandedSize,
            glowCollapsedSize = BottomNavGlowCollapsedSize
        )
    }

fun bottomChromeNavItems(): List<BottomChromeNavItem> = listOf(
    BottomChromeNavItem(Icons.Rounded.Home, "本地库", Routes.Library),
    BottomChromeNavItem(Icons.Rounded.Search, "在线搜索", Routes.Search),
    BottomChromeNavItem(Icons.Rounded.Whatshot, "热门收听", Routes.HotListening),
    BottomChromeNavItem(Icons.Rounded.Favorite, "我的收藏", "playlist_system/favorites"),
    BottomChromeNavItem(Icons.AutoMirrored.Rounded.QueueMusic, "我的列表", "playlists"),
    BottomChromeNavItem(Icons.Rounded.Folder, "我的分组", "groups"),
    BottomChromeNavItem(Icons.Rounded.Route, "ASMR 看板", "listening_calendar"),
    BottomChromeNavItem(Icons.Rounded.Settings, "设置", "settings")
)

fun isPrimaryRoute(route: String?): Boolean {
    if (route.isNullOrBlank()) return false
    return route in setOf(
        Routes.Library,
        Routes.Search,
        Routes.HotListening,
        "playlist_system/favorites",
        "playlists",
        "groups",
        "settings",
        "listening_calendar"
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
        currentRoute == Routes.SearchAssist -> Routes.Search
        currentRoute == Routes.SearchAssistPattern -> Routes.Search
        currentRoute == Routes.HotListening -> Routes.HotListening
        currentRoute == "playlists" -> "playlists"
        currentRoute == "groups" -> "groups"
        currentRoute == "settings" -> "settings"
        currentRoute == "listening_calendar" -> "listening_calendar"
        currentRoute == "playlist_system/{type}" && playlistSystemType == "favorites" -> "playlist_system/favorites"
        currentRoute == "playlist/{playlistId}/{playlistName}" -> "playlists"
        currentRoute == "group/{groupId}/{groupName}" -> "groups"
        currentRoute == "library_filter" -> Routes.Library
        currentRoute?.startsWith("album_detail") == true -> lastPrimaryRoute ?: Routes.Library
        else -> lastPrimaryRoute ?: Routes.Library
    }
}

private fun resolveBottomNavGroupIndex(
    navItems: List<BottomChromeNavItem>,
    route: String,
    groupSize: Int = BottomNavPageToggleGroupSize
): Int? {
    val routeIndex = navItems.indexOfFirst { it.route == route }
        .takeIf { it >= 0 }
        ?: return null
    return routeIndex / groupSize.coerceAtLeast(1)
}

private fun buildBottomNavRailEntries(
    visibleItems: List<BottomChromeNavItem>,
    showsGroupToggle: Boolean,
    metrics: BottomChromeMetrics
): List<BottomNavRailEntry> {
    if (visibleItems.isEmpty()) return emptyList()

    val entries = visibleItems
        .map { item ->
            BottomNavRailEntry(
                item = item,
                width = metrics.itemSlotWidth
            )
        }
        .toMutableList()

    if (showsGroupToggle) {
        entries += BottomNavRailEntry(
            item = BottomChromeNavItem(Icons.Rounded.SwapHoriz, "切换", BottomNavOverflowTag),
            width = metrics.itemSlotWidth,
            isGroupToggle = true
        )
    }

    return entries
}

private fun computeBottomNavRailOffsets(
    entries: List<BottomNavRailEntry>,
    currentWidth: Dp,
    metrics: BottomChromeMetrics
): List<Dp> {
    if (entries.isEmpty()) return emptyList()

    val gapCount = (entries.size - 1).coerceAtLeast(0)
    val baseContentWidth = entries.fold(0.dp) { total, entry -> total + entry.width } +
        metrics.expandedHorizontalPadding * 2 +
        metrics.expandedItemSpacing * gapCount
    val extraWidth = (currentWidth - baseContentWidth).coerceAtLeast(0.dp)
    val extraSpacing = if (gapCount > 0) {
        extraWidth / (gapCount + 2)
    } else {
        0.dp
    }
    val resolvedSpacing = metrics.expandedItemSpacing + extraSpacing
    val resolvedContentWidth = entries.fold(0.dp) { total, entry -> total + entry.width } +
        metrics.expandedHorizontalPadding * 2 +
        resolvedSpacing * gapCount
    var currentOffset = metrics.expandedHorizontalPadding +
        ((currentWidth - resolvedContentWidth).coerceAtLeast(0.dp) / 2)
    val offsets = ArrayList<Dp>(entries.size)
    entries.forEachIndexed { index, entry ->
        offsets += currentOffset
        currentOffset += entry.width
        if (index != entries.lastIndex) {
            currentOffset += resolvedSpacing
        }
    }
    return offsets
}

private fun computeBottomNavRailShift(
    entries: List<BottomNavRailEntry>,
    offsets: List<Dp>,
    activeRoute: String,
    collapseProgress: Float,
    metrics: BottomChromeMetrics
): Dp {
    val activeIndex = entries.indexOfFirst { !it.isGroupToggle && it.item.route == activeRoute }
        .takeIf { it >= 0 }
        ?: return 0.dp
    val activeLeft = offsets.getOrNull(activeIndex) ?: return 0.dp
    val activeWidth = entries[activeIndex].width
    val collapsedActiveLeft = ((metrics.collapsedWidth - activeWidth) / 2f).coerceAtLeast(0.dp)
    val collapsedShift = activeLeft - collapsedActiveLeft
    if (collapsedShift == 0.dp) return 0.dp

    return collapsedShift * collapseProgress.coerceIn(0f, 1f)
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
    miniPlayerVisible: Boolean,
    miniPlayerDisplayMode: MiniPlayerDisplayMode,
    onMiniPlayerDisplayModeChange: (MiniPlayerDisplayMode) -> Unit,
    onOpenNowPlaying: () -> Unit,
    onOpenQueue: () -> Unit,
    onNavigate: (String) -> Unit,
    miniPlayerPlayFeedbackSignal: Long = 0L,
    largeLayout: Boolean = false,
    modifier: Modifier = Modifier,
    navItems: List<BottomChromeNavItem> = bottomChromeNavItems(),
    miniPlayerContent: (@Composable (Modifier) -> Unit)? = null
) {
    BoxWithConstraints(modifier = modifier) {
        val baseMetrics = remember(largeLayout) { bottomChromeMetrics(largeLayout) }
        val navExpanded = !miniPlayerVisible || miniPlayerDisplayMode == MiniPlayerDisplayMode.CoverOnly
        val baseMiniCollapsedWidth = if (largeLayout) 84.dp else 72.dp
        val baseChromeSpacing = if (largeLayout) 8.dp else 6.dp
        val chromeWidthLimit = bottomChromeWidthLimit(maxWidth, largeLayout)
        val compactScaleTarget = remember(
            chromeWidthLimit,
            miniPlayerVisible,
            baseMetrics,
            baseMiniCollapsedWidth,
            baseChromeSpacing
        ) {
            val requiredWidth = baseMetrics.preferredExpandedWidth +
                if (miniPlayerVisible) baseMiniCollapsedWidth + baseChromeSpacing else 0.dp
            (chromeWidthLimit.value / requiredWidth.value)
                .coerceIn(BottomChromeMinCompactScale, 1f)
        }
        val compactScale by animateFloatAsState(
            targetValue = compactScaleTarget,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "bottomChromeCompactScale"
        )
        val metrics = remember(baseMetrics, compactScale) { baseMetrics.scaled(compactScale) }
        val miniCollapsedWidth = baseMiniCollapsedWidth * compactScale
        val miniExpandedMinWidth = (if (largeLayout) 244.dp else 204.dp) * compactScale
        val chromeSpacing = baseChromeSpacing * compactScale
        val expandedNavWidthLimit = chromeWidthLimit.coerceAtMost(metrics.preferredExpandedWidth)
        val expandedNavWidth = expandedNavWidthLimit
        val miniExpandedWidth = bottomChromeMiniPlayerExpandedWidth(
            chromeWidthLimit = chromeWidthLimit,
            collapsedNavWidth = metrics.collapsedWidth,
            chromeSpacing = chromeSpacing,
            minimumWidth = miniExpandedMinWidth,
            expandedNavWidth = expandedNavWidth
        )
        val expansionProgress by animateFloatAsState(
            targetValue = if (miniPlayerVisible && miniPlayerDisplayMode == MiniPlayerDisplayMode.Expanded) 1f else 0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "bottomChromeExpansionProgress"
        )
        val miniWidth = if (!miniPlayerVisible) {
            0.dp
        } else {
            miniCollapsedWidth + (miniExpandedWidth - miniCollapsedWidth) * expansionProgress
        }
        val navWidthTarget = if (miniPlayerVisible && miniPlayerDisplayMode == MiniPlayerDisplayMode.Expanded) {
            metrics.collapsedWidth
        } else {
            expandedNavWidth
        }
        val navWidth = if (!miniPlayerVisible) {
            expandedNavWidth
        } else {
            expandedNavWidth + (metrics.collapsedWidth - expandedNavWidth) * expansionProgress
        }
        val navCollapseProgress = if (miniPlayerVisible) expansionProgress else 0f
        val coverOnlyGroupWidth = if (miniPlayerVisible) {
            expandedNavWidth + chromeSpacing + miniCollapsedWidth
        } else {
            expandedNavWidth
        }
        val expandedGroupWidth = if (miniPlayerVisible) {
            metrics.collapsedWidth + chromeSpacing + miniExpandedWidth
        } else {
            expandedNavWidth
        }
        val trackWidth = if (miniPlayerVisible) {
            maxOf(coverOnlyGroupWidth, expandedGroupWidth).coerceAtMost(chromeWidthLimit)
        } else {
            expandedNavWidth
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(metrics.barHeight)
                .align(Alignment.BottomCenter)
                .graphicsLayer { clip = false }
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .width(trackWidth)
                    .height(metrics.barHeight)
                    .graphicsLayer { clip = false }
            ) {
                BottomNavigationPill(
                    navItems = navItems,
                    activeRoute = activeRoute,
                    selectionProgresses = selectionProgresses,
                    expanded = navExpanded,
                    availableWidth = expandedNavWidth,
                    targetWidth = navWidthTarget,
                    currentWidth = navWidth,
                    collapseProgress = navCollapseProgress,
                    metrics = metrics,
                    onExpandRequest = { onMiniPlayerDisplayModeChange(MiniPlayerDisplayMode.CoverOnly) },
                    onNavigate = onNavigate,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .width(navWidth)
                )

                if (miniPlayerVisible) {
                    val miniPlayerModifier = Modifier
                        .align(Alignment.BottomEnd)
                        .width(miniWidth.coerceAtLeast(miniCollapsedWidth))
                        .testTag(BottomChromeMiniPlayerTag)
                    if (miniPlayerContent != null) {
                        miniPlayerContent(miniPlayerModifier)
                    } else {
                        MiniPlayer(
                            displayMode = miniPlayerDisplayMode,
                            onDisplayModeChange = onMiniPlayerDisplayModeChange,
                            onOpenNowPlaying = onOpenNowPlaying,
                            onOpenQueue = onOpenQueue,
                            playFeedbackSignal = miniPlayerPlayFeedbackSignal,
                            largeLayout = largeLayout,
                            compactScale = compactScale,
                            modifier = miniPlayerModifier
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomNavigationPill(
    navItems: List<BottomChromeNavItem>,
    activeRoute: String,
    selectionProgresses: Map<String, Float>,
    expanded: Boolean,
    availableWidth: Dp,
    targetWidth: Dp,
    currentWidth: Dp,
    collapseProgress: Float,
    metrics: BottomChromeMetrics,
    onExpandRequest: () -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = AsmrTheme.colorScheme
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
    val containerColor = colorScheme.primarySoft
        .copy(alpha = if (colorScheme.isDark) 0.05f else 0.08f)
        .compositeOver(colorScheme.surface)
    val borderColor = if (colorScheme.isDark) {
        Color.White.copy(alpha = 0.14f)
    } else {
        colorScheme.primaryStrong.copy(alpha = 0.14f)
    }
    val activeItem = navItems.firstOrNull { it.route == layoutFocusRoute } ?: navItems.firstOrNull()
    val navGroups = remember(navItems) {
        navItems.chunked(BottomNavPageToggleGroupSize).filter { it.isNotEmpty() }
    }
    val navGroupKey = remember(navItems) {
        navItems.joinToString(separator = "|") { it.route }
    }
    var displayedGroupIndex by rememberSaveable(navGroupKey) {
        mutableStateOf(resolveBottomNavGroupIndex(navItems, activeRoute) ?: 0)
    }
    var lastObservedActiveRoute by rememberSaveable(navGroupKey) {
        mutableStateOf(activeRoute)
    }
    LaunchedEffect(activeRoute, navItems) {
        if (activeRoute != lastObservedActiveRoute) {
            resolveBottomNavGroupIndex(navItems, activeRoute)?.let { displayedGroupIndex = it }
            lastObservedActiveRoute = activeRoute
        }
    }
    val gestureFocusRoute = remember(activeRoute, resolvedSelectionProgresses) {
        resolvedSelectionProgresses
            .maxByOrNull { (_, progress) -> progress }
            ?.takeIf { it.value > 0.001f }
            ?.key
            ?: activeRoute
    }
    val selectionTransitionInFlight = remember(resolvedSelectionProgresses) {
        resolvedSelectionProgresses.count { (_, progress) -> progress > 0.001f } > 1
    }
    val gestureDrivenGroupIndex = remember(navItems, gestureFocusRoute, selectionTransitionInFlight) {
        if (selectionTransitionInFlight) {
            resolveBottomNavGroupIndex(navItems, gestureFocusRoute)
        } else {
            null
        }
    }
    val resolvedGroupIndex = (gestureDrivenGroupIndex ?: displayedGroupIndex)
        .coerceIn(0, (navGroups.lastIndex).coerceAtLeast(0))
    if (resolvedGroupIndex != displayedGroupIndex) {
        SideEffect {
            displayedGroupIndex = resolvedGroupIndex
        }
    }
    val visibleGroupItems = navGroups.getOrNull(resolvedGroupIndex).orEmpty()
        .ifEmpty { listOfNotNull(activeItem) }
    val showsGroupToggle = navGroups.size > 1
    val toggleSelectionProgress = if (showsGroupToggle && resolvedGroupIndex > 0) 1f else 0f
    val motionEntries = buildBottomNavRailEntries(visibleGroupItems, showsGroupToggle, metrics)
    val motionOffsets = computeBottomNavRailOffsets(
        entries = motionEntries,
        currentWidth = availableWidth,
        metrics = metrics
    )
    val railShift = computeBottomNavRailShift(
        entries = motionEntries,
        offsets = motionOffsets,
        activeRoute = layoutFocusRoute,
        collapseProgress = collapseProgress,
        metrics = metrics
    )
    val interactionBlocked = abs(currentWidth.value - targetWidth.value) > 0.5f

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = metrics.barHeight)
            .height(metrics.barHeight)
            .testTag(BottomNavBarTag)
            .semantics {
                stateDescription = if (expanded) "expanded" else "collapsed"
            }
            .graphicsLayer { clip = false }
            .drawBehind {
                val drawBarHeightPx = metrics.barHeight.toPx()
                val drawBarRadiusPx = metrics.barCornerRadius.toPx()
                val borderStroke = Stroke(
                    width = BottomNavBorderWidth.toPx(),
                    join = StrokeJoin.Round,
                    cap = StrokeCap.Round
                )
                val topLeft = Offset(0f, size.height - drawBarHeightPx)
                val barSize = Size(size.width, drawBarHeightPx)
                val cornerRadius = CornerRadius(drawBarRadiusPx, drawBarRadiusPx)
                drawRoundRect(
                    color = containerColor,
                    topLeft = topLeft,
                    size = barSize,
                    cornerRadius = cornerRadius
                )
                drawRoundRect(
                    color = borderColor,
                    topLeft = topLeft,
                    size = barSize,
                    cornerRadius = cornerRadius,
                    style = borderStroke
                )
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .consumeTapThrough()
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(metrics.barHeight)
                .graphicsLayer { clip = true }
        ) {
            motionEntries.forEachIndexed { index, entry ->
                val itemOffset = motionOffsets.getOrNull(index)?.minus(railShift) ?: 0.dp
                val selectedProgress = if (entry.isGroupToggle) {
                    toggleSelectionProgress
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
                Box(
                    modifier = slotModifier,
                    contentAlignment = Alignment.Center
                ) {
                    BottomNavItemChip(
                        item = entry.item,
                        selectedProgress = selectedProgress,
                        collapseProgress = collapseProgress,
                        metrics = metrics,
                        enabled = if (entry.isGroupToggle) {
                            navGroups.size > 1 && isFullyVisible
                        } else {
                            selectedProgress > 0.001f || isFullyVisible
                        },
                        modifier = if (entry.isGroupToggle) {
                            Modifier.testTag(BottomNavOverflowTag)
                        } else {
                            Modifier
                        },
                        onClick = {
                            if (!expanded && !entry.isGroupToggle) {
                                onExpandRequest()
                            } else if (entry.isGroupToggle) {
                                if (navGroups.size > 1) {
                                    displayedGroupIndex = (resolvedGroupIndex + 1) % navGroups.size
                                }
                            } else {
                                onNavigate(entry.item.route)
                            }
                        }
                    )
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

@Composable
private fun BottomNavItemChip(
    item: BottomChromeNavItem,
    selectedProgress: Float,
    collapseProgress: Float,
    metrics: BottomChromeMetrics,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = AsmrTheme.colorScheme
    val resolvedSelectedProgress = selectedProgress.coerceIn(0f, 1f)
    val activeContainer = colorScheme.primarySoft.copy(alpha = if (colorScheme.isDark) 0.52f else 0.95f)
    val contentColor = lerp(colorScheme.textSecondary, colorScheme.primaryStrong, resolvedSelectedProgress)
    val containerColor = resolveBottomNavItemContainerColor(
        activeContainer = activeContainer,
        selectedProgress = resolvedSelectedProgress
    )
    val iconScale = 1f + (0.16f * resolvedSelectedProgress)
    val glowAlpha = resolvedSelectedProgress
    val glowSize = metrics.glowExpandedSize +
        (metrics.glowCollapsedSize - metrics.glowExpandedSize) * collapseProgress.coerceIn(0f, 1f)
    val glowColor = colorScheme.primaryStrong.copy(alpha = if (colorScheme.isDark) 0.22f else 0.18f)
    val interactionSource = remember { MutableInteractionSource() }

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
        Box(
            modifier = Modifier
                .size(metrics.chipSize)
                .background(containerColor, CircleShape)
                .clip(CircleShape)
                // ElevatedCard's state layer can briefly show a polygonal highlight during
                // selection transitions on some devices. Use a plain circular surface instead.
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = item,
                transitionSpec = {
                    (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                        scaleIn(
                            initialScale = 0.82f,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                        )) togetherWith
                        (fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
                            scaleOut(
                                targetScale = 1.08f,
                                animationSpec = spring(stiffness = Spring.StiffnessMedium)
                            )) using SizeTransform(clip = false)
                },
                label = "bottomNavItemIcon"
            ) { targetItem ->
                Icon(
                    imageVector = targetItem.icon,
                    contentDescription = targetItem.label,
                    modifier = Modifier
                        .size(metrics.iconSize)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        },
                    tint = contentColor
                )
            }
        }
    }
}
