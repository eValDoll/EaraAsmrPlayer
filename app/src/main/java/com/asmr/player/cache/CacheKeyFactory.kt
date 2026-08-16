package com.asmr.player.cache

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.unit.IntSize
import coil.request.ImageRequest
import java.io.File
import java.security.MessageDigest
import java.util.LinkedHashMap

object CacheKeyFactory {
    private const val MaxRememberedHashes = 2048
    private val hashCacheLock = Any()
    private val hashCache = object : LinkedHashMap<String, String>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
            return size > MaxRememberedHashes
        }
    }
    private val md5Digest = ThreadLocal.withInitial { MessageDigest.getInstance("MD5") }

    fun createKey(
        context: Context,
        model: Any,
        size: IntSize?,
        version: String
    ): String {
        val data = normalizeModel(model)
        val w = size?.width ?: -1
        val h = size?.height ?: -1
        val dark = isDarkMode(context)
        return cachedMd5("$version|$dark|$w|$h|$data")
    }

    /**
     * 与 [createKey] 相同的归一化逻辑，但忽略尺寸维度。
     * 用于按“同一张图片数据”跨尺寸检索已缓存的任意位图（即时占位复用）。
     */
    fun createDataKey(
        context: Context,
        model: Any,
        version: String
    ): String {
        val data = normalizeModel(model)
        val dark = isDarkMode(context)
        return cachedMd5("$version|$dark|$data")
    }

    private fun normalizeModel(model: Any): String {
        return when (model) {
            is CacheImageModel -> "model:${model.keyTag}|${normalizeModelData(model.data)}"
            is ImageRequest -> "request:${normalizeModelData(model.data)}"
            else -> normalizeModelData(model)
        }
    }

    private fun normalizeModelData(data: Any?): String {
        return when (data) {
            null -> "null"
            is String -> data.trim()
            is File -> "file:${data.absolutePath}|${data.length()}|${data.lastModified()}"
            else -> data.toString()
        }
    }

    private fun isDarkMode(context: Context): Boolean {
        val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return uiMode == Configuration.UI_MODE_NIGHT_YES
    }

    private fun cachedMd5(input: String): String {
        synchronized(hashCacheLock) {
            hashCache[input]?.let { return it }
        }
        val digest = checkNotNull(md5Digest.get())
        digest.reset()
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        val chars = CharArray(bytes.size * 2)
        for (index in bytes.indices) {
            val value = bytes[index].toInt() and 0xFF
            chars[index * 2] = HexDigits[value ushr 4]
            chars[index * 2 + 1] = HexDigits[value and 0x0F]
        }
        val hash = String(chars)
        return synchronized(hashCacheLock) {
            hashCache[input] ?: hash.also { hashCache[input] = it }
        }
    }

    private const val HexDigits = "0123456789abcdef"
}
