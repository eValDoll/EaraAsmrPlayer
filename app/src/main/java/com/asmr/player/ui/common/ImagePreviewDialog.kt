package com.asmr.player.ui.common

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ImageNotSupported
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.asmr.player.AsmrApp
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.util.MessageManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal const val IMAGE_PREVIEW_DIALOG_TAG = "image_preview_dialog"
internal const val IMAGE_PREVIEW_CLOSE_TAG = "image_preview_close"
internal const val IMAGE_PREVIEW_PAGER_TAG = "image_preview_pager"
internal const val IMAGE_PREVIEW_COUNT_TAG = "image_preview_count"
internal const val IMAGE_PREVIEW_PREV_TAG = "image_preview_prev"
internal const val IMAGE_PREVIEW_NEXT_TAG = "image_preview_next"
internal const val IMAGE_PREVIEW_OPEN_EXTERNAL_TAG = "image_preview_open_external"
internal const val IMAGE_PREVIEW_SAVE_TO_GALLERY_TAG = "image_preview_save_to_gallery"
internal val ImagePreviewOverlayColor = Color(0xFF202124)

internal data class ImagePreviewLayoutSpec(
    val imageViewportPaddingDp: Int = 6,
    val toolbarVerticalPaddingDp: Int = 8,
    val footerVerticalPaddingDp: Int = 8
)

internal val DefaultImagePreviewLayoutSpec = ImagePreviewLayoutSpec()

@Suppress("DEPRECATION")
private fun Window.applyImagePreviewSystemBarStyle() {
    val overlayColor = ImagePreviewOverlayColor.toArgb()
    setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
    setBackgroundDrawable(ColorDrawable(overlayColor))
    decorView.setBackgroundColor(overlayColor)
    statusBarColor = overlayColor
    navigationBarColor = overlayColor
    WindowCompat.setDecorFitsSystemWindows(this, false)
    WindowCompat.getInsetsController(this, decorView).apply {
        isAppearanceLightStatusBars = false
        isAppearanceLightNavigationBars = false
    }
}

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
    val layoutSpec = DefaultImagePreviewLayoutSpec
    val scope = rememberCoroutineScope()
    val actionColors = IconButtonDefaults.iconButtonColors(
        containerColor = Color.White.copy(alpha = 0.10f),
        contentColor = Color.White,
        disabledContainerColor = Color.White.copy(alpha = 0.05f),
        disabledContentColor = Color.White.copy(alpha = 0.38f)
    )
    val pagerState = rememberPagerState(
        initialPage = normalizedRequest.initialIndex,
        pageCount = { items.size }
    )
    val pageTransforms = remember(items) { mutableStateMapOf<String, ImagePreviewTransformState>() }
    val resolvedItems = remember(items) { mutableStateMapOf<String, ImagePreviewPreparedItem>() }
    var openingExternalImageKey by remember { mutableStateOf<String?>(null) }
    var savingImageKey by remember { mutableStateOf<String?>(null) }
    var savedImageKey by remember { mutableStateOf<String?>(null) }
    var pendingSavePermissionRequest by remember { mutableStateOf<PreviewImageSaveRequest?>(null) }
    val currentItem = items.getOrElse(pagerState.currentPage) { items.first() }
    val currentPreparedItem = resolvedItems[currentItem.key]
    val currentTransform = pageTransforms.getOrPut(currentItem.key) { ImagePreviewTransformState() }
    val canNavigate = items.size > 1
    val allowPaging = currentTransform.isAtRest
    val isCurrentImagePreparing = currentPreparedItem == null &&
        currentItem.imageModel == null &&
        currentItem.prepareImage != null

    fun currentImageRequest(): PreviewImageSaveRequest {
        val prepared = resolvedItems[currentItem.key]
        return PreviewImageSaveRequest(
            key = currentItem.key,
            title = currentItem.title,
            imageModel = prepared?.imageModel ?: currentItem.imageModel,
            openPathOrUrl = prepared?.openPathOrUrl ?: currentItem.openPathOrUrl
        )
    }

    fun saveImage(request: PreviewImageSaveRequest) {
        if (savingImageKey != null) return
        savingImageKey = request.key
        scope.launch {
            try {
                val app = context.applicationContext as? AsmrApp
                    ?: error("Image client is unavailable")
                savePreviewImageToGallery(
                    context = context.applicationContext,
                    request = request,
                    httpClient = app.imageOkHttpClient
                )
                savingImageKey = null
                savedImageKey = request.key
                messageManager.showSuccess("已保存到相册")
                delay(1_500)
                if (savedImageKey == request.key) savedImageKey = null
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                messageManager.showError("保存到相册失败")
            } finally {
                savingImageKey = null
            }
        }
    }

    val savePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val pendingRequest = pendingSavePermissionRequest
        pendingSavePermissionRequest = null
        if (granted && pendingRequest != null) {
            saveImage(pendingRequest)
        } else if (!granted) {
            messageManager.showWarning("需要存储权限才能保存到相册")
        }
    }

    fun saveCurrentToGallery() {
        val saveRequest = currentImageRequest()
        val needsLegacyPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        if (needsLegacyPermission) {
            pendingSavePermissionRequest = saveRequest
            savePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            saveImage(saveRequest)
        }
    }

    fun openCurrentWithOtherApp() {
        if (openingExternalImageKey != null) return
        val openRequest = currentImageRequest()
        if (resolvePreviewImageSaveSource(openRequest).location.isBlank()) {
            messageManager.showError("无法打开：路径为空")
            return
        }

        openingExternalImageKey = openRequest.key
        scope.launch {
            try {
                val app = context.applicationContext as? AsmrApp
                    ?: error("Image client is unavailable")
                val prepared = preparePreviewImageForExternalOpen(
                    context = context.applicationContext,
                    request = openRequest,
                    httpClient = app.imageOkHttpClient
                )
                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(prepared.uri, prepared.mimeType)
                    clipData = ClipData.newRawUri("image", prepared.uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(viewIntent, "打开图片").apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(chooser)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: ActivityNotFoundException) {
                messageManager.showInfo("未找到可打开的应用")
            } catch (_: java.io.FileNotFoundException) {
                messageManager.showError("文件不存在")
            } catch (_: Exception) {
                messageManager.showError("无法打开该图片")
            } finally {
                openingExternalImageKey = null
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
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val dialogView = LocalView.current
        DisposableEffect(dialogView) {
            (dialogView.parent as? DialogWindowProvider)?.window?.applyImagePreviewSystemBarStyle()
            onDispose {}
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ImagePreviewOverlayColor)
                .testTag(IMAGE_PREVIEW_DIALOG_TAG)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
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
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(
                        onClick = ::openCurrentWithOtherApp,
                        enabled = openingExternalImageKey == null &&
                            savingImageKey == null &&
                            !isCurrentImagePreparing,
                        colors = actionColors,
                        modifier = Modifier.testTag(IMAGE_PREVIEW_OPEN_EXTERNAL_TAG)
                    ) {
                        if (openingExternalImageKey != null) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = "使用其他应用打开")
                        }
                    }
                    IconButton(
                        onClick = ::saveCurrentToGallery,
                        enabled = savingImageKey == null &&
                            openingExternalImageKey == null &&
                            savedImageKey != currentItem.key &&
                            !isCurrentImagePreparing,
                        colors = actionColors,
                        modifier = Modifier.testTag(IMAGE_PREVIEW_SAVE_TO_GALLERY_TAG)
                    ) {
                        if (savingImageKey != null) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else if (savedImageKey == currentItem.key) {
                            Icon(Icons.Rounded.Check, contentDescription = "已保存到相册")
                        } else {
                            Icon(Icons.Rounded.SaveAlt, contentDescription = "保存到相册")
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        colors = actionColors,
                        modifier = Modifier.testTag(IMAGE_PREVIEW_CLOSE_TAG)
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "关闭")
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clipToBounds(),
                    contentAlignment = Alignment.Center
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxSize()
                            .clipToBounds()
                            .testTag(IMAGE_PREVIEW_PAGER_TAG),
                        beyondViewportPageCount = 0,
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
                            icon = Icons.Rounded.ChevronLeft,
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
                            icon = Icons.Rounded.ChevronRight,
                            enabled = pagerState.currentPage < items.lastIndex
                        )
                    }
                }

                if (canNavigate) {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${items.size}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = layoutSpec.footerVerticalPaddingDp.dp)
                            .testTag(IMAGE_PREVIEW_COUNT_TAG),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.72f)
                    )
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
                AsmrImageLoadingPlaceholder(
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = 0,
                    indicatorSize = 40.dp
                )
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
                AsmrImageLoadingPlaceholder(
                    modifier = modifier.fillMaxSize(),
                    cornerRadius = 0,
                    indicatorSize = 40.dp
                )
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
                imageVector = Icons.Rounded.ImageNotSupported,
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
