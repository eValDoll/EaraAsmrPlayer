package com.asmr.player.ui.library.prepared

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import com.asmr.player.domain.model.Album
import java.lang.reflect.Proxy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumParagraphAheadWindowTest {
    @Test
    fun reverseWindowSurvivesCollectorRestartAndZeroDelta() = runBlocking {
        val direction = AlbumParagraphScrollDirection()
        val lookedUp = mutableListOf<Int>()
        val published = Channel<List<PreparedParagraphRequest>>(Channel.UNLIMITED)
        val controller = AlbumParagraphAheadWindow(
            this, resolver, { flowOf(emptyMap()) }, { published.trySend(it) }, {}, Dispatchers.Unconfined,
        )
        try {
            controller.setActive(true)
            assertTrue(direction.resolve(null))
            assertFalse(direction.resolve(false))

            // 新 collector 没有非零滚动信号；数据更新和生命周期恢复不能把窗口翻转。
            repeat(2) {
                controller.submit(AlbumParagraphViewport(20, 23, 100), direction.resolve(null), environment) { index ->
                    lookedUp.add(index)
                    album(key = index, cv = "声优-$index")
                }
                withTimeout(5_000) { published.receive() }
                assertEquals((19 downTo 8).toList() + listOf(24, 25), lookedUp)
                lookedUp.clear()
            }

            controller.submit(AlbumParagraphViewport(20, 23, 100), direction.resolve(true), environment) { index ->
                lookedUp.add(index)
                album(key = index, cv = "声优-$index")
            }
            withTimeout(5_000) { published.receive() }
            assertEquals((24..35).toList() + listOf(19, 18), lookedUp)
            assertTrue(direction.resolve(null))
        } finally {
            controller.close()
        }
    }

    @Test
    fun onlyPeeksTwelveAheadAndTwoBehindAndSkipsNonAlbumItems() = runBlocking {
        val published = Channel<List<PreparedParagraphRequest>>(Channel.UNLIMITED)
        val lookedUp = mutableListOf<Int>()
        val controller = AlbumParagraphAheadWindow(
            this, resolver, { flowOf(emptyMap()) }, { published.trySend(it) }, {}, Dispatchers.Unconfined,
        )
        try {
            controller.setActive(true)
            controller.submit(AlbumParagraphViewport(5, 8, 1_000), true, environment) { index ->
                lookedUp.add(index)
                if (index == 11) null else album(key = index, cv = "声优-$index")
            }
            val forward = withTimeout(5_000) { published.receive() }
            assertEquals((9..20).toList() + listOf(4, 3), lookedUp)
            assertEquals(lookedUp.filter { it != 11 }.map { "声优-$it" }, forward.map { it.text })
            assertTrue(forward.all { it.constraints == Constraints() })
            assertTrue(forward.all { it.maxLines == 1 && it.minLines == 1 })
            assertSame(resolver, forward.first().fontFamilyResolver)

            lookedUp.clear()
            controller.submit(AlbumParagraphViewport(15, 18, 1_000), false, environment) { index ->
                lookedUp.add(index)
                album(key = index, cv = "声优-$index")
            }
            val reverse = withTimeout(5_000) { published.receive() }
            assertEquals((14 downTo 3).toList() + listOf(19, 20), lookedUp)
            assertEquals(lookedUp.map { "声优-$it" }, reverse.map { it.text })

            lookedUp.clear()
            controller.submit(AlbumParagraphViewport(0, 0, 2), false, environment) { index ->
                lookedUp.add(index)
                album(key = index, cv = "声优-$index")
            }
            withTimeout(5_000) { published.receive() }
            assertEquals(listOf(1), lookedUp)
        } finally {
            controller.close()
        }
    }

    @Test
    fun pauseResumeAndWindowReplacementCannotPublishOldInFlightWork() = runBlocking {
        val published = Channel<List<PreparedParagraphRequest>>(Channel.UNLIMITED)
        val started = Channel<Unit>(Channel.UNLIMITED)
        val release = CompletableDeferred<Unit>()
        val activations = mutableListOf<Boolean>()
        val controller = AlbumParagraphAheadWindow(
            this,
            resolver,
            translations = { sources ->
                flow {
                    if (sources.any { it.original == "旧标签" }) {
                        started.send(Unit)
                        // 模拟不能立即中断的后台工作；恢复页面不能接收旧 generation。
                        withContext(NonCancellable) { release.await() }
                    }
                    emit(emptyMap())
                }
            },
            publish = { published.trySend(it) },
            activateSession = { activations.add(it) },
            workerDispatcher = Dispatchers.Unconfined,
        )
        try {
            controller.setActive(true)
            controller.submit(AlbumParagraphViewport(0, 0, 2), true, environment) {
                album(tags = listOf("旧标签"))
            }
            withTimeout(5_000) { started.receive() }
            controller.setActive(false)
            var inactiveLookups = 0
            controller.submit(AlbumParagraphViewport(0, 0, 2), true, environment) {
                inactiveLookups++
                album()
            }
            assertEquals(0, inactiveLookups)
            controller.setActive(true)
            controller.submit(AlbumParagraphViewport(0, 0, 2), true, environment) {
                album(tags = listOf("新标签"))
            }
            release.complete(Unit)
            val result = withTimeout(5_000) { published.receive() }
            assertEquals(listOf("#新标签"), result.map { it.text })
            yield()
            assertTrue(published.tryReceive().isFailure)
            controller.close()
            controller.setActive(true)
            controller.submit(AlbumParagraphViewport(0, 0, 2), true, environment) { error("Closed controller peeked") }
            assertEquals(listOf(true, false, true, false), activations)
        } finally {
            release.complete(Unit)
            controller.close()
        }
    }

    @Test
    fun cachedTranslationUpdatesPreserveUiNormalizationAndDuplicateOccurrences() = runBlocking {
        val published = Channel<List<PreparedParagraphRequest>>(Channel.UNLIMITED)
        val cached = MutableStateFlow<Map<String, String>>(emptyMap())
        var sources: List<PreparedTagSource> = emptyList()
        val controller = AlbumParagraphAheadWindow(
            this, resolver, { sources = it; cached }, { published.trySend(it) }, {}, Dispatchers.Unconfined,
        )
        try {
            controller.setActive(true)
            controller.submit(AlbumParagraphViewport(0, 0, 2), true, environment) {
                album(cv = "甲 / 甲; 乙", tags = listOf(" #耳かき ", "#耳かき", "耳かき", "RJ123", "  "))
            }
            val initial = withTimeout(5_000) { published.receive() }
            assertEquals(listOf("甲", "乙", "#耳かき", "#耳かき", "#RJ123"), initial.map { it.text })
            assertEquals(listOf(0, 1, 0, 1, 2), initial.map { it.occurrence.index })
            assertEquals(5, initial.map { it.occurrence }.distinct().size)
            assertEquals(listOf(PreparedParagraphLabel.Cv, PreparedParagraphLabel.Cv,
                PreparedParagraphLabel.Tag, PreparedParagraphLabel.Tag, PreparedParagraphLabel.Tag),
                initial.map { it.occurrence.label })
            assertEquals(listOf("耳かき", "RJ123"), sources.map { it.original })
            assertFalse(sources.last().translatable)
            cached.value = mapOf("耳かき" to "掏耳")
            assertEquals(
                listOf("甲", "乙", "#掏耳", "#掏耳", "#RJ123"),
                withTimeout(5_000) { published.receive() }.map { it.text },
            )
        } finally {
            controller.close()
        }
    }

    @Test
    fun dataReorderKeepsStableItemOccurrence_withDuplicateTextAcrossAlbums() = runBlocking {
        val published = Channel<List<PreparedParagraphRequest>>(Channel.UNLIMITED)
        val controller = AlbumParagraphAheadWindow(
            this, resolver, { flowOf(emptyMap()) }, { published.trySend(it) }, {}, Dispatchers.Unconfined,
        )
        try {
            controller.setActive(true)
            val first = album(key = "album-a", tags = listOf("同名标签"))
            val second = album(key = "album-b", tags = listOf("同名标签"))
            var items = listOf(null, first, second)
            controller.submit(AlbumParagraphViewport(0, 0, 3), true, environment) { items.getOrNull(it) }
            val before = withTimeout(5_000) { published.receive() }
            items = listOf(null, second, first)
            controller.submit(AlbumParagraphViewport(0, 0, 3), true, environment) { items.getOrNull(it) }
            val after = withTimeout(5_000) { published.receive() }
            assertEquals(listOf("album-a", "album-b"), before.map { it.occurrence.itemKey })
            assertEquals(before.reversed(), after)
        } finally {
            controller.close()
        }
    }

    private fun album(key: Any = "fixture", cv: String = "", tags: List<String> = emptyList()): PreparedAlbumParagraphItem =
        PreparedAlbumParagraphItem(key, Album(title = "fixture", path = "", cv = cv, tags = tags))

    private val environment = AlbumParagraphEnvironment(
        PreparedLayoutStyle(TextStyle.Default), PreparedLayoutStyle(TextStyle.Default), 1f, 1f, LayoutDirection.Ltr,
    )
    private val resolver = Proxy.newProxyInstance(
        FontFamily.Resolver::class.java.classLoader,
        arrayOf(FontFamily.Resolver::class.java),
    ) { proxy, method, args ->
        when (method.name) {
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.firstOrNull()
            "toString" -> "Producer-only font resolver"
            else -> error("The page producer must not perform font layout")
        }
    } as FontFamily.Resolver
}
