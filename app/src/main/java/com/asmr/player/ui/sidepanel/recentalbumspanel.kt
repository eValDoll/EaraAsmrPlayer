package com.asmr.player.ui.sidepanel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.domain.model.Album
import com.asmr.player.ui.common.AsmrAsyncImage
import com.asmr.player.ui.player.PlayerViewModel
import com.asmr.player.ui.theme.AsmrTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.runtime.snapshots.SnapshotStateMap
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.ComponentActivity
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.asmr.player.ui.common.findActivity

@Composable
fun RecentAlbumsPanel(
    onOpenAlbum: (AlbumEntity) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecentAlbumsPanelViewModel? = null
) {
    val colorScheme = AsmrTheme.colorScheme
    val context = LocalContext.current
    val activityOwner = remember(context) { context.findActivity() as? ComponentActivity }
    val owner = activityOwner ?: LocalViewModelStoreOwner.current
    val resolvedViewModel: RecentAlbumsPanelViewModel = viewModel ?: if (owner != null) {
        hiltViewModel(owner)
    } else {
        hiltViewModel()
    }
    val playerViewModel: PlayerViewModel = if (owner != null) {
        hiltViewModel(owner)
    } else {
        hiltViewModel()
    }
    val baseItems by resolvedViewModel.items.collectAsState()
    val optimisticOrderIds = remember { mutableStateListOf<Long>() }
    LaunchedEffect(baseItems) {
        val ids = baseItems.map { it.album.id }.toSet()
        optimisticOrderIds.retainAll(ids)
        while (optimisticOrderIds.size > 50) optimisticOrderIds.removeLast()
    }

    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        color = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "最近听过",
                    color = colorScheme.textPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "最近播放",
                    color = colorScheme.textTertiary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            RecentAlbumsList(
                items = baseItems,
                preferredOrderIds = optimisticOrderIds.toList(),
                onOpenAlbum = onOpenAlbum,
                onResumePlay = { item ->
                    optimisticOrderIds.removeAll { it == item.album.id }
                    optimisticOrderIds.add(0, item.album.id)
                    while (optimisticOrderIds.size > 50) optimisticOrderIds.removeLast()
                    val a = albumDomain(item.album)
                    val resume = item.resume
                    playerViewModel.playAlbumResume(
                        album = a,
                        resumeMediaId = resume?.mediaId,
                        startPositionMs = resume?.positionMs ?: 0L
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}

@Composable
internal fun RecentAlbumsList(
    items: List<RecentAlbumUiItem>,
    preferredOrderIds: List<Long>,
    onOpenAlbum: (AlbumEntity) -> Unit,
    onResumePlay: (RecentAlbumUiItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = 4.dp
    val initialTarget = remember(items, preferredOrderIds) {
        val byId = items.associateBy { it.album.id }
        val mergedIds = preferredOrderIds.filter { it in byId.keys } + items.map { it.album.id }
        mergedIds.distinct().mapNotNull { byId[it] }.take(6)
    }
    val order = remember {
        mutableStateListOf<Long>().apply {
            addAll(initialTarget.map { it.album.id })
        }
    }
    val itemById: SnapshotStateMap<Long, RecentAlbumUiItem> = remember {
        mutableStateMapOf<Long, RecentAlbumUiItem>().apply {
            initialTarget.forEach { put(it.album.id, it) }
        }
    }
    val scope = rememberCoroutineScope()
    val removalJobs = remember { mutableMapOf<Long, Job>() }
    val yAnims = remember { mutableMapOf<Long, Animatable<Float, AnimationVector1D>>() }
    val alphaAnims = remember { mutableMapOf<Long, Animatable<Float, AnimationVector1D>>() }
    val scaleAnims = remember { mutableMapOf<Long, Animatable<Float, AnimationVector1D>>() }
    var animationsReady by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = modifier) {
        if (items.isEmpty()) {
            Text(
                text = "暂无数据",
                color = AsmrTheme.colorScheme.textSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            return@BoxWithConstraints
        }
        val density = LocalDensity.current
        val minItem = 56.dp
        val maxVisible = remember(items, maxHeight) {
            val maxByMinHeight = ((maxHeight + spacing) / (minItem + spacing)).toInt().coerceAtLeast(1)
            maxByMinHeight.coerceAtMost(items.size)
        }
        val target = remember(items, preferredOrderIds, maxVisible) {
            val byId = items.associateBy { it.album.id }
            val mergedIds = preferredOrderIds.filter { it in byId.keys } + items.map { it.album.id }
            mergedIds.distinct().mapNotNull { byId[it] }.take(maxVisible)
        }
        val count = target.size
        val targetIds = target.map { it.album.id }.toSet()
        val featuredId = target.firstOrNull()?.album?.id
        val featuredWeight = 2.5f
        val normalWeight = 1f
        val totalWeight = if (count == 1) 1f else featuredWeight + (count - 1) * normalWeight
        val availableHeight = maxHeight - spacing * (count - 1)
        val maxItem = 60.dp
        val featuredMax = 160.dp

        val targetOrderIds = remember(target) { target.map { it.album.id } }
        LaunchedEffect(targetOrderIds, maxVisible) {
            target.forEach { itemById[it.album.id] = it }

            val exitingIds = order.filter { it !in targetIds }
            val newOrder = target.map { it.album.id } + exitingIds
            val distinctOrder = newOrder.distinct()
            if (order.toList() != distinctOrder) {
                order.clear()
                order.addAll(distinctOrder)
            }

            val currentIds = order.toSet()
            val removed = currentIds - targetIds

            removed.forEach { id ->
                if (removalJobs.containsKey(id)) return@forEach
                removalJobs[id] = scope.launch {
                    delay(220)
                    order.removeAll { it == id }
                    itemById.remove(id)
                    yAnims.remove(id)
                    alphaAnims.remove(id)
                    scaleAnims.remove(id)
                    removalJobs.remove(id)
                }
            }
            targetIds.forEach { id -> removalJobs.remove(id)?.cancel() }
        }

        val targetHeights = remember(targetOrderIds, maxHeight) {
            buildMap<Long, androidx.compose.ui.unit.Dp> {
                if (count <= 0) return@buildMap
                val ids = target.map { it.album.id }
                ids.forEach { id ->
                    val featured = id == featuredId
                    val w = if (count == 1) 1f else if (featured) featuredWeight else normalWeight
                    val h = (availableHeight * (w / totalWeight)).coerceIn(minItem, if (featured) featuredMax else maxItem)
                    put(id, h)
                }
            }
        }

        val targetYById = remember(targetIds, targetHeights, maxHeight, order.toList()) {
            val map = mutableMapOf<Long, androidx.compose.ui.unit.Dp>()
            var y = 0.dp
            val orderedTargets = order.filter { it in targetIds }
            orderedTargets.forEachIndexed { idx, id ->
                map[id] = y
                val h = targetHeights[id] ?: minItem
                y += h
                if (idx != orderedTargets.lastIndex) y += spacing
            }
            val afterTargets = y + if (orderedTargets.isNotEmpty()) spacing else 0.dp
            var exitIndex = 0
            order.filter { it !in targetIds }.forEach { id ->
                map[id] = afterTargets + (minItem + spacing) * exitIndex
                exitIndex++
            }
            map
        }

        val targetYPxById = remember(targetYById) {
            targetYById.mapValues { (_, yDp) ->
                with(density) { yDp.toPx() }
            }
        }

        LaunchedEffect(targetYPxById, targetIds, featuredId, order.toList()) {
            val enterFromPx = with(density) { (-24).dp.toPx() }
            val animate = animationsReady

            for (id in order) {
                val yTarget = targetYPxById[id] ?: 0f
                val isNew = !yAnims.containsKey(id)
                val yAnim = yAnims.getOrPut(id) { Animatable(yTarget) }
                val aAnim = alphaAnims.getOrPut(id) { Animatable(if (id in targetIds) 1f else 0f) }
                val sAnim = scaleAnims.getOrPut(id) { Animatable(if (id in targetIds) 1f else 0.98f) }

                val isTarget = id in targetIds
                if (!animate) {
                    yAnim.snapTo(yTarget)
                    aAnim.snapTo(if (isTarget) 1f else 0f)
                    sAnim.snapTo(if (isTarget) 1f else 0.98f)
                } else {
                    if (isNew && isTarget) {
                        yAnim.snapTo(yTarget + enterFromPx)
                        aAnim.snapTo(0f)
                        sAnim.snapTo(0.98f)
                    }
                    launch {
                        yAnim.animateTo(yTarget, animationSpec = tween(durationMillis = 240))
                    }
                    launch {
                        aAnim.animateTo(if (isTarget) 1f else 0f, animationSpec = tween(durationMillis = 180))
                    }
                    launch {
                        sAnim.animateTo(if (isTarget) 1f else 0.98f, animationSpec = tween(durationMillis = 180))
                    }
                }
            }
            if (!animationsReady) animationsReady = true
        }

        Box(modifier = Modifier.fillMaxSize()) {
            for (id in order) {
                key(id) {
                    val item = itemById[id]
                    if (item != null) {
                        val isTarget = id in targetIds
                        val featured = isTarget && id == featuredId
                        val baseHeight = targetHeights[id] ?: minItem
                        val yPx = if (animationsReady) {
                            yAnims[id]?.value ?: (targetYPxById[id] ?: 0f)
                        } else {
                            targetYPxById[id] ?: 0f
                        }
                        val alpha = if (animationsReady) {
                            alphaAnims[id]?.value ?: (if (isTarget) 1f else 0f)
                        } else {
                            if (isTarget) 1f else 0f
                        }
                        val scale = if (animationsReady) {
                            scaleAnims[id]?.value ?: (if (isTarget) 1f else 0.98f)
                        } else {
                            if (isTarget) 1f else 0.98f
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(if (id == featuredId) 1f else 0f)
                                .graphicsLayer {
                                    translationY = yPx
                                    this.alpha = alpha
                                    scaleX = scale
                                    scaleY = scale
                                }
                        ) {
                            RecentAlbumRow(
                                item = item,
                                featured = featured,
                                height = baseHeight,
                                onClick = { if (isTarget) onOpenAlbum(item.album) },
                                onPlay = { if (isTarget) onResumePlay(item) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentAlbumRow(
    item: RecentAlbumUiItem,
    featured: Boolean,
    height: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    onPlay: () -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    val glassShape = androidx.compose.foundation.shape.RoundedCornerShape(if (featured) 18.dp else 16.dp)
    
    val blurRadius = 2.dp
    val blurModifier = remember(blurRadius) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Modifier.graphicsLayer {
                val blurPx = blurRadius.toPx()
                renderEffect = RenderEffect
                    .createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
                    .asComposeRenderEffect()
            }
        } else {
            Modifier.blur(blurRadius)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(glassShape)
            .clickable(onClick = onClick)
            .height(height)
    ) {
        val contentPadH = if (featured) 18.dp else 14.dp
        val contentPadV = if (featured) 14.dp else 10.dp
        val coverModel = albumThumb(item.album)

        if (featured) {
            // 1. 底层清晰图
            AsmrAsyncImage(
                model = coverModel,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                placeholderCornerRadius = 16
            )

            // 2. 顶层模糊图（带渐变遮罩，只在底部显示模糊）
            AsmrAsyncImage(
                model = coverModel,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .then(blurModifier)
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.6f to Color.Transparent,
                                1f to Color.Black
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    },
                contentScale = ContentScale.Crop,
                placeholderCornerRadius = 16
            )

            // 3. 渐变变暗遮罩（底部更暗以突出文字）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.1f),
                                Color.Black.copy(alpha = 0.6f)
                            )
                        )
                    )
            )
        } else {
            // 非首个item：整体模糊 + 遮罩
            AsmrAsyncImage(
                model = coverModel,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .then(blurModifier),
                contentScale = ContentScale.Crop,
                placeholderCornerRadius = 16
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(
                    width = 1.dp,
                    color = colorScheme.onSurface.copy(alpha = 0.06f),
                    shape = glassShape
                )
        )

        val textColor = Color.White

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = contentPadH, vertical = contentPadV),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val playButtonShape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            Box(
                modifier = Modifier
                    .size(if (featured) 40.dp else 34.dp)
                    .background(Color.White.copy(alpha = 0.14f), shape = playButtonShape)
                    .clickable(onClick = onPlay)
                    .clip(playButtonShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = textColor
                )
            }
            Spacer(modifier = Modifier.size(if (featured) 12.dp else 10.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = if (featured) Arrangement.Bottom else Arrangement.Center
            ) {
                Text(
                    text = item.album.title,
                    color = textColor,
                    style = (if (featured) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.labelSmall)
                        .copy(fontWeight = FontWeight.SemiBold),
                    maxLines = if (featured) 2 else 1,
                    overflow = TextOverflow.Ellipsis
                )
                val resume = item.resume
                if (resume?.trackTitle != null) {
                    val subColor = textColor.copy(alpha = 0.82f)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Text(
                            text = "上次听到",
                            color = subColor,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = resume.trackTitle,
                            color = subColor,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Text(
                            text = "，${formatRecentProgressPosition(resume.positionMs)}处",
                            color = subColor,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

internal fun formatRecentProgressPosition(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

private fun albumDomain(album: AlbumEntity): Album {
    return Album(
        id = album.id,
        title = album.title,
        path = album.path,
        localPath = album.localPath,
        downloadPath = album.downloadPath,
        circle = album.circle,
        cv = album.cv,
        coverUrl = album.coverUrl,
        coverPath = album.coverPath,
        coverThumbPath = album.coverThumbPath,
        workId = album.workId,
        rjCode = album.rjCode,
        description = album.description
    )
}

private fun albumThumb(album: AlbumEntity): String? {
    return album.coverThumbPath.takeIf { it.isNotBlank() }
        ?: album.coverPath.takeIf { it.isNotBlank() }
        ?: album.coverUrl.takeIf { it.isNotBlank() }
}
