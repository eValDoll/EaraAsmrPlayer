@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.asmr.player.ui.library.prepared

import android.content.res.Configuration
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.EmojiCompatStatus
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Constraints
import com.asmr.player.ui.library.albumGridMetadataTextWidth
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.asmr.player.translation.LocalPageTranslation
import com.asmr.player.translation.PageTranslationSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** itemAtLazyIndex 只读取已加载数据，并为 header/loading 返回 null。 */
@Composable
internal fun PreparedAlbumParagraphs(
    listState: LazyListState,
    active: Boolean,
    dataKey: Any?,
    itemAtLazyIndex: (Int) -> PreparedAlbumParagraphItem?,
    content: @Composable () -> Unit,
) = PreparedAlbumParagraphs(
    scrollState = listState,
    viewport = {
        val layout = listState.layoutInfo
        AlbumParagraphViewport(
            first = layout.visibleItemsInfo.firstOrNull()?.index ?: -1,
            last = layout.visibleItemsInfo.lastOrNull()?.index ?: -1,
            total = layout.totalItemsCount,
        ) to when {
            listState.lastScrolledBackward -> false
            listState.lastScrolledForward -> true
            else -> null
        }
    },
    active = active,
    dataKey = dataKey,
    itemAtLazyIndex = itemAtLazyIndex,
    content = content,
)

@Composable
internal fun PreparedAlbumParagraphs(
    gridState: LazyStaggeredGridState,
    horizontal: Boolean,
    active: Boolean,
    dataKey: Any?,
    itemAtLazyIndex: (Int) -> PreparedAlbumParagraphItem?,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    PreparedAlbumParagraphs(
        scrollState = gridState,
        viewport = {
            val layout = gridState.layoutInfo
            val items = layout.visibleItemsInfo
            // 整行页脚比栏位更宽；逐像素滚动时只读尺寸，不读取或合并专辑数据。
            val cellWidth = items.minOfOrNull { it.size.width }
            AlbumParagraphViewport(
                first = items.minOfOrNull { it.index } ?: -1,
                last = items.maxOfOrNull { it.index } ?: -1,
                total = layout.totalItemsCount,
                textMaxWidth = if (horizontal || cellWidth == null) Constraints.Infinity else {
                    albumGridMetadataTextWidth(cellWidth, density)
                },
            ) to when {
                gridState.lastScrolledBackward -> false
                gridState.lastScrolledForward -> true
                else -> null
            }
        },
        active = active,
        dataKey = dataKey,
        itemAtLazyIndex = itemAtLazyIndex,
        content = content,
    )
}

@Composable
private fun PreparedAlbumParagraphs(
    scrollState: Any,
    viewport: () -> Pair<AlbumParagraphViewport, Boolean?>,
    active: Boolean,
    dataKey: Any?,
    itemAtLazyIndex: (Int) -> PreparedAlbumParagraphItem?,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val configuration = Configuration(LocalConfiguration.current)
    val parentFontResolver = LocalFontFamilyResolver.current
    val session = remember(context, configuration, parentFontResolver) {
        PreparedParagraphSession.create(context)
    }
    DisposableEffect(session) { onDispose { session.close() } }
    LaunchedEffect(session) {
        snapshotFlow { EmojiCompatStatus.fontLoaded.value }
            .drop(1)
            .collect { session.invalidatePreparedFonts() }
    }
    val scope = rememberCoroutineScope()
    val translationSession = LocalPageTranslation.current
    val controller = remember(session, translationSession, scope) {
        AlbumParagraphAheadWindow(
            scope = scope,
            fontFamilyResolver = session.fontFamilyResolver,
            translations = { sources ->
                snapshotFlow { cachedPreparedTagTranslations(translationSession, sources) }
                    .flowOn(Dispatchers.Main.immediate)
            },
            publish = session::setAheadWindow,
            activateSession = session::setActive,
        )
    }
    DisposableEffect(controller) { onDispose { controller.close() } }
    val environment = currentAlbumParagraphEnvironment()
    val latestItemAtIndex = rememberUpdatedState(itemAtLazyIndex)
    val latestActive = rememberUpdatedState(active)
    val latestViewport = rememberUpdatedState(viewport)
    val scrollDirection = remember(scrollState) { AlbumParagraphScrollDirection() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(controller, lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            controller.setActive(latestActive.value && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            controller.setActive(false)
        }
    }
    SideEffect { controller.setActive(active && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    LaunchedEffect(controller, scrollState, active, dataKey, environment, lifecycle) {
        if (!active) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            snapshotFlow { latestViewport.value() }
                .map { (viewport, direction) -> viewport to scrollDirection.resolve(direction) }
                .distinctUntilChanged()
                .collect { (viewport, forward) ->
                    controller.submit(
                        viewport, forward,
                        environment.copy(textConstraints = Constraints(maxWidth = viewport.textMaxWidth)),
                        latestItemAtIndex.value,
                    )
                }
        }
    }
    CompositionLocalProvider(
        LocalPreparedParagraphSession provides session,
        LocalFontFamilyResolver provides session.fontFamilyResolver,
        content = content,
    )
}

/** 零位移及 effect 重启都保留上次方向，不通过逐像素 offset 推动预排版。 */
internal class AlbumParagraphScrollDirection {
    private var forward = true

    fun resolve(lastNonZeroDirection: Boolean?): Boolean {
        if (lastNonZeroDirection != null) forward = lastNonZeroDirection
        return forward
    }
}

@Composable
private fun currentAlbumParagraphEnvironment(): AlbumParagraphEnvironment {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val labelSmall = MaterialTheme.typography.labelSmall
    return remember(labelSmall, density.density, density.fontScale, layoutDirection) {
        AlbumParagraphEnvironment(
            cvStyle = PreparedLayoutStyle(labelSmall.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)),
            tagStyle = PreparedLayoutStyle(labelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium)),
            density = density.density,
            fontScale = density.fontScale,
            layoutDirection = layoutDirection,
        )
    }
}

/** 与 translatedPageText 相同的最终选择；字符串解析已在后台完成，这里只读有限内存项。 */
internal fun cachedPreparedTagTranslations(
    session: PageTranslationSession?,
    sources: List<PreparedTagSource>,
): Map<String, String> {
    if (session == null || !session.enabled) return emptyMap()
    val target = session.settings.target
    return buildMap {
        for (source in sources) {
            if (!source.translatable) continue
            val translated = session.translations[source.source]
                ?: session.repository.cached(source.source, target)
            if (translated != null) put(source.original, translated + source.suffix)
        }
    }
}
