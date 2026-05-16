package com.asmr.player.ui.common

import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.util.MessageManager
import java.io.File
import kotlinx.coroutines.launch

internal const val IMAGE_PREVIEW_DIALOG_TAG = "image_preview_dialog"
internal const val IMAGE_PREVIEW_CLOSE_TAG = "image_preview_close"
internal const val IMAGE_PREVIEW_OUTSIDE_TAG = "image_preview_outside"
internal const val IMAGE_PREVIEW_PAGER_TAG = "image_preview_pager"
internal const val IMAGE_PREVIEW_COUNT_TAG = "image_preview_count"
internal const val IMAGE_PREVIEW_PREV_TAG = "image_preview_prev"
internal const val IMAGE_PREVIEW_NEXT_TAG = "image_preview_next"
internal const val IMAGE_PREVIEW_OPEN_EXTERNAL_TAG = "image_preview_open_external"

internal data class ImagePreviewLayoutSpec(
    val widthFraction: Float = 0.92f,
    val maxWidthDp: Int = 760,
    val heightFraction: Float = 0.66f,
    val minHeightDp: Int = 260,
    val maxHeightDp: Int = 680,
    val imageViewportPaddingDp: Int = 6,
    val toolbarVerticalPaddingDp: Int = 8,
    val footerVerticalPaddingDp: Int = 8
)

internal val DefaultImagePreviewLayoutSpec = ImagePreviewLayoutSpec()

internal data class ImagePreviewItem(
    val key: String,
    val title: String,
    val imageModel: Any? = null,
    val openPathOrUrl: String,
    val prepareImage: (suspend () -> ImagePreviewPreparedItem?)? = null
)

internal data class ImagePreviewPreparedItem(
    val imageModel: Any,
    val openPathOrUrl: String
)

internal data class ImagePreviewRequest(
    val items: List<ImagePreviewItem>,
    val initialIndex: Int = 0
)

internal fun ImagePreviewRequest.normalized(): ImagePreviewRequest? {
    val normalizedItems = items.filter { item ->
        item.key.isNotBlank() && item.openPathOrUrl.isNotBlank()
    }
    if (normalizedItems.isEmpty()) return null
    val index = initialIndex.coerceIn(0, normalizedItems.lastIndex)
    return copy(items = normalizedItems, initialIndex = index)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ImagePreviewDialog(
    request: ImagePreviewRequest,
    messageManager: MessageManager,
    onDismiss: () -> Unit,
    pageContent: @Composable ((
        item: ImagePreviewItem,
        state: ImagePreviewTransformState,
        onStateChange: (ImagePreviewTransformState) -> Unit
    ) -> Unit) = { item, state, onStateChange ->
        ImagePreviewPage(item = item, state = state, onStateChange = onStateChange)
    }
) {
    val normalizedRequest = remember(request) { request.normalized() } ?: return
    val items = normalizedRequest.items
    val context = LocalContext.current
    val colorScheme = AsmrTheme.colorScheme
    val layoutSpec = DefaultImagePreviewLayoutSpec
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = normalizedRequest.initialIndex,
        pageCount = { items.size }
    )
    val pageTransforms = remember(items) { mutableStateMapOf<String, ImagePreviewTransformState>() }
    val resolvedItems = remember(items) { mutableStateMapOf<String, ImagePreviewPreparedItem>() }
    val currentItem = items.getOrElse(pagerState.currentPage) { items.first() }
    val currentTransform = pageTransforms.getOrPut(currentItem.key) { ImagePreviewTransformState() }
    val canNavigate = items.size > 1
    val allowPaging = currentTransform.isAtRest

    fun openCurrentWithOtherApp() {
        val path = (resolvedItems[currentItem.key]?.openPathOrUrl ?: currentItem.openPathOrUrl).trim()
        if (path.isBlank()) {
            messageManager.showError("无法打开：路径为空")
            return
        }

        runCatching {
            val uri = when {
                path.startsWith("content://", ignoreCase = true) -> Uri.parse(path)
                path.startsWith("http://", ignoreCase = true) || path.startsWith("https://", ignoreCase = true) -> Uri.parse(path)
                else -> {
                    val file = File(path)
                    if (!file.exists()) throw java.io.FileNotFoundException(path)
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                }
            }
            val mimeType = runCatching { context.contentResolver.getType(uri) }.getOrNull()
                ?: path.substringAfterLast('.', "").lowercase().takeIf { it.isNotBlank() }?.let { ext ->
                    MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                }
                ?: "image/*"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "打开图片"))
        }.onFailure { throwable ->
            when (throwable) {
                is android.content.ActivityNotFoundException -> messageManager.showInfo("未找到可打开的应用")
                is java.io.FileNotFoundException -> messageManager.showError("文件不存在")
                else -> messageManager.showError("无法打开该图片")
            }
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        pageTransforms[currentItem.key] = ImagePreviewTransformState()
        pageTransforms.keys.toList().forEach { key ->
            if (key != currentItem.key) {
                pageTransforms[key] = ImagePreviewTransformState()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag(IMAGE_PREVIEW_OUTSIDE_TAG)
                .background(Color.Black.copy(alpha = 0.74f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(layoutSpec.widthFraction)
                    .widthIn(max = layoutSpec.maxWidthDp.dp)
                    .fillMaxHeight(layoutSpec.heightFraction)
                    .heightIn(min = layoutSpec.minHeightDp.dp, max = layoutSpec.maxHeightDp.dp)
                    .testTag(IMAGE_PREVIEW_DIALOG_TAG),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = {}
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = layoutSpec.toolbarVerticalPaddingDp.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = currentItem.title.ifBlank { currentItem.openPathOrUrl.substringAfterLast('/').substringAfterLast('\\') },
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            color = colorScheme.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(
                            onClick = ::openCurrentWithOtherApp,
                            modifier = Modifier.testTag(IMAGE_PREVIEW_OPEN_EXTERNAL_TAG)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "打开")
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag(IMAGE_PREVIEW_CLOSE_TAG)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "关闭")
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clipToBounds()
                            .background(Color(0xFF13161A)),
                        contentAlignment = Alignment.Center
                    ) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier
                                .fillMaxSize()
                                .clipToBounds()
                                .testTag(IMAGE_PREVIEW_PAGER_TAG),
                            beyondBoundsPageCount = 0,
                            userScrollEnabled = canNavigate && allowPaging,
                            key = { index -> items[index].key }
                        ) { page ->
                            val item = items[page]
                            val transformState = pageTransforms.getOrPut(item.key) { ImagePreviewTransformState() }
                            ImagePreviewPageHost(
                                item = item,
                                cachedPrepared = resolvedItems[item.key],
                                onPrepared = { prepared -> resolvedItems[item.key] = prepared },
                                state = transformState,
                                onStateChange = { pageTransforms[item.key] = it },
                                pageContent = pageContent
                            )
                        }

                        if (canNavigate && allowPaging) {
                            PreviewNavButton(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(start = 12.dp)
                                    .testTag(IMAGE_PREVIEW_PREV_TAG),
                                onClick = {
                                    if (pagerState.currentPage > 0) {
                                        pageTransforms[currentItem.key] = ImagePreviewTransformState()
                                        scope.launch {
                                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                        }
                                    }
                                },
                                icon = Icons.Default.ChevronLeft,
                                enabled = pagerState.currentPage > 0
                            )
                            PreviewNavButton(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 12.dp)
                                    .testTag(IMAGE_PREVIEW_NEXT_TAG),
                                onClick = {
                                    if (pagerState.currentPage < items.lastIndex) {
                                        pageTransforms[currentItem.key] = ImagePreviewTransformState()
                                        scope.launch {
                                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                        }
                                    }
                                },
                                icon = Icons.Default.ChevronRight,
                                enabled = pagerState.currentPage < items.lastIndex
                            )
                        }
                    }

                    if (canNavigate) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        Text(
                            text = "${pagerState.currentPage + 1} / ${items.size}",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = layoutSpec.footerVerticalPaddingDp.dp)
                                .testTag(IMAGE_PREVIEW_COUNT_TAG),
                            style = MaterialTheme.typography.labelLarge,
                            color = colorScheme.textSecondary
                        )
                    }
                }
            }
        }
    }
}

internal data class ImagePreviewTransformState(
    val scale: Float = 1f,
    val offset: Offset = Offset.Zero
) {
    val isAtRest: Boolean
        get() = scale <= 1.01f && kotlin.math.abs(offset.x) < 0.5f && kotlin.math.abs(offset.y) < 0.5f
}

@Composable
private fun ImagePreviewPageHost(
    item: ImagePreviewItem,
    cachedPrepared: ImagePreviewPreparedItem?,
    onPrepared: (ImagePreviewPreparedItem) -> Unit,
    state: ImagePreviewTransformState,
    onStateChange: (ImagePreviewTransformState) -> Unit,
    pageContent: @Composable (
        item: ImagePreviewItem,
        state: ImagePreviewTransformState,
        onStateChange: (ImagePreviewTransformState) -> Unit
    ) -> Unit
) {
    val prepared by produceState(
        initialValue = cachedPrepared ?: item.imageModel?.let { model ->
            ImagePreviewPreparedItem(imageModel = model, openPathOrUrl = item.openPathOrUrl)
        },
        key1 = item.key,
        key2 = cachedPrepared
    ) {
        if (value != null) return@produceState
        value = item.prepareImage?.invoke()
    }

    LaunchedEffect(item.key, prepared) {
        prepared?.let(onPrepared)
    }

    val displayItem = remember(item, prepared) {
        prepared?.let { resolved ->
            item.copy(
                imageModel = resolved.imageModel,
                openPathOrUrl = resolved.openPathOrUrl,
                prepareImage = null
            )
        } ?: item.copy(prepareImage = null)
    }

    pageContent(displayItem, state, onStateChange)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImagePreviewPage(
    item: ImagePreviewItem,
    state: ImagePreviewTransformState,
    onStateChange: (ImagePreviewTransformState) -> Unit
) {
    var scale by rememberSaveable(item.key) { mutableFloatStateOf(1f) }
    var offsetX by rememberSaveable(item.key) { mutableFloatStateOf(0f) }
    var offsetY by rememberSaveable(item.key) { mutableFloatStateOf(0f) }

    LaunchedEffect(item.key, state) {
        scale = state.scale
        offsetX = state.offset.x
        offsetY = state.offset.y
    }

    fun publish(newScale: Float, newOffset: Offset) {
        onStateChange(ImagePreviewTransformState(scale = newScale, offset = newOffset))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(DefaultImagePreviewLayoutSpec.imageViewportPaddingDp.dp)
            .clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        if (item.imageModel == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                AsmrShimmerPlaceholder(modifier = Modifier.fillMaxSize(), cornerRadius = 0)
            }
            return
        }

        AsmrAsyncImage(
            model = item.imageModel,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(item.key) {
                    detectTapGestures(
                        onDoubleTap = {
                            val nextScale = if (scale > 1f) 1f else 2f
                            val nextOffset = Offset.Zero
                            scale = nextScale
                            offsetX = nextOffset.x
                            offsetY = nextOffset.y
                            publish(nextScale, nextOffset)
                        }
                    )
                }
                .pointerInput(item.key) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val nextScale = (scale * zoom).coerceIn(1f, 5f)
                        val nextOffset = if (nextScale > 1f) {
                            Offset(offsetX + pan.x, offsetY + pan.y)
                        } else {
                            Offset.Zero
                        }
                        scale = nextScale
                        offsetX = nextOffset.x
                        offsetY = nextOffset.y
                        publish(nextScale, nextOffset)
                    }
                }
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                ),
            placeholderCornerRadius = 0,
            placeholder = { modifier ->
                PreviewImageFallback(
                    modifier = modifier,
                    title = item.title,
                    failed = true
                )
            },
            loading = { modifier ->
                Box(
                    modifier = modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    AsmrShimmerPlaceholder(modifier = Modifier.fillMaxSize(), cornerRadius = 0)
                }
            }
        )
    }
}

@Composable
private fun PreviewImageFallback(
    modifier: Modifier,
    title: String,
    failed: Boolean
) {
    val colorScheme = AsmrTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.ImageNotSupported,
                contentDescription = null,
                tint = colorScheme.textTertiary,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = if (failed) "图片加载失败" else title,
                color = colorScheme.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}

@Composable
private fun PreviewNavButton(
    modifier: Modifier,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = Color.Black.copy(alpha = if (enabled) 0.48f else 0.24f)
    ) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(icon, contentDescription = null, tint = Color.White)
        }
    }
}
