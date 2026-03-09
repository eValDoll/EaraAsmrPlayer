package com.asmr.player.cache

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.unit.IntSize
import coil.request.ImageRequest
import java.security.MessageDigest

object CacheKeyFactory {
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
        return md5("$version|$dark|$w|$h|$data")
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
            else -> data.toString()
        }
    }

    private fun isDarkMode(context: Context): Boolean {
        val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return uiMode == Configuration.UI_MODE_NIGHT_YES
    }

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return buildString(digest.size * 2) {
            for (b in digest) append(((b.toInt() and 0xFF) + 0x100).toString(16).substring(1))
        }
    }
}
