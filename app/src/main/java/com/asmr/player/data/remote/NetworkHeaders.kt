package com.asmr.player.data.remote

import java.util.Locale

object NetworkHeaders {
    const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    const val REFERER_DLSITE = "https://www.dlsite.com/"
    const val HEADER_SILENT_IO_ERROR = "X-Silent-IO-Error"
    const val SILENT_IO_ERROR_ON = "1"

    val ACCEPT_LANGUAGE: String
        get() = getAcceptLanguage()

    // 根据系统语言动态生成 Accept-Language
    fun getAcceptLanguage(language: String = getSystemLanguage()): String {
        return when {
            language.startsWith("zh") -> "zh-CN,zh;q=0.9,en;q=0.8,ja;q=0.7"
            language.startsWith("ja") -> "ja-JP,ja;q=0.9,en;q=0.8"
            else -> "en;q=0.9,zh;q=0.8,ja;q=0.7"
        }
    }
    
    private fun getSystemLanguage(): String {
        return Locale.getDefault().language
    }
}

