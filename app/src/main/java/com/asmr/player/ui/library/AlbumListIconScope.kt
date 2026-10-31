package com.asmr.player.ui.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlendModeColorFilter
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.LayoutDirection
import com.asmr.player.R

private val LocalAlbumListIconPainters = staticCompositionLocalOf<AlbumListIconPainters?> { null }

@Composable
internal fun AlbumListIconScope(content: @Composable () -> Unit) {
    val club = painterResource(R.drawable.ic_album_meta_club)
    val cv = painterResource(R.drawable.ic_album_meta_cv)
    val tags = painterResource(R.drawable.ic_album_meta_tags)
    val clubEntry = remember(club) { SharedAlbumIconPainter(club) }
    val cvEntry = remember(cv) { SharedAlbumIconPainter(cv) }
    val tagsEntry = remember(tags) { SharedAlbumIconPainter(tags) }
    val painters = remember(clubEntry, cvEntry, tagsEntry) {
        AlbumListIconPainters(clubEntry, cvEntry, tagsEntry)
    }
    CompositionLocalProvider(LocalAlbumListIconPainters provides painters, content = content)
}

@Composable
internal fun rememberAlbumListMetaIconPainter(iconRes: Int): Painter {
    val fallback = painterResource(iconRes)
    val shared = LocalAlbumListIconPainters.current?.forResource(iconRes) ?: return fallback
    return remember(shared, fallback) { AlbumListIconPainter(shared, fallback) }
}

private class AlbumListIconPainters(
    private val clubEntry: SharedAlbumIconPainter,
    private val cvEntry: SharedAlbumIconPainter,
    private val tagsEntry: SharedAlbumIconPainter,
) {

    fun forResource(iconRes: Int): SharedAlbumIconPainter? = when (iconRes) {
        R.drawable.ic_album_meta_club -> clubEntry
        R.drawable.ic_album_meta_cv -> cvEntry
        R.drawable.ic_album_meta_tags -> tagsEntry
        else -> null
    }
}

private class SharedAlbumIconPainter(private val painter: Painter) {
    private var drawSize: Size? = null
    private var drawLayoutDirection: LayoutDirection? = null

    fun forDraw(size: Size, layoutDirection: LayoutDirection, colorFilter: ColorFilter?): Painter? {
        // 不同尺寸或位图格式不能改写已被其他行 DisplayList 引用的缓存。
        if (colorFilter != null &&
            (colorFilter !is BlendModeColorFilter ||
                (colorFilter.blendMode != BlendMode.SrcIn && colorFilter.blendMode != BlendMode.SrcOver))
        ) return null
        if (drawSize == null) {
            drawSize = size
            drawLayoutDirection = layoutDirection
        }
        return painter.takeIf { drawSize == size && drawLayoutDirection == layoutDirection }
    }
}

private class AlbumListIconPainter(
    private val shared: SharedAlbumIconPainter,
    private val fallback: Painter,
) : Painter() {
    private var alpha = 1f
    private var colorFilter: ColorFilter? = null

    override val intrinsicSize: Size
        get() = fallback.intrinsicSize

    override fun applyAlpha(alpha: Float): Boolean {
        this.alpha = alpha
        return true
    }

    override fun applyColorFilter(colorFilter: ColorFilter?): Boolean {
        this.colorFilter = colorFilter
        return true
    }

    override fun DrawScope.onDraw() {
        val targetSize = size
        val targetAlpha = alpha
        val targetColorFilter = colorFilter
        val targetPainter = shared.forDraw(targetSize, layoutDirection, targetColorFilter) ?: fallback
        with(targetPainter) {
            draw(size = targetSize, alpha = targetAlpha, colorFilter = targetColorFilter)
        }
    }
}
