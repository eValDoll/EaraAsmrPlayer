@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.asmr.player.ui.library.prepared

import android.os.Build
import android.os.Trace
import androidx.compose.foundation.text.modifiers.ParagraphLayoutCache
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.AndroidParagraph
import androidx.compose.ui.text.Paragraph
import com.asmr.player.BuildConfig

internal val LocalPreparedParagraphSession = staticCompositionLocalOf<PreparedParagraphSession?> { null }

internal object PreparedParagraphTrace {
    val enabled: Boolean
        get() = BuildConfig.BUILD_TYPE == "benchmark" &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && Trace.isEnabled()

    fun event(node: Any, action: String) {
        Trace.beginSection("Eara:paragraph:$action:${System.identityHashCode(node)}")
        Trace.endSection()
    }

    fun fontsAreStale(cache: ParagraphLayoutCache): Boolean =
        (cache.paragraph as? AndroidParagraph)?.paragraphIntrinsics?.hasStaleResolvedFonts == true
}

/** Allocated only while a benchmark trace is active; events never contain text or its hash. */
internal class PreparedParagraphDiagnostics(private val node: Any) {
    private var previousRequest: PreparedParagraphRequest? = null
    private var previousParagraph: Paragraph? = null
    private val changes = linkedSetOf<String>()

    fun changed(reason: String) {
        changes.add(reason)
    }

    fun measured(
        request: PreparedParagraphRequest?,
        cache: ParagraphLayoutCache,
        hadCache: Boolean,
        hadParagraph: Boolean,
        fontsWereStale: Boolean,
        densityChanged: Boolean,
        adopted: Boolean,
        laidOutSynchronously: Boolean,
    ) {
        if (laidOutSynchronously) {
            val previous = previousRequest
            val reason = when {
                fontsWereStale -> "font-stale"
                densityChanged -> "density"
                "text" in changes -> "text"
                "style" in changes -> "style"
                "resolver" in changes -> "resolver"
                "options" in changes -> "options"
                "substitution" in changes -> "substitution"
                previous != null && request != null && previous.constraints != request.constraints -> "constraints"
                previous != null && request != null && previous.layoutDirection != request.layoutDirection -> "direction"
                !hadCache && !adopted -> "new-cache"
                previous != null && previous == request && previousParagraph !== null -> "same-input"
                !hadParagraph && !adopted -> "empty-cache"
                adopted -> "adopted-stale"
                else -> "existing-unclassified"
            }
            PreparedParagraphTrace.event(node, "sync-$reason")
        } else {
            PreparedParagraphTrace.event(node, if (adopted) "adopted-hit" else "retained-hit")
        }
        previousRequest = request
        previousParagraph = cache.paragraph
        changes.clear()
    }
}
