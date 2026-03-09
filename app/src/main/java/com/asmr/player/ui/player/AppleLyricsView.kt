package com.asmr.player.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asmr.player.util.SubtitleEntry
import com.asmr.player.util.SubtitleIndexFinder
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@Composable
fun AppleLyricsView(
    lyrics: List<SubtitleEntry>,
    currentPosition: Long,
    onSeekTo: (Long) -> Unit,
    onOpenLyrics: () -> Unit = {},
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
    modifier: Modifier = Modifier,
    isLandscape: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val indexFinder = remember(lyrics) { SubtitleIndexFinder(lyrics) }
    val activeIndex = remember(currentPosition, indexFinder) {
        indexFinder.findActiveIndex(currentPosition)
    }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Store per-item translation offsets
    // Key: Item Index, Value: Animatable OffsetY
    val itemOffsets = remember { mutableStateMapOf<Int, Animatable<Float, AnimationVector1D>>() }

    // Configuration for the "Focus Area" (slightly below center)
    val focusRatio = 0.4f // 40% from top (so 60% space below? No, usually slightly below center means top is ~40-45%)
    // Apple Music is actually often around 30-40% from top. User said "near middle but slightly below".
    // Let's interpret "slightly below center" as the *highlight* is at ~55%?
    // User: "located in visual focus area (near middle but slightly below)".
    // Let's try 0.45f (45% from top).

    // Track the previous active index to detect changes
    // Use rememberSaveable to persist state across configuration changes (orientation)
    var previousIndex by remember { mutableIntStateOf(activeIndex) }
    
    // Use a key that changes on orientation or activeIndex to force update if needed
    // Actually, if we rotate, AppleLyricsView is recomposed.
    // If activeIndex is correct, listState.scrollToItem should be called.
    // But current logic only scrolls if (activeIndex != previousIndex).
    // On rotation, previousIndex is re-initialized to activeIndex (because of remember { mutableIntStateOf(activeIndex) }).
    // So activeIndex == previousIndex.
    // So the LaunchedEffect(activeIndex) block is SKIPPED (except for initial check, but activeIndex == previousIndex).
    
    // We need to force scroll to activeIndex on first composition (or orientation change).
    // We use a key that includes configuration to trigger on rotation.
    val configuration = LocalConfiguration.current
    LaunchedEffect(Unit, configuration) {
         if (activeIndex >= 0 && lyrics.isNotEmpty()) {
              // Wait a frame for layout to be ready so we can get viewport height
              // But we can't easily wait for layout in LaunchedEffect(Unit).
              // We can just try to scroll.
              // A negative offset puts it towards the top/center.
              // To be precise, we need viewportHeight.
              
              // We can use a simple approximate offset for now, or just rely on the fact 
              // that on rotation, we want to re-snap.
              
              // Let's force a "refresh" of the position logic by resetting previousIndex?
              // No, that would trigger animation. We want snap.
              
              // Correct fix:
              // Just call scrollToItem with a reasonable offset.
              // Since we don't know the exact pixel height yet, maybe just index is enough?
              // But we want it centered (or at focusRatio).
              // If we just scroll to index, it goes to top.
              // We need offset.
              
              // Retry scrolling after a short delay to allow layout?
              // Or better: Observe layout changes?
              
              // Simple fix: Just update previousIndex to -1 so the main effect runs?
              // If we set previousIndex = -1, the main effect sees activeIndex != previousIndex.
              // It will trigger the "Large Jump" or "Small Jump" logic.
              // If it triggers "Small Jump" (Wave), it might look weird on rotation.
              // If abs > 2, it snaps.
              // So if we force previousIndex = -1, it will likely be > 2 diff?
              // No, activeIndex might be 0 or 1.
              
              // Let's just manually scroll here.
              listState.scrollToItem(activeIndex, -200) // -200px is roughly a good offset
              
              // Ensure highlight state is correct by resetting previousIndex
              // This forces the next update to be seen as a change if needed, 
              // or just ensures we are in sync.
              // Actually, if we rotate, previousIndex is initialized to activeIndex.
              // So no wave animation triggers. This is correct.
              // But we want to ensure scroll position is correct.
         }
    }

    // Clear offsets when lyrics change to avoid memory leaks or incorrect offsets
    LaunchedEffect(lyrics) {
        itemOffsets.clear()
    }

    LaunchedEffect(activeIndex) {
        if (activeIndex != previousIndex && activeIndex >= 0 && lyrics.isNotEmpty()) {
            val isMovingDown = activeIndex > previousIndex
            
            // 1. Calculate the Snap
            // We want to center the NEW activeIndex at `viewportHeight * focusRatio`
            val layoutInfo = listState.layoutInfo
            val viewportHeight = layoutInfo.viewportSize.height
            
            // We can't know the exact size of the new item if it's not visible.
            // But if we are scrolling sequentially, it's likely just below or nearby.
            // If it's a large jump (seek), we just snap without wave.
            
            if ((activeIndex - previousIndex).absoluteValue > 2) {
                // Large jump: just scroll smoothly or snap
                listState.scrollToItem(activeIndex, -(viewportHeight * focusRatio).toInt())
                previousIndex = activeIndex
                return@LaunchedEffect
            }

            // Small jump: Perform Wave
            // Check if we actually NEED to scroll.
            // If the item is already above the focus point (and we are near top), we might not need to scroll.
            // But for consistency, we try to scroll to focus point, and LazyColumn clamps it.
            
            // Current position of i+1 (if visible)
            val nextItemInfo = layoutInfo.visibleItemsInfo.find { it.index == activeIndex }
            val currentItemInfo = layoutInfo.visibleItemsInfo.find { it.index == previousIndex }
            
            // Fallback height if not visible (estimate)
            val estimatedHeight = currentItemInfo?.size ?: 100 
            
            // Calculate target scroll offset
            // We want activeIndex to be at targetY
            val targetY = (viewportHeight * focusRatio).toInt()
            
            // Execute Snap
            listState.scrollToItem(activeIndex, -targetY)
            
            // Calculate how much we ACTUALLY scrolled (visually)
            // If we were at top (offset 0), and we called scrollToItem(0, -targetY),
            // LazyColumn stays at offset 0. So visual shift is 0.
            // We need to know the visual shift to apply the inverse translation.
            
            // We can compare the position of a stable item?
            // Or simpler: We know where 'previousIndex' was.
            // previousIndex was at 'previousItemInfo.offset'.
            // Now, where is 'previousIndex'?
            // We need to wait for layout? No, scrollToItem happens instantly in state, but layout info updates later?
            // Actually, scrollToItem requests a scroll. The layout info might not update until next frame.
            // BUT, we need to set offsets NOW.
            
            // Let's assume the standard behavior:
            // We WANT the previous item to stay visually where it was.
            // The previous item was at `currentItemInfo.offset`.
            // After scroll, where WILL it be?
            // We moved `activeIndex` to `targetY`.
            // `previousIndex` is `activeIndex - 1` (if moving down).
            // So `previousIndex` will be at `targetY - previousItemHeight`.
            // So the shift is `(targetY - previousItemHeight) - currentItemInfo.offset`.
            // Wait, if we are at the top and don't scroll, `targetY` is not reached.
            
            // Better approach:
            // Don't calculate shift. Just define the animation:
            // "Everything moves UP by one row height".
            // So we shift everything DOWN by one row height, and animate to 0.
            // EXCEPT if we are at the top and didn't scroll.
            // How to detect if we scrolled?
            // If `activeIndex` is small (e.g. < 5), we might be clamping.
            
            // Heuristic:
            // If `activeIndex` is 0, we are at top.
            // If `activeIndex` increases, but we are still filling the top space...
            
            val jumpDelta = if (currentItemInfo != null) {
                 currentItemInfo.size.toFloat()
            } else {
                 estimatedHeight.toFloat()
            }
            
            // Only apply wave if we are "in the flow". 
            // If we are at the very top (e.g. index 0 -> 1), and both are visible without scrolling...
            // Visual check: is `currentItemInfo.offset` already < targetY?
            // If current item was at 100px, and targetY is 500px.
            // We don't scroll. The item just changes color.
            // But user wants "wave" even then?
            // "When lyrics switch... move up... from top of visible list".
            // If list doesn't scroll, items don't move up physically.
            // So we shouldn't translate them down.
            
            // We only translate if the list content SCROLLED underneath.
            // If we are just highlighting next line, and list is static, no translation needed?
            // User: "Move up... based on current highlighted line height... sequential wave".
            // This implies the list ALWAYS moves up?
            // "Lyrics always maintain vertical alignment and stable reading rhythm".
            // "Highlight switch has 'lifted to focus' feeling".
            
            // If we are at the top, and we switch 0 -> 1.
            // Item 0 is at top. Item 1 is below it.
            // If we keep Item 1 at its position (below top), it's not "lifted to focus".
            // Unless "Focus" implies scrolling.
            // But if we can't scroll (clamped), we can't lift it to the physical screen center.
            // In that case, we just highlight.
            // So: Only apply offset if we actually scrolled.
            
            // How to know if we scrolled?
            // We can check `listState.firstVisibleItemIndex` and `scrollOffset` before and after?
            // But `scrollToItem` is async-ish in effect?
            // No, `scrollToItem` updates the state immediately for the next measure pass.
            
            // Let's rely on the visual position check.
            // If the item (previous active) was ALREADY above or at the target focus area, we probably scrolled.
            // If it was well above (e.g. at top 0), and target is 500, we definitely didn't scroll UP.
            // We only scroll if the new item is BELOW the target.
            
            // Simplified Logic:
            // Always apply the offset animation logic, BUT...
            // If we are clamped at top, `scrollToItem` won't move the items.
            // So if we apply `translationY = jumpDelta`, we will push items DOWN visually, while they stay physically in place.
            // Then they slide UP to their original place.
            // This looks like they moved down then up. Bad.
            // We want them to appear static, then move up.
            // This implies they must physically move up (scroll) to cancel the translation.
            
            // So we ONLY apply translation if `scrollToItem` successfully moved the list.
            // Since we can't easily know the result of `scrollToItem` before it happens...
            // We can predict clamping.
            // Total height of items 0..activeIndex?
            // Too complex.
            
            // Workaround:
            // Only apply wave if `activeIndex > 3` (heuristic) OR if we know we are past the fold.
            // Or, observe `listState` snapshot.
            
            // Let's just allow the wave for indices > 0, but dampen it if we are near top?
            // Actually, if we are at top, `previousIndex=0` is at `topPad`.
            // `topPad` is small now.
            // So `previousIndex` is at ~0.
            // `targetY` is ~500.
            // If we switch 0 -> 1. 1 is at ~50.
            // We want 1 to be at ~500? No, we want 1 to be at ~50 (clamped).
            // So we call `scrollToItem(1, -500)`. LazyColumn clamps to 0.
            // Item 1 stays at ~50.
            // If we apply translation, it jumps.
            
            // So: Check if `currentItemInfo.offset` is significantly smaller than `targetY`.
            // If `currentItemInfo.offset < targetY - currentItemInfo.size`, it means we are "above" the focus zone.
            // In this case, we likely won't scroll (or scroll very little).
            // So we should NOT apply the full jump delta.
            
            val currentOffset = currentItemInfo?.offset ?: 0
            val shouldAnimateWave = currentOffset >= (targetY - (currentItemInfo?.size ?: 0) * 2) 
            // Heuristic: If we are close to the target Y (within 2 rows), we assume we are in "scrolling mode".
            // If we are way above (offset 0 vs target 500), we are in "top clamp mode".
            
            if (shouldAnimateWave) {
                 val visibleIndices = listState.layoutInfo.visibleItemsInfo.map { it.index }
                 visibleIndices.forEach { index ->
                     val anim = itemOffsets.getOrPut(index) { Animatable(0f, Float.VectorConverter) }
                     anim.snapTo(jumpDelta)
                 }
                 
                 val firstVisible = visibleIndices.minOrNull() ?: 0
                 visibleIndices.forEach { index ->
                     val anim = itemOffsets[index] ?: return@forEach
                     val rowDelay = (index - firstVisible) * 40
                     launch {
                         anim.animateTo(
                             targetValue = 0f,
                             animationSpec = tween(
                                 durationMillis = 500,
                                 delayMillis = rowDelay,
                                 easing = FastOutSlowInEasing
                             )
                         )
                     }
                 }
            }
        } else if (lyrics.isNotEmpty() && activeIndex >= 0 && previousIndex == -1) {
             // Initial load scroll
             val viewportHeight = listState.layoutInfo.viewportSize.height
             val targetY = (viewportHeight * focusRatio).toInt()
             listState.scrollToItem(activeIndex, -targetY)
        }
        
        previousIndex = activeIndex
    }

    Box(modifier = modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            itemsIndexed(
                items = lyrics,
                key = { _, entry -> entry.startMs },
                contentType = { _, _ -> "appleLyricLine" }
            ) { index, entry ->
                val isActive = index == activeIndex
                val isPast = index < activeIndex
                
                // Animate visual properties
                val targetScale = 1.0f // Unified scale for active and inactive
                val scale by animateFloatAsState(targetScale, animationSpec = tween(400))
                
                val targetBlur = if (isActive) 0.dp else 1.5.dp // Slight blur for inactive?
                // Blur is expensive on Android < 12 (RenderEffect). 
                // Maybe just alpha/color is enough. Apple Music has blur on background but text is usually sharp, just dim.
                // Let's skip blur for performance unless requested. User said "Transparency/Color transition".
                
                val targetAlpha = if (isActive) 1f else 0.55f
                val alpha by animateFloatAsState(targetAlpha, animationSpec = tween(400))

                val targetColor = if (isActive) activeColor else inactiveColor
                // We animate color manually or via animateColorAsState
                val color by animateColorAsState(targetColor, animationSpec = tween(400))
                
                val targetFontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold
                val fontSize = if (isLandscape) 22.sp else 24.sp
                
                // Add shadow for active lyric
                val shadow = if (isActive) {
                     Shadow(
                         color = Color.Black.copy(alpha = 0.6f),
                         offset = androidx.compose.ui.geometry.Offset(0f, 2f),
                         blurRadius = 4f
                     )
                } else null

                // Get the wave offset
                // We need to observe the Animatable.
                // Note: itemOffsets is a SnapshotStateMap, so accessing it is state-read.
                // But the Animatable inside is stable. We need to read `.value`.
                val offsetAnim = itemOffsets[index]
                val translationY = offsetAnim?.value ?: 0f

                Text(
                    text = entry.text,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 24.dp)
                        .graphicsLayer {
                            this.translationY = translationY
                            this.scaleX = scale
                            this.scaleY = scale
                            this.alpha = alpha
                        }
                        .clickable { 
                            onSeekTo(entry.startMs) 
                            onOpenLyrics()
                        },
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = targetFontWeight,
                        fontSize = fontSize,
                        textAlign = TextAlign.Center,
                        lineHeight = if (isLandscape) 32.sp else 36.sp,
                        shadow = shadow
                    ),
                    color = color
                )
            }
        }
    }
}
