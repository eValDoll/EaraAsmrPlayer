package com.asmr.player.ui.library.prepared

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import com.asmr.player.domain.model.Album
import com.asmr.player.translation.pageTranslationText
import com.asmr.player.translation.shouldTranslatePageText
import com.asmr.player.ui.common.parseCvNames
import java.io.Closeable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

internal data class AlbumParagraphEnvironment(
    val cvStyle: PreparedLayoutStyle,
    val tagStyle: PreparedLayoutStyle,
    val density: Float,
    val fontScale: Float,
    val layoutDirection: LayoutDirection,
)

internal data class PreparedAlbumParagraphItem(val key: Any, val album: Album)

internal data class AlbumParagraphViewport(val first: Int, val last: Int, val total: Int)

internal data class PreparedTagSource(
    val original: String,
    val source: String,
    val suffix: String,
    val translatable: Boolean,
)

/** 只持有有限专辑引用；后台解析与翻译内存查询不触发分页或翻译请求。 */
internal class AlbumParagraphAheadWindow(
    scope: CoroutineScope,
    private val fontFamilyResolver: FontFamily.Resolver,
    private val translations: (List<PreparedTagSource>) -> Flow<Map<String, String>>,
    private val publish: (List<PreparedParagraphRequest>) -> Unit,
    private val activateSession: (Boolean) -> Unit,
    workerDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : Closeable {
    private val gate = Any()
    private val events = Channel<Window?>(Channel.CONFLATED)
    private var generation = 0L
    private var active = false
    private var activationInitialized = false
    private var closed = false
    private val processor = scope.launch(workerDispatcher) {
        events.receiveAsFlow().collectLatest { window ->
            if (window == null) return@collectLatest
            val labels = parseLabels(window.items)
            val sources = labels.mapNotNull { it.tag }.distinct()
            translations(sources).collectLatest { translated ->
                val requests = labels.map { label ->
                    currentCoroutineContext().ensureActive()
                    val tag = label.tag
                    PreparedParagraphRequest(
                        occurrence = label.occurrence,
                        text = if (tag == null) label.text else "#" + (translated[tag.original] ?: tag.original),
                        layoutStyle = if (tag == null) window.environment.cvStyle else window.environment.tagStyle,
                        fontFamilyResolver = fontFamilyResolver,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = true,
                        maxLines = 1,
                        minLines = 1,
                        density = window.environment.density,
                        fontScale = window.environment.fontScale,
                        layoutDirection = window.environment.layoutDirection,
                        // 横向 Scrollable Row 内的文字不使用 viewport 宽度；节点仍核对真实约束。
                        constraints = Constraints(),
                    )
                }
                synchronized(gate) {
                    if (active && !closed && generation == window.generation) publish(requests)
                }
            }
        }
    }

    fun submit(
        viewport: AlbumParagraphViewport,
        scrollingForward: Boolean,
        environment: AlbumParagraphEnvironment,
        itemAtLazyIndex: (Int) -> PreparedAlbumParagraphItem?,
    ) {
        val epoch = synchronized(gate) {
            if (!active || closed) return
            ++generation
        }
        val items = ArrayList<PreparedAlbumParagraphItem>(AheadRows + ReverseRows)
        if (viewport.first >= 0 && viewport.last >= viewport.first) {
            val primaryStep = if (scrollingForward) 1 else -1
            val primaryStart = if (scrollingForward) viewport.last.toLong() + 1 else viewport.first.toLong() - 1
            val reverseStart = if (scrollingForward) viewport.first.toLong() - 1 else viewport.last.toLong() + 1
            fun append(start: Long, step: Int, count: Int) {
                repeat(count) { offset ->
                    val index = start + step.toLong() * offset
                    if (index >= 0 && index < viewport.total) {
                        itemAtLazyIndex(index.toInt())?.let(items::add)
                    }
                }
            }
            append(primaryStart, primaryStep, AheadRows)
            append(reverseStart, -primaryStep, ReverseRows)
        }
        synchronized(gate) {
            if (active && !closed && generation == epoch) {
                events.trySend(Window(epoch, items, environment))
            }
        }
    }

    fun setActive(value: Boolean) {
        synchronized(gate) {
            if (closed || activationInitialized && active == value) return
            activationInitialized = true
            active = value
            generation++
            events.trySend(null)
            activateSession(value)
        }
    }

    override fun close() {
        synchronized(gate) {
            if (closed) return
            closed = true
            active = false
            generation++
            activateSession(false)
        }
        processor.cancel()
        events.close()
    }

    private suspend fun parseLabels(items: List<PreparedAlbumParagraphItem>): List<Label> {
        val labels = ArrayList<Label>(MaxLabels)
        for (item in items) {
            val album = item.album
            currentCoroutineContext().ensureActive()
            if (album.cv.length <= MaxEntryUtf16Units) {
                for ((index, cv) in parseCvNames(album.cv).withIndex()) {
                    if (labels.size == MaxLabels) return labels
                    labels.add(Label(PreparedParagraphOccurrence(item.key, PreparedParagraphLabel.Cv, index), cv))
                }
            }
            val seen = HashSet<String>()
            var tagIndex = 0
            for (raw in album.tags.take(MaxRawTagsPerAlbum)) {
                if (labels.size == MaxLabels) return labels
                // Stop rather than shift later occurrence indices relative to the UI normalization.
                if (raw.length > MaxEntryUtf16Units) break
                val tag = raw.trim()
                if (tag.isBlank() || !seen.add(tag)) continue
                val original = tag.removePrefix("#")
                val parts = pageTranslationText(original, fileName = false)
                labels.add(
                    Label(
                        occurrence = PreparedParagraphOccurrence(item.key, PreparedParagraphLabel.Tag, tagIndex++),
                        text = tag,
                        tag = PreparedTagSource(original, parts.source, parts.suffix, shouldTranslatePageText(parts.source)),
                    )
                )
            }
        }
        return labels
    }

    private data class Label(val occurrence: PreparedParagraphOccurrence, val text: String, val tag: PreparedTagSource? = null)
    private data class Window(
        val generation: Long,
        val items: List<PreparedAlbumParagraphItem>,
        val environment: AlbumParagraphEnvironment,
    )

    private companion object {
        const val AheadRows = 12
        const val ReverseRows = 2
        const val MaxLabels = 128
        const val MaxRawTagsPerAlbum = 16
        const val MaxEntryUtf16Units = 2_048
    }
}
