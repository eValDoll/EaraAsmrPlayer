@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.asmr.player.ui.library.prepared

import android.content.Context
import android.os.Trace
import androidx.compose.foundation.text.modifiers.ParagraphLayoutCache
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import java.io.Closeable
import java.util.ArrayDeque
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

internal enum class PreparedParagraphLabel { Cv, Tag }

/**
 * The same lazy item key and normalized label position used by the UI. Callers supply only
 * immutable String/Long/Int keys from the existing page key factories, never mutable item objects.
 */
@Immutable
internal data class PreparedParagraphOccurrence(
    val itemKey: Any,
    val label: PreparedParagraphLabel,
    val index: Int,
)

internal class PreparedParagraphLease internal constructor(internal val request: PreparedParagraphRequest)

internal data class PreparedParagraphAcquisition(
    val cache: ParagraphLayoutCache?,
    val lease: PreparedParagraphLease?,
)

/** Exact text-node inputs after caller padding; this is not the row viewport width. */
internal data class PreparedParagraphRequest(
    val occurrence: PreparedParagraphOccurrence,
    val text: String,
    val layoutStyle: PreparedLayoutStyle,
    val fontFamilyResolver: FontFamily.Resolver,
    val overflow: TextOverflow,
    val softWrap: Boolean,
    val maxLines: Int,
    val minLines: Int,
    val density: Float,
    val fontScale: Float,
    val layoutDirection: LayoutDirection,
    val constraints: Constraints,
)

/** Uses the same layout-affecting equality/hash as Foundation; color/brush/shadow stay in draw. */
internal class PreparedLayoutStyle(val value: TextStyle) {
    override fun equals(other: Any?): Boolean =
        other is PreparedLayoutStyle && value.hasSameLayoutAffectingAttributes(other.value)

    override fun hashCode(): Int = value.hashCodeLayoutAffectingAttributes()
}

/**
 * One page session, one worker and one bounded ahead window. Completed caches are transferred,
 * never shared: once acquire() removes an entry the UI node alone owns its Paragraph/TextPaint.
 * Supports only minLines == 1, which is the CV/Tags contract. Foundation 1.7.8's
 * MinLinesConstrainer has a mutable static cache and must not be entered by this worker.
 */
internal class PreparedParagraphSession internal constructor(
    val fontFamilyResolver: FontFamily.Resolver,
    private val executor: ExecutorService,
    private val maxEntries: Int = 128,
    private val maxUtf16Units: Int = 16_384,
    private val maxEntryUtf16Units: Int = 2_048,
) : Closeable {
    init {
        require(maxEntries > 0 && maxUtf16Units > 0 && maxEntryUtf16Units > 0)
    }

    private val lock = Any()
    private var active = true
    private var closed = false
    private var epoch = 0L
    private var draining = false
    private var running: PreparedParagraphRequest? = null
    private var runningEpoch = -1L
    private var wanted = linkedSetOf<PreparedParagraphRequest>()
    private val pending = ArrayDeque<PreparedParagraphRequest>()
    private val ready = LinkedHashMap<PreparedParagraphRequest, ParagraphLayoutCache>()
    // Value-only leases do not retain nodes or their layout trees. Eviction only loses an
    // optimization: the node still owns its cache and will continue to render normally.
    private val leases = LinkedHashMap<PreparedParagraphLease, Unit>()
    private val held = HashMap<PreparedParagraphRequest, Int>()
    private var leaseUtf16Units = 0

    private val prepared = AtomicLong()
    private val adopted = AtomicLong()
    private val misses = AtomicLong()
    private val syncLayouts = AtomicLong()
    private val staleAdoptions = AtomicLong()
    private val failures = AtomicLong()
    private val rejected = AtomicLong()

    /** Call from the page's data/translation/viewport pipeline, before composing those rows. */
    fun setAheadWindow(requests: List<PreparedParagraphRequest>) {
        val next = linkedSetOf<PreparedParagraphRequest>()
        var utf16Units = 0
        for (request in requests) {
            if (!eligible(request) || request.text.length > maxEntryUtf16Units) {
                rejected.incrementAndGet()
                continue
            }
            if (request in next) continue
            if (next.size == maxEntries || utf16Units + request.text.length > maxUtf16Units) break
            next.add(request)
            utf16Units += request.text.length
        }
        synchronized(lock) {
            if (closed || !active) return
            wanted = next
            ready.keys.retainAll(wanted)
            pending.clear()
            next.forEach { request ->
                if (request !in held && request !in ready &&
                    !(request == running && runningEpoch == epoch)
                ) pending.addLast(request)
            }
            startDrainIfNeeded()
        }
    }

    fun setActive(value: Boolean) {
        synchronized(lock) {
            if (closed || active == value) return
            active = value
            epoch++
            wanted.clear()
            pending.clear()
            ready.clear()
        }
    }

    /** Rebuild only the bounded, still-wanted pool; adopted caches remain owned by their UI nodes. */
    fun invalidatePreparedFonts() {
        synchronized(lock) {
            if (closed || !active) return
            epoch++
            ready.clear()
            pending.clear()
            wanted.forEach { request ->
                if (request !in held) pending.addLast(request)
            }
            startDrainIfNeeded()
        }
    }

    /** A cold miss also owns the synchronous layout; do not prepare that occurrence again. */
    internal fun acquire(request: PreparedParagraphRequest): PreparedParagraphAcquisition = synchronized(lock) {
        val usable = !closed && eligible(request)
        val cache = if (active && usable) ready.remove(request) else null
        val lease = if (usable && request.text.length <= maxEntryUtf16Units &&
            request.text.length <= maxUtf16Units
        ) {
            while (leases.size >= maxEntries || leaseUtf16Units + request.text.length > maxUtf16Units) {
                forgetLease(leases.keys.first())
            }
            PreparedParagraphLease(request).also {
                leases[it] = Unit
                held[request] = (held[request] ?: 0) + 1
                leaseUtf16Units += request.text.length
            }
        } else null
        pending.removeFirstOccurrence(request)
        if (cache === null) misses.incrementAndGet() else adopted.incrementAndGet()
        PreparedParagraphAcquisition(cache, lease)
    }

    internal fun release(lease: PreparedParagraphLease) {
        synchronized(lock) {
            if (!forgetLease(lease)) return
            val request = lease.request
            if (!closed && active && request in wanted && request !in held &&
                request !in ready && !(request == running && runningEpoch == epoch) &&
                request !in pending
            ) {
                pending.addLast(request)
                startDrainIfNeeded()
            }
        }
    }

    private fun forgetLease(lease: PreparedParagraphLease): Boolean {
        if (leases.remove(lease) == null) return false
        val request = lease.request
        val count = held.getValue(request)
        if (count == 1) held.remove(request) else held[request] = count - 1
        leaseUtf16Units -= request.text.length
        return true
    }

    /** Caller holds lock; only one drain runnable can be queued or running. */
    private fun startDrainIfNeeded() {
        if (!draining && pending.isNotEmpty()) {
            draining = true
            executor.execute(::drain)
        }
    }

    /** Called after the original cache checks fonts and constraints on the UI thread. */
    internal fun recordMeasure(adoptedPreparedCache: Boolean, laidOutSynchronously: Boolean) {
        if (laidOutSynchronously) {
            syncLayouts.incrementAndGet()
            if (adoptedPreparedCache) staleAdoptions.incrementAndGet()
        }
    }

    fun stats(): Stats = synchronized(lock) {
        Stats(
            prepared.get(), adopted.get(), misses.get(), syncLayouts.get(),
            staleAdoptions.get(), failures.get(), rejected.get(),
            ready.size, pending.size, running !== null, leases.size, leaseUtf16Units,
        )
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            active = false
            epoch++
            wanted.clear()
            pending.clear()
            ready.clear()
            leases.clear()
            held.clear()
            leaseUtf16Units = 0
        }
        // Native layout may finish despite interruption; epoch/closed gates discard its result.
        executor.shutdownNow()
    }

    private fun eligible(request: PreparedParagraphRequest): Boolean =
        request.fontFamilyResolver === fontFamilyResolver && request.minLines == 1 && request.maxLines > 0

    private fun drain() {
        while (true) {
            val work = synchronized(lock) {
                if (closed || !active || pending.isEmpty()) {
                    draining = false
                    running = null
                    return
                }
                pending.removeFirst().let { request ->
                    running = request
                    runningEpoch = epoch
                    request to epoch
                }
            }
            val request = work.first
            val tracePreparation = PreparedParagraphTrace.enabled
            if (tracePreparation) Trace.beginSection("Eara:prepareParagraph")
            val cache = try {
                // Uses the exact 1.7.8 ParagraphIntrinsics -> Paragraph path, not MultiParagraph.
                ParagraphLayoutCache(
                    request.text,
                    request.layoutStyle.value,
                    request.fontFamilyResolver,
                    request.overflow,
                    request.softWrap,
                    request.maxLines,
                    request.minLines,
                ).apply {
                    density = Density(request.density, request.fontScale)
                    layoutWithConstraints(request.constraints, request.layoutDirection)
                }
            } catch (_: RuntimeException) {
                failures.incrementAndGet()
                null
            } finally {
                if (tracePreparation) Trace.endSection()
            }
            synchronized(lock) {
                running = null
                if (cache !== null && !closed && active && epoch == work.second &&
                    request in wanted && request !in held && request !in ready
                ) {
                    ready[request] = cache
                    prepared.incrementAndGet()
                }
            }
        }
    }

    data class Stats(
        val prepared: Long,
        val adopted: Long,
        val misses: Long,
        val syncLayouts: Long,
        val staleAdoptions: Long,
        val failures: Long,
        val rejected: Long,
        val ready: Int,
        val pending: Int,
        val running: Boolean,
        val leased: Int,
        val leasedUtf16Units: Int,
    )

    companion object {
        fun create(
            context: Context,
            maxEntries: Int = 128,
            maxUtf16Units: Int = 16_384,
            maxEntryUtf16Units: Int = 2_048,
        ): PreparedParagraphSession {
            return PreparedParagraphSession(
                createFontFamilyResolver(context.applicationContext),
                Executors.newSingleThreadExecutor { command ->
                    Thread(command, "eara-paragraph-prepare").apply {
                        isDaemon = true
                        priority = Thread.NORM_PRIORITY - 1
                    }
                },
                maxEntries,
                maxUtf16Units,
                maxEntryUtf16Units,
            )
        }
    }
}
