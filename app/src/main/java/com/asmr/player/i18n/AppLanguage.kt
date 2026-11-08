package com.asmr.player.i18n

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/** Resource access for services and other code outside a Compose resource scope. */
object AppLanguage {
    @Volatile private var application: Context? = null
    @Volatile private var localizedContext: Context? = null

    fun initialize(context: Context) {
        application = context.applicationContext
        localizedContext = null
    }

    fun refresh() { localizedContext = null }

    fun select(language: String) {
        require(language in setOf("", "zh-CN", "en"))
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language))
        localizedContext = null
    }

    fun selected(): String {
        val language = AppCompatDelegate.getApplicationLocales()[0]?.language ?: return ""
        return when (language) { "zh" -> "zh-CN"; "en" -> "en"; else -> "" }
    }

    fun resourcesContext(): Context {
        localizedContext?.let { return it }
        val context = checkNotNull(application) { "Application resources have not been initialized" }
        return synchronized(this) {
            localizedContext ?: run {
                val requested = AppCompatDelegate.getApplicationLocales()
                val next = Configuration(context.resources.configuration)
                if (!requested.isEmpty) next.setLocales(android.os.LocaleList.forLanguageTags(requested.toLanguageTags()))
                context.createConfigurationContext(next).also { localizedContext = it }
            }
        }
    }

    fun metadataLocale(): String = metadataLocaleForLanguage(
        application?.let { resourcesContext().resources.configuration.locales[0].toLanguageTag() }
            ?: Locale.getDefault().toLanguageTag()
    ).let { if (it.startsWith("zh")) it else "en_US" }
}

fun metadataLocaleForLanguage(language: String?): String {
    val normalized = language.orEmpty().replace('_', '-').lowercase(Locale.ROOT)
    return when {
        normalized.startsWith("zh-tw") || normalized.startsWith("zh-hk") || normalized.contains("hant") -> "zh_TW"
        normalized.startsWith("zh") -> "zh_CN"
        normalized.startsWith("ja") -> "ja_JP"
        else -> "en_US"
    }
}

fun appString(@StringRes id: Int, vararg arguments: Any?): String =
    AppLanguage.resourcesContext().getString(id, *arguments)
