package com.asmr.player.translation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.util.MessageManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface PageTranslationEntryPoint {
    fun translations(): PageTranslationRepository
    fun preferences(): PageTranslationPreferences
    fun messages(): MessageManager
}

@Composable
internal fun rememberPageTranslationServices(): PageTranslationEntryPoint {
    val context = LocalContext.current.applicationContext
    return remember(context) { EntryPointAccessors.fromApplication(context, PageTranslationEntryPoint::class.java) }
}

@Stable
internal class PageTranslationSession(
    val repository: PageTranslationRepository,
    initialSettings: PageTranslationSettings,
    initiallyEnabled: Boolean,
) {
    var enabled by mutableStateOf(initiallyEnabled)
    var settings by mutableStateOf(initialSettings)
    var pending by mutableIntStateOf(0)
    var failure by mutableStateOf<String?>(null)
    val labels = mutableStateMapOf<String, Int>()
    val translations = mutableStateMapOf<String, String>()

    fun register(text: String) { labels[text] = (labels[text] ?: 0) + 1 }
    fun unregister(text: String) {
        val count = labels[text] ?: return
        if (count > 1) labels[text] = count - 1
        else {
            labels.remove(text)
            translations.remove(text)
        }
    }
}

internal val LocalPageTranslation = staticCompositionLocalOf<PageTranslationSession?> { null }

@Stable
internal class PageTranslationControl(val session: PageTranslationSession, val toggle: () -> Unit)

@Stable
internal class PageTranslationHeaderState {
    val actions = mutableStateMapOf<String, PageTranslationControl>()
}

internal val LocalPageTranslationHeader = staticCompositionLocalOf<PageTranslationHeaderState?> { null }
private val LocalPageTranslationControl = staticCompositionLocalOf<PageTranslationControl?> { null }

@Composable
internal fun PageTranslationHost(active: Boolean = true, headerKey: String? = null, content: @Composable () -> Unit) {
    val services = rememberPageTranslationServices()
    val settingsFlow = remember(services) { services.preferences().settings }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val settings by produceState(PageTranslationSettings(), settingsFlow, active, lifecycle) {
        if (active) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { settingsFlow.collect { value = it } }
    }
    PageTranslationScope(active, settings, services.translations(), onFailure = { services.messages().showError(it) }, headerKey = headerKey, content = content)
}

@Composable
internal fun PageTranslationScope(
    active: Boolean,
    settings: PageTranslationSettings,
    repository: PageTranslationRepository,
    onFailure: (String) -> Unit = {},
    headerKey: String? = null,
    content: @Composable () -> Unit,
) {
    var manualEnabled by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val enabled = manualEnabled ?: settings.automatic
    val session = remember(repository, settings.target) {
        PageTranslationSession(repository, settings, enabled)
    }
    val control = remember(session) {
        PageTranslationControl(session) {
            manualEnabled = !session.enabled
            session.failure = null
        }
    }
    val header = LocalPageTranslationHeader.current
    DisposableEffect(header, headerKey, control) {
        if (header != null && headerKey != null) header.actions[headerKey] = control
        onDispose {
            if (header != null && headerKey != null && header.actions[headerKey] === control) {
                header.actions.remove(headerKey)
            }
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reportFailure by rememberUpdatedState(onFailure)
    SideEffect {
        session.enabled = enabled
        if (session.settings != settings) session.failure = null
        session.settings = settings
    }
    LaunchedEffect(session.failure, active, enabled) {
        if (active && enabled) session.failure?.let(reportFailure)
    }
    LaunchedEffect(session, active, enabled, lifecycle) {
        if (!active || !enabled) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            snapshotFlow { session.labels.keys.toList() }.collect {
                // Coalesce layout/scroll changes. Let an in-flight batch finish to avoid resending it;
                // the outer effect still cancels immediately when this page is inactive or disabled.
                val texts = session.labels.keys.toList()
                if (texts.isEmpty()) return@collect
                session.pending = texts.count {
                    it !in session.translations && repository.cached(it, settings.target) == null
                }
                try {
                    delay(180)
                    val currentTexts = session.labels.keys.toList()
                    session.pending = currentTexts.count {
                        it !in session.translations && repository.cached(it, settings.target) == null
                    }
                    val result = repository.translateBatch(currentTexts, settings.target)
                    result.translations.forEach { (text, translated) ->
                        if (text in session.labels) session.translations[text] = translated
                    }
                    session.failure = result.failure
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    session.failure = (error as? PageTranslationException)?.message ?: "页面翻译失败，请稍后重试"
                } finally {
                    session.pending = 0
                }
            }
        }
    }
    CompositionLocalProvider(LocalPageTranslation provides session, LocalPageTranslationControl provides control) { content() }
}

@Composable
internal fun PageTranslationHeaderAction(pageKey: String?, modifier: Modifier = Modifier) {
    if (pageKey == null) return
    val header = LocalPageTranslationHeader.current ?: return
    val control = header.actions[pageKey] ?: return
    PageTranslationAction(modifier, control)
}

/** Labels register with their page; only the page scope owns network requests. */
@Composable
internal fun translatedPageText(text: String, fileName: Boolean = false): String {
    val session = LocalPageTranslation.current ?: return text
    if (!session.enabled) return text
    val parts = remember(text, fileName) { pageTranslationText(text, fileName) }
    if (!shouldTranslatePageText(parts.source)) return text
    val settings = session.settings
    DisposableEffect(session, parts.source) {
        session.register(parts.source)
        onDispose { session.unregister(parts.source) }
    }
    val translated = session.translations[parts.source]
        ?: session.repository.cached(parts.source, settings.target)
    return translated?.let { it + parts.suffix } ?: text
}

@Composable
internal fun PageTranslationAction(
    modifier: Modifier = Modifier,
    control: PageTranslationControl? = LocalPageTranslationControl.current,
    contentColor: Color = AsmrTheme.colorScheme.onSurfaceVariant,
    activeContentColor: Color = AsmrTheme.colorScheme.primary,
) {
    val session = control?.session ?: return
    val colors = AsmrTheme.colorScheme
    val loading = session.enabled && session.pending > 0
    IconToggleButton(
        checked = session.enabled,
        onCheckedChange = { control.toggle() },
        modifier = modifier.size(40.dp).testTag("page_translation_action").semantics {
            contentDescription = if (session.enabled) "显示原文" else "翻译页面"
            stateDescription = when {
                loading -> "正在翻译"
                !session.enabled -> "原文"
                session.failure != null -> "翻译失败"
                else -> "已开启翻译"
            }
        },
        colors = IconButtonDefaults.iconToggleButtonColors(
            containerColor = Color.Transparent,
            contentColor = contentColor,
            checkedContainerColor = Color.Transparent,
            checkedContentColor = activeContentColor,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = colors.textTertiary,
        ),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp).testTag("page_translation_loading"),
                color = activeContentColor,
                trackColor = Color.Transparent,
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                if (session.enabled) Icons.AutoMirrored.Rounded.Undo else Icons.Rounded.Translate,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
