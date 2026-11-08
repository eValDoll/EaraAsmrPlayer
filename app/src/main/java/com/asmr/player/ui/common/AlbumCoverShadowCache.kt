package com.asmr.player.ui.common

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

internal val LocalAlbumCoverShadowCache = staticCompositionLocalOf<AlbumCoverShadowCache?> { null }

@Composable
internal fun rememberAlbumCoverShadowCache(): AlbumCoverShadowCache? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !LocalView.current.isHardwareAccelerated) return null
    val graphicsContext = LocalGraphicsContext.current
    val cache = remember(graphicsContext) { AlbumCoverShadowCache(graphicsContext) }
    DisposableEffect(cache) { onDispose { cache.close() } }
    return cache
}

/** Each live draw node holds one lease; unused sizes never outlive their last row. */
internal class AlbumCoverShadowCache(private val graphicsContext: GraphicsContext) {
    internal data class Key(
        val size: IntSize,
        val density: Float,
        val fontScale: Float,
        val layoutDirection: LayoutDirection,
        val shape: Shape,
        val isDark: Boolean,
        val blurRadius: Dp,
    )

    internal class Source(
        val key: Key,
        val color: GraphicsLayer,
        val blur: GraphicsLayer,
        val outer: GraphicsLayer,
    ) {
        var references = 0
    }

    private val sources = mutableMapOf<Key, Source>()
    private var closed = false
    internal val liveSourceCount: Int get() = sources.size
    internal val liveReferenceCount: Int get() = sources.values.sumOf { it.references }

    internal fun acquire(key: Key): Source {
        check(!closed)
        val source = sources.getOrPut(key) { create(key) }
        source.references++
        return source
    }

    internal fun release(source: Source) {
        if (closed) return
        check(source.references > 0)
        if (--source.references == 0) {
            check(sources.remove(source.key) === source)
            releaseLayers(source)
        }
    }

    fun close() {
        if (closed) return
        closed = true
        sources.values.forEach(::releaseLayers)
        sources.clear()
    }

    private fun releaseLayers(source: Source) {
        graphicsContext.releaseGraphicsLayer(source.outer)
        graphicsContext.releaseGraphicsLayer(source.blur)
        graphicsContext.releaseGraphicsLayer(source.color)
    }

    private fun create(key: Key): Source {
        val recordingDensity = Density(key.density, key.fontScale)
        val radius = with(recordingDensity) { key.blurRadius.toPx() }
        val outset = with(recordingDensity) { (key.blurRadius * 3f + 6.dp).roundToPx() }
        val created = ArrayList<GraphicsLayer>(3)
        try {
            val color = graphicsContext.createGraphicsLayer().also(created::add)
            val blur = graphicsContext.createGraphicsLayer().also(created::add)
            val outer = graphicsContext.createGraphicsLayer().also(created::add)
            // Match the original modifier's three nodes, including alpha before blur.
            color.alpha = if (key.isDark) .38f else .54f
            color.scaleX = .93f
            color.scaleY = .93f
            color.translationX = with(recordingDensity) { (if (key.isDark) 4.dp else 5.dp).toPx() }
            color.translationY = with(recordingDensity) { (if (key.isDark) 5.dp else 6.dp).toPx() }
            color.compositingStrategy = CompositingStrategy.ModulateAlpha
            val outline = key.shape.createOutline(
                Size(key.size.width.toFloat(), key.size.height.toFloat()), key.layoutDirection, recordingDensity,
            )
            color.record(recordingDensity, key.layoutDirection, key.size) {
                drawOutline(outline, if (key.isDark) Color.White else Color.Black)
            }
            blur.topLeft = IntOffset(outset, outset)
            blur.renderEffect = BlurEffect(radius, radius, TileMode.Decal)
            blur.record(recordingDensity, key.layoutDirection, key.size) { drawLayer(color) }
            outer.topLeft = IntOffset(-outset, -outset)
            outer.compositingStrategy = CompositingStrategy.Offscreen
            outer.record(recordingDensity, key.layoutDirection,
                IntSize(key.size.width + outset * 2, key.size.height + outset * 2)) { drawLayer(blur) }
            return Source(key, color, blur, outer)
        } catch (error: Throwable) {
            for (index in created.indices.reversed()) {
                try {
                    graphicsContext.releaseGraphicsLayer(created[index])
                } catch (cleanupError: Throwable) {
                    error.addSuppressed(cleanupError)
                }
            }
            throw error
        }
    }
}

internal fun Modifier.sharedAlbumCoverShadow(
    cache: AlbumCoverShadowCache,
    isDark: Boolean,
    shape: Shape,
    resolvedBlurRadius: Dp,
): Modifier = this then AlbumCoverShadowElement(cache, isDark, shape, resolvedBlurRadius)

private data class AlbumCoverShadowElement(
    val cache: AlbumCoverShadowCache,
    val isDark: Boolean,
    val shape: Shape,
    val blurRadius: Dp,
) : ModifierNodeElement<AlbumCoverShadowNode>() {
    override fun create() = AlbumCoverShadowNode(cache, isDark, shape, blurRadius)
    override fun update(node: AlbumCoverShadowNode) = node.update(cache, isDark, shape, blurRadius)
    override fun InspectorInfo.inspectableProperties() { name = "sharedAlbumCoverShadow" }
}

private class AlbumCoverShadowNode(
    private var cache: AlbumCoverShadowCache,
    private var isDark: Boolean,
    private var shape: Shape,
    private var blurRadius: Dp,
) : Modifier.Node(), DrawModifierNode {
    private var source: AlbumCoverShadowCache.Source? = null

    fun update(cache: AlbumCoverShadowCache, isDark: Boolean, shape: Shape, blurRadius: Dp) {
        if (this.cache !== cache || this.isDark != isDark || this.shape != shape || this.blurRadius != blurRadius) {
            release()
            this.cache = cache
            this.isDark = isDark
            this.shape = shape
            this.blurRadius = blurRadius
        }
    }

    override fun ContentDrawScope.draw() {
        val recordingSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
        val current = source?.key
        if (current == null || current.size != recordingSize || current.density != density ||
            current.fontScale != fontScale || current.layoutDirection != layoutDirection) {
            release()
            source = cache.acquire(AlbumCoverShadowCache.Key(
                recordingSize, density, fontScale, layoutDirection, shape, isDark, blurRadius,
            ))
        }
        drawLayer(checkNotNull(source).outer)
        drawContent()
    }

    override fun onDetach() { release() }

    private fun release() {
        source?.let(cache::release)
        source = null
    }
}
