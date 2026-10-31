@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.asmr.player.ui.library.prepared

import android.app.Application
import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.AndroidFont
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PreparedParagraphSessionTest {
    @Test
    fun repeatedTextInDifferentOccurrences_transfersDistinctCachesWithCompletedParagraphsOnlyOnce() {
        val executor = QueuedParagraphExecutor()
        val resolver = createFontFamilyResolver(RuntimeEnvironment.getApplication())
        PreparedParagraphSession(resolver, executor).use { session ->
            val request = paragraphRequest(resolver)
            val other = request.copy(occurrence = request.occurrence.copy(itemKey = "other-album"))
            session.setAheadWindow(listOf(request, other))
            assertEquals(1, executor.queuedCount)
            executor.runNext()
            assertEquals(2, session.stats().ready)

            val first = requireNotNull(session.acquire(request).cache)
            val second = requireNotNull(session.acquire(other).cache)
            assertNotSame(first, second)
            assertNotNull(first.paragraph)
            assertNotSame(first.paragraph, second.paragraph)
            first.density = Density(request.density, request.fontScale)
            assertFalse(first.layoutWithConstraints(request.constraints, request.layoutDirection))
            assertNull(session.acquire(request).cache)
            assertEquals(2L, session.stats().adopted)
            assertEquals(0, session.stats().ready)
        }
    }

    @Test
    fun boundedWindow_rejectsUnsafeMinLinesAndDiscardsReplacedWork() {
        val executor = QueuedParagraphExecutor()
        val resolver = createFontFamilyResolver(RuntimeEnvironment.getApplication())
        PreparedParagraphSession(resolver, executor, maxEntries = 2).use { session ->
            val first = paragraphRequest(resolver, "first")
            val second = paragraphRequest(resolver, "second")
            session.setAheadWindow(listOf(first,
                first.copy(occurrence = first.occurrence.copy(index = 1)),
                first.copy(occurrence = first.occurrence.copy(index = 2))))
            assertEquals(2, session.stats().pending)
            session.setAheadWindow(listOf(second.copy(minLines = 2, maxLines = 2), second))
            assertEquals(1, executor.queuedCount)
            executor.runNext()
            assertNull(session.acquire(first).cache)
            assertNotNull(session.acquire(second).cache)
            assertEquals(1L, session.stats().rejected)
            assertEquals(1L, session.stats().prepared)
        }
    }

    @Test
    fun pauseDuringActualFontResolution_discardsTheInFlightParagraphAndCanResume() {
        val executor = QueuedParagraphExecutor()
        val resolver = createFontFamilyResolver(RuntimeEnvironment.getApplication())
        val font = CallbackFont()
        PreparedParagraphSession(resolver, executor).use { session ->
            val request = paragraphRequest(resolver, fontFamily = FontFamily(font))
            font.onLoad = { session.setActive(false) }
            session.setAheadWindow(listOf(request))
            executor.runNext()
            assertTrue(font.wasLoaded)
            val paused = session.acquire(request)
            assertNull(paused.cache)
            paused.lease?.let(session::release)
            assertEquals(0, session.stats().ready)
            assertEquals(0L, session.stats().prepared)
            assertFalse(session.stats().running)

            session.setActive(true)
            session.setAheadWindow(listOf(request))
            executor.runNext()
            assertNotNull(session.acquire(request).cache)
        }
    }

    @Test
    fun replaceWindowDuringFontResolution_doesNotPublishOldText() {
        val executor = QueuedParagraphExecutor()
        val resolver = createFontFamilyResolver(RuntimeEnvironment.getApplication())
        val font = CallbackFont()
        PreparedParagraphSession(resolver, executor).use { session ->
            val old = paragraphRequest(resolver, "old", FontFamily(font))
            val replacement = paragraphRequest(resolver, "replacement")
            font.onLoad = { session.setAheadWindow(listOf(replacement)) }
            session.setAheadWindow(listOf(old))
            executor.runNext()
            assertTrue(font.wasLoaded)
            assertNull(session.acquire(old).cache)
            assertNotNull(session.acquire(replacement).cache)
            assertEquals(1L, session.stats().prepared)
        }
    }

    @Test
    fun closeDuringFontResolution_dropsResultAndNeverSchedulesAgain() {
        val executor = QueuedParagraphExecutor()
        val resolver = createFontFamilyResolver(RuntimeEnvironment.getApplication())
        val font = CallbackFont()
        val session = PreparedParagraphSession(resolver, executor)
        val request = paragraphRequest(resolver, fontFamily = FontFamily(font))
        font.onLoad = { session.close() }
        session.setAheadWindow(listOf(request))
        executor.runNext()
        assertTrue(font.wasLoaded)
        assertTrue(executor.isShutdown())
        assertNull(session.acquire(request).cache)
        assertEquals(0L, session.stats().prepared)
        session.setActive(true)
        session.setAheadWindow(listOf(request))
        assertEquals(0, executor.queuedCount)
    }

    @Test
    fun fontInvalidation_rebuildsUnconsumedWindowWithoutTouchingAdoptedParagraph() {
        val executor = QueuedParagraphExecutor()
        val resolver = createFontFamilyResolver(RuntimeEnvironment.getApplication())
        val session = PreparedParagraphSession(resolver, executor)
        session.use {
            val request = paragraphRequest(resolver)
            val other = request.copy(occurrence = request.occurrence.copy(index = 1))
            val third = request.copy(occurrence = request.occurrence.copy(index = 2))
            session.setAheadWindow(listOf(request, other, third))
            executor.runNext()
            val adopted = requireNotNull(session.acquire(request).cache)
            val paragraph = adopted.paragraph
            assertEquals(2, session.stats().ready)

            session.invalidatePreparedFonts()
            assertEquals(0, session.stats().ready)
            assertEquals(2, session.stats().pending)
            assertEquals(1, executor.queuedCount)
            assertTrue(adopted.paragraph === paragraph)
            assertFalse(adopted.layoutWithConstraints(request.constraints, request.layoutDirection))

            executor.runNext()
            assertEquals(2, session.stats().ready)
            assertEquals(5L, session.stats().prepared)
            assertNotSame(adopted, session.acquire(other).cache)
            session.setActive(false)
            session.invalidatePreparedFonts()
            assertEquals(0, executor.queuedCount)
            assertEquals(0, session.stats().pending)
        }
        session.invalidatePreparedFonts()
        assertEquals(0, executor.queuedCount)
    }

    @Test
    fun fontInvalidationDuringResolution_dropsOldEpochBeforePreparingCurrentWindow() {
        val executor = QueuedParagraphExecutor()
        val resolver = createFontFamilyResolver(RuntimeEnvironment.getApplication())
        val firstFont = CallbackFont()
        val secondFont = CallbackFont()
        PreparedParagraphSession(resolver, executor).use { session ->
            val first = paragraphRequest(resolver, "first", FontFamily(firstFont))
            val second = paragraphRequest(resolver, "second", FontFamily(secondFont))
            firstFont.onLoad = {
                session.setAheadWindow(listOf(second, first))
                session.invalidatePreparedFonts()
                assertEquals(2, session.stats().pending)
                assertEquals(0, executor.queuedCount)
            }
            secondFont.onLoad = {
                // The old first paragraph has finished, but must not have entered ready.
                assertEquals(0, session.stats().ready)
                assertEquals(0L, session.stats().prepared)
            }
            session.setAheadWindow(listOf(first, second))
            executor.runNext()
            assertTrue(firstFont.wasLoaded)
            assertTrue(secondFont.wasLoaded)
            assertEquals(2L, session.stats().prepared)
            assertNotNull(session.acquire(first).cache)
            assertNotNull(session.acquire(second).cache)
            assertNull(session.acquire(first).cache)
            assertFalse(session.stats().running)
            assertEquals(0, executor.queuedCount)
        }
    }
    @Test
    fun movingAndReorderedWindow_doesNotPrepareTheSameHeldOccurrenceAgain() {
        val executor = QueuedParagraphExecutor()
        val resolver = createFontFamilyResolver(RuntimeEnvironment.getApplication())
        PreparedParagraphSession(resolver, executor).use { session ->
            val first = paragraphRequest(resolver, "同名声优")
            val second = first.copy(occurrence = first.occurrence.copy(itemKey = "album-b"))
            val entering = first.copy(occurrence = first.occurrence.copy(itemKey = "album-c"))
            session.setAheadWindow(listOf(first, second))
            executor.runNext()
            val held = session.acquire(first)
            assertNotNull(held.cache)

            // A prefetched row remains composed while its item moves within the ahead window.
            session.setAheadWindow(listOf(second, first, entering))
            executor.runNext()
            assertEquals(3L, session.stats().prepared)
            assertEquals(2, session.stats().ready)
            assertNotNull(session.acquire(second).cache)
            assertNotNull(session.acquire(entering).cache)
            repeat(3) { session.setAheadWindow(listOf(entering, first, second)) }
            assertEquals(0, executor.queuedCount)
            assertEquals(0, session.stats().pending)
            assertEquals(3L, session.stats().prepared)

            session.release(requireNotNull(held.lease))
            executor.runNext()
            val replacement = requireNotNull(session.acquire(first).cache)
            assertNotSame(held.cache, replacement)
            assertEquals(4L, session.stats().prepared)
        }
    }

    @Test
    fun coldMissOwnsItsSynchronousOccurrence_withoutBlockingSameTextElsewhere() {
        val executor = QueuedParagraphExecutor()
        val resolver = createFontFamilyResolver(RuntimeEnvironment.getApplication())
        PreparedParagraphSession(resolver, executor).use { session ->
            val first = paragraphRequest(resolver, "#重复标签")
            val other = first.copy(occurrence = first.occurrence.copy(index = 1))
            val cold = session.acquire(first)
            assertNull(cold.cache)
            assertNotNull(cold.lease)
            session.setAheadWindow(listOf(first, other))
            executor.runNext()
            assertEquals(1L, session.stats().prepared)
            assertNotNull(session.acquire(other).cache)
            assertEquals(0, session.stats().ready)
            session.setAheadWindow(listOf(other, first))
            session.invalidatePreparedFonts()
            assertEquals(0, executor.queuedCount)
            assertEquals(0, session.stats().pending)
        }
    }

    @Test
    fun heldOccurrencesSurviveInactivePage_withoutRestartingWorkerUntilReleased() {
        val executor = QueuedParagraphExecutor()
        val resolver = createFontFamilyResolver(RuntimeEnvironment.getApplication())
        PreparedParagraphSession(resolver, executor).use { session ->
            val request = paragraphRequest(resolver)
            val owned = session.acquire(request)
            session.setActive(false)
            session.setAheadWindow(listOf(request))
            session.invalidatePreparedFonts()
            assertEquals(0, executor.queuedCount)
            session.setActive(true)
            session.setAheadWindow(listOf(request))
            assertEquals(0, executor.queuedCount)
            session.release(requireNotNull(owned.lease))
            assertEquals(1, executor.queuedCount)
            executor.runNext()
            assertNotNull(session.acquire(request).cache)
        }
    }

    @Test
    fun multipleOwnersOfSameOccurrence_releaseOnlyAfterLastOwner() {
        val executor = QueuedParagraphExecutor()
        val resolver = createFontFamilyResolver(RuntimeEnvironment.getApplication())
        PreparedParagraphSession(resolver, executor).use { session ->
            val request = paragraphRequest(resolver)
            val first = session.acquire(request)
            val second = session.acquire(request)
            session.setAheadWindow(listOf(request, request))
            session.release(requireNotNull(first.lease))
            assertEquals(0, executor.queuedCount)
            session.release(requireNotNull(second.lease))
            assertEquals(1, executor.queuedCount)
            executor.runNext()
            assertEquals(1L, session.stats().prepared)
        }
    }

    @Test
    fun leaseBudgetIsBoundedAndEvictionOnlyLosesPreparationSuppression() {
        val executor = QueuedParagraphExecutor()
        val resolver = createFontFamilyResolver(RuntimeEnvironment.getApplication())
        PreparedParagraphSession(resolver, executor, maxEntries = 2, maxUtf16Units = 8).use { session ->
            val request = paragraphRequest(resolver, "1234")
            val first = session.acquire(request)
            val second = session.acquire(request.copy(occurrence = request.occurrence.copy(index = 1)))
            val third = session.acquire(request.copy(occurrence = request.occurrence.copy(index = 2)))
            assertEquals(2, session.stats().leased)
            assertEquals(8, session.stats().leasedUtf16Units)
            session.release(requireNotNull(first.lease)) // Already evicted; must not decrement another owner.
            assertEquals(2, session.stats().leased)
            session.setAheadWindow(listOf(request))
            executor.runNext()
            assertNotNull(session.acquire(request).cache)
            session.release(requireNotNull(second.lease))
            session.release(requireNotNull(third.lease))
            assertTrue(session.stats().leased <= 2)
            assertTrue(session.stats().leasedUtf16Units <= 8)
        }
    }

}

internal fun paragraphRequest(
    resolver: FontFamily.Resolver,
    text: String = "繁體 日本語 العربية CV",
    fontFamily: FontFamily? = null,
) = PreparedParagraphRequest(
    occurrence = PreparedParagraphOccurrence("fixture", PreparedParagraphLabel.Cv, 0),
    text = text,
    layoutStyle = PreparedLayoutStyle(TextStyle(fontSize = 13.sp, fontFamily = fontFamily)),
    fontFamilyResolver = resolver,
    overflow = TextOverflow.Ellipsis,
    softWrap = true,
    maxLines = 1,
    minLines = 1,
    density = 1f,
    fontScale = 1f,
    layoutDirection = LayoutDirection.Ltr,
    constraints = Constraints(),
)

internal class QueuedParagraphExecutor : AbstractExecutorService() {
    private val commands = ArrayDeque<Runnable>()
    private var stopped = false
    val queuedCount get() = commands.size

    fun runNext() = commands.removeFirst().run()
    override fun execute(command: Runnable) {
        if (stopped) throw RejectedExecutionException()
        commands.addLast(command)
    }
    override fun shutdown() { stopped = true }
    override fun shutdownNow(): MutableList<Runnable> {
        stopped = true
        return commands.toMutableList().also { commands.clear() }
    }
    override fun isShutdown() = stopped
    override fun isTerminated() = stopped && commands.isEmpty()
    override fun awaitTermination(timeout: Long, unit: TimeUnit) = isTerminated()
}

private class CallbackFont : AndroidFont(
    FontLoadingStrategy.Blocking,
    Loader,
    FontVariation.Settings(),
) {
    override val weight = FontWeight.Normal
    override val style = FontStyle.Normal
    var onLoad: (() -> Unit)? = null
    var wasLoaded = false
        private set

    private object Loader : AndroidFont.TypefaceLoader {
        override fun loadBlocking(context: Context, font: AndroidFont): Typeface {
            val callbackFont = font as CallbackFont
            callbackFont.wasLoaded = true
            callbackFont.onLoad?.also { callbackFont.onLoad = null }?.invoke()
            return Typeface.DEFAULT
        }

        override suspend fun awaitLoad(context: Context, font: AndroidFont): Typeface =
            error("Only the real blocking font path is used by this fixture")
    }
}
