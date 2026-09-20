package com.asmr.player.translation

import android.app.Application
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.activity.ComponentActivity
import android.content.ComponentName
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.foundation.layout.Column
import androidx.test.core.app.ApplicationProvider
import com.asmr.player.ui.theme.AsmrPlayerTheme
import com.asmr.player.ui.settings.PageTranslationLanguageSelector
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import okhttp3.mockwebserver.SocketPolicy
import java.util.concurrent.TimeUnit
import java.util.concurrent.CountDownLatch
import org.junit.Rule
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class PageTranslationUiTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    // Release intentionally does not package ui-test-manifest. Register a test-only host
    // before ActivityScenario launches it, retaining the standard Compose test lifecycle.
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            val context = ApplicationProvider.getApplicationContext<Application>()
            shadowOf(context.packageManager).addActivityIfNotPresent(ComponentName(context, ComponentActivity::class.java))
        }
    }).around(compose)

    private fun waitForTranslation(condition: () -> Boolean) {
        compose.runOnIdle { assertEquals(Lifecycle.State.RESUMED, compose.activity.lifecycle.currentState) }
        compose.waitUntil(10_000) {
            // repeatOnLifecycle uses Android's Main dispatcher; advance its paused Robolectric looper too.
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            condition()
        }
    }

    @Test fun cachedFileTranslationRestoresOriginalAndSwitchesLanguageWithoutChangingSource() {
        MockWebServer().use { server ->
            val repository = PageTranslationRepository(ApplicationProvider.getApplicationContext(), PageTranslationClient(OkHttpClient(), server.url("/").toString()))
            server.enqueue(MockResponse().setBody("""["掏耳朵"]"""))
            server.enqueue(MockResponse().setBody("""["Ear cleaning"]"""))
            runBlocking {
                repository.clearCache()
                repository.translateBatch(listOf("耳かき"), "zh-CN")
                repository.translateBatch(listOf("耳かき"), "en")
            }
            val settings = mutableStateOf(PageTranslationSettings(automatic = true))
            val source = "耳かき.wav"
            compose.setContent {
                AsmrPlayerTheme {
                    PageTranslationScope(active = false, settings = settings.value, repository = repository) {
                        Text(translatedPageText(source, fileName = true))
                    }
                }
            }
            compose.onNodeWithText("掏耳朵.wav").assertIsDisplayed()
            compose.runOnIdle { settings.value = settings.value.copy(target = "en") }
            compose.onNodeWithText("Ear cleaning.wav").assertIsDisplayed()
            compose.runOnIdle { settings.value = settings.value.copy(automatic = false) }
            compose.onNodeWithText(source).assertIsDisplayed()
            assertEquals("耳かき.wav", source)
            assertEquals(2, server.requestCount)
        }
    }

    @Test fun inactivePageDoesNotStartTranslation() {
        MockWebServer().use { server ->
            val repository = PageTranslationRepository(ApplicationProvider.getApplicationContext(), PageTranslationClient(OkHttpClient(), server.url("/").toString()))
            compose.setContent {
                AsmrPlayerTheme {
                    PageTranslationScope(active = false, settings = PageTranslationSettings(automatic = true), repository = repository) {
                        Text(translatedPageText("待翻译的可见标题"))
                    }
                }
            }
            compose.mainClock.advanceTimeBy(1_000)
            compose.onNodeWithText("待翻译的可见标题").assertIsDisplayed()
            assertEquals(0, server.requestCount)
        }
    }

    @Test fun pageActionTogglesTheActualScopeAndRestoresOriginal() {
        MockWebServer().use { server ->
            val repository = PageTranslationRepository(ApplicationProvider.getApplicationContext(), PageTranslationClient(OkHttpClient(), server.url("/").toString()))
            server.enqueue(MockResponse().setBody("""["掏耳朵"]"""))
            runBlocking {
                repository.clearCache()
                repository.translateBatch(listOf("耳かき"), "zh-CN")
            }
            compose.setContent {
                AsmrPlayerTheme {
                    PageTranslationScope(active = false, settings = PageTranslationSettings(), repository = repository) {
                        Column { PageTranslationAction(); Text(translatedPageText("耳かき")) }
                    }
                }
            }
            compose.onNodeWithText("耳かき").assertIsDisplayed()
            compose.onNodeWithTag("page_translation_action").assertIsOff()
            compose.onNodeWithTag("page_translation_action").performClick()
            compose.onNodeWithText("掏耳朵").assertIsDisplayed()
            compose.onNodeWithTag("page_translation_action").assertIsOn()
            compose.onNodeWithText("翻译页面").assertDoesNotExist()
            compose.onNodeWithText("由 Google 翻译提供").assertDoesNotExist()
            compose.onNodeWithTag("page_translation_loading").assertDoesNotExist()
            compose.onNodeWithTag("page_translation_action").performClick()
            compose.onNodeWithText("耳かき").assertIsDisplayed()
            compose.onNodeWithTag("page_translation_action").assertIsOff()
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun actionShowsLoadingUntilTheBatchCompletesThenRestoresOriginalWithOneTap() {
        MockWebServer().use { server ->
            val responseReady = CountDownLatch(1)
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    check(responseReady.await(10, TimeUnit.SECONDS))
                    return MockResponse().setBody("""["掏耳朵"]""")
                }
            }
            try {
                val repository = PageTranslationRepository(ApplicationProvider.getApplicationContext(), PageTranslationClient(OkHttpClient(), server.url("/").toString()))
                runBlocking { repository.clearCache() }
                val header = PageTranslationHeaderState()
                compose.setContent {
                    AsmrPlayerTheme {
                        CompositionLocalProvider(LocalPageTranslationHeader provides header) {
                            Column {
                                PageTranslationHeaderAction("library")
                                PageTranslationScope(active = true, settings = PageTranslationSettings(), repository = repository, headerKey = "library") {
                                    Text(translatedPageText("耳かき"))
                                }
                            }
                        }
                    }
                }
                compose.onNodeWithContentDescription("翻译页面").performClick()
                waitForTranslation { server.requestCount == 1 }
                compose.onNodeWithTag("page_translation_loading", useUnmergedTree = true).assertIsDisplayed()
                compose.onNodeWithText("耳かき").assertIsDisplayed()
                responseReady.countDown()
                waitForTranslation { repository.cached("耳かき", "zh-CN") != null }
                compose.onNodeWithText("掏耳朵").assertIsDisplayed()
                compose.onNodeWithTag("page_translation_loading", useUnmergedTree = true).assertDoesNotExist()
                compose.onNodeWithContentDescription("显示原文").performClick()
                compose.onNodeWithText("耳かき").assertIsDisplayed()
                assertEquals(1, server.requestCount)
            } finally { responseReady.countDown() }
        }
    }

    @Test fun sharedHeaderOnlyTogglesItsCurrentPageAndRetainsEachPageSelection() {
        MockWebServer().use { server ->
            server.echoPageTranslations()
            val repository = PageTranslationRepository(ApplicationProvider.getApplicationContext(), PageTranslationClient(OkHttpClient(), server.url("/").toString()))
            val pages = listOf("library", "search", "hot_listening")
            runBlocking {
                repository.clearCache()
                repository.translateBatch(pages, "zh-CN")
            }
            val currentPage = mutableStateOf("library")
            val header = PageTranslationHeaderState()
            compose.setContent {
                AsmrPlayerTheme {
                    CompositionLocalProvider(LocalPageTranslationHeader provides header) {
                        Column {
                            PageTranslationHeaderAction(currentPage.value)
                            pages.forEach { page ->
                                key(page) {
                                    PageTranslationScope(active = false, settings = PageTranslationSettings(), repository = repository, headerKey = page) {
                                        Text(translatedPageText(page), Modifier.testTag("label:$page"))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            compose.onNodeWithTag("page_translation_action").performClick()
            compose.onNodeWithTag("label:library").assertTextEquals("译文：library")
            compose.onNodeWithTag("label:search").assertTextEquals("search")
            compose.onNodeWithTag("label:hot_listening").assertTextEquals("hot_listening")
            compose.runOnIdle { currentPage.value = "search" }
            compose.onNodeWithTag("page_translation_action").assertIsOff().performClick()
            compose.onNodeWithTag("label:search").assertTextEquals("译文：search")
            compose.onNodeWithTag("label:library").assertTextEquals("译文：library")
            compose.runOnIdle { currentPage.value = "library" }
            compose.onNodeWithTag("page_translation_action").assertIsOn().performClick()
            compose.onNodeWithTag("label:library").assertTextEquals("library")
            compose.onNodeWithTag("label:search").assertTextEquals("译文：search")
            compose.runOnIdle { currentPage.value = "settings" }
            compose.onNodeWithTag("page_translation_action").assertDoesNotExist()
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun disposingThePageRemovesItsHeaderAction() {
        MockWebServer().use { server ->
            val repository = PageTranslationRepository(ApplicationProvider.getApplicationContext(), PageTranslationClient(OkHttpClient(), server.url("/").toString()))
            val showPage = mutableStateOf(true)
            val header = PageTranslationHeaderState()
            compose.setContent {
                AsmrPlayerTheme {
                    CompositionLocalProvider(LocalPageTranslationHeader provides header) {
                        Column {
                            PageTranslationHeaderAction("library")
                            if (showPage.value) {
                                PageTranslationScope(active = false, settings = PageTranslationSettings(), repository = repository, headerKey = "library") {
                                    Text(translatedPageText("耳かき"))
                                }
                            }
                        }
                    }
                }
            }
            compose.onNodeWithTag("page_translation_action").assertIsDisplayed()
            compose.runOnIdle { showPage.value = false }
            compose.onNodeWithTag("page_translation_action").assertDoesNotExist()
            compose.runOnIdle { assertTrue(header.actions.isEmpty()) }
        }
    }

    @Test fun tappingWhileLoadingCancelsTheBatchAndStopsTheAnimation() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val http = OkHttpClient()
            val repository = PageTranslationRepository(ApplicationProvider.getApplicationContext(), PageTranslationClient(http, server.url("/").toString()))
            runBlocking { repository.clearCache() }
            compose.setContent {
                AsmrPlayerTheme {
                    PageTranslationScope(active = true, settings = PageTranslationSettings(), repository = repository) {
                        Column { PageTranslationAction(); Text(translatedPageText("耳かき")) }
                    }
                }
            }
            compose.onNodeWithTag("page_translation_action").performClick()
            waitForTranslation { server.requestCount == 1 }
            compose.onNodeWithTag("page_translation_loading", useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithTag("page_translation_action").performClick()
            waitForTranslation { http.dispatcher.runningCalls().all { it.isCanceled() } }
            compose.onNodeWithTag("page_translation_action").assertIsOff()
            compose.onNodeWithTag("page_translation_loading", useUnmergedTree = true).assertDoesNotExist()
            compose.onNodeWithText("耳かき").assertIsDisplayed()
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun failedTranslationStopsLoadingAndReportsOnceWithoutOpeningAMenu() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(429))
            val repository = PageTranslationRepository(ApplicationProvider.getApplicationContext(), PageTranslationClient(OkHttpClient(), server.url("/").toString()))
            runBlocking { repository.clearCache() }
            val failures = mutableListOf<String>()
            compose.setContent {
                AsmrPlayerTheme {
                    PageTranslationScope(active = true, settings = PageTranslationSettings(), repository = repository, onFailure = { failures += it }) {
                        Column { PageTranslationAction(); Text(translatedPageText("耳かき")) }
                    }
                }
            }
            compose.onNodeWithTag("page_translation_action").performClick()
            waitForTranslation { failures.isNotEmpty() }
            compose.onNodeWithTag("page_translation_loading", useUnmergedTree = true).assertDoesNotExist()
            compose.onNodeWithText("耳かき").assertIsDisplayed()
            compose.onNodeWithText("翻译页面").assertDoesNotExist()
            compose.onNodeWithContentDescription("显示原文").performClick()
            compose.onNodeWithTag("page_translation_action").assertIsOff()
            assertEquals(listOf("翻译服务暂时限流，请稍后重试"), failures)
        }
    }

    @Test fun targetLanguageMenuPreservesSelectionAndDoesNotOfferAutoDetection() {
        val target = mutableStateOf("zh-CN")
        compose.setContent {
            AsmrPlayerTheme {
                PageTranslationLanguageSelector("目标语言", target.value, { target.value = it })
            }
        }
        compose.onNodeWithTag("page_translation_目标语言").performClick()
        val targetBounds = compose.onNodeWithTag("page_translation_目标语言").getUnclippedBoundsInRoot()
        compose.onNodeWithTag("page_translation_menu_目标语言").assertWidthIsEqualTo(
            targetBounds.right - targetBounds.left
        )
        compose.onNodeWithTag("page_translation_option_目标语言_zh-CN").assertIsSelected()
        compose.onNodeWithText("自动识别语言").assertDoesNotExist()
        compose.onNodeWithText("英文").performClick()
        assertEquals("en", target.value)
        compose.onNodeWithTag("page_translation_menu_目标语言").assertDoesNotExist()
        compose.onNodeWithText("英文").assertIsDisplayed()
        compose.onNodeWithTag("page_translation_目标语言").performClick()
        compose.onNodeWithTag("page_translation_option_目标语言_en").assertIsSelected()
    }

    @Test fun pageCollectsLabelsIntoOneRequestAndOnlySendsNewLabelsAfterScrolling() {
        MockWebServer().use { server ->
            server.echoPageTranslations()
            val repository = PageTranslationRepository(ApplicationProvider.getApplicationContext(), PageTranslationClient(OkHttpClient(), server.url("/").toString()))
            runBlocking { repository.clearCache() }
            val texts = mutableStateOf((1..12).map { "目录$it" })
            compose.setContent {
                AsmrPlayerTheme {
                    PageTranslationScope(active = true, settings = PageTranslationSettings(automatic = true), repository = repository) {
                        Column {
                            texts.value.forEach { Text(translatedPageText(it)) }
                            Text(translatedPageText("目录1.wav", fileName = true))
                        }
                    }
                }
            }
            waitForTranslation { repository.cached("目录12", "zh-CN") != null }
            compose.onNodeWithText("译文：目录1.wav").assertIsDisplayed()
            assertEquals(1, server.requestCount)
            assertEquals(texts.value.toSet(), server.takeRequest().pageTranslationTexts().toSet())
            compose.runOnIdle { texts.value = listOf("目录1", "新目录") }
            waitForTranslation { repository.cached("新目录", "zh-CN") != null }
            compose.onNodeWithText("译文：新目录").assertIsDisplayed()
            assertEquals(listOf("新目录"), server.takeRequest().pageTranslationTexts())
            assertEquals(2, server.requestCount)
        }
    }

    @Test fun newLabelsDuringAnInFlightBatchDoNotRestartItsRequest() {
        MockWebServer().use { server ->
            server.echoPageTranslations(delayMillis = 400)
            val repository = PageTranslationRepository(ApplicationProvider.getApplicationContext(), PageTranslationClient(OkHttpClient(), server.url("/").toString()))
            runBlocking { repository.clearCache() }
            val texts = mutableStateOf(listOf("耳かき", "眠り"))
            compose.setContent {
                AsmrPlayerTheme {
                    PageTranslationScope(active = true, settings = PageTranslationSettings(automatic = true), repository = repository) {
                        Column { texts.value.forEach { Text(translatedPageText(it)) } }
                    }
                }
            }
            waitForTranslation { server.requestCount == 1 }
            compose.runOnIdle { texts.value = texts.value + "新目录" }
            waitForTranslation { repository.cached("新目录", "zh-CN") != null }
            compose.onNodeWithText("译文：耳かき").assertIsDisplayed()
            compose.onNodeWithText("译文：新目录").assertIsDisplayed()
            assertEquals(setOf("耳かき", "眠り"), server.takeRequest().pageTranslationTexts().toSet())
            assertEquals(listOf("新目录"), server.takeRequest().pageTranslationTexts())
            assertEquals(2, server.requestCount)
        }
    }

    @Test fun leavingPageCancelsItsEntireBatch() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val http = OkHttpClient()
            val repository = PageTranslationRepository(ApplicationProvider.getApplicationContext(), PageTranslationClient(http, server.url("/").toString()))
            runBlocking { repository.clearCache() }
            val active = mutableStateOf(true)
            compose.setContent {
                AsmrPlayerTheme {
                    PageTranslationScope(active = active.value, settings = PageTranslationSettings(automatic = true), repository = repository) {
                        Column { Text(translatedPageText("耳かき")); Text(translatedPageText("眠り")) }
                    }
                }
            }
            waitForTranslation { server.requestCount == 1 }
            assertEquals(setOf("耳かき", "眠り"), server.takeRequest(1, TimeUnit.SECONDS)!!.pageTranslationTexts().toSet())
            compose.runOnIdle { active.value = false }
            waitForTranslation { http.dispatcher.runningCalls().all { it.isCanceled() } }
            assertTrue(http.dispatcher.runningCalls().all { it.isCanceled() })
            compose.onNodeWithText("耳かき").assertIsDisplayed()
            assertEquals(1, server.requestCount)
        }
    }
}
