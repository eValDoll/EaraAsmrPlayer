package com.asmr.player.domain.model

import androidx.compose.runtime.Immutable
import com.asmr.player.i18n.AppLanguage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Locale

@Immutable
data class AlbumTag(
    val key: String = "",
    val source: String = "",
    val originalName: String = "",
    val names: Map<String, String>? = emptyMap()
) {
    fun localizedName(locale: String): String? {
        val target = tagLanguage(locale)
        return names.orEmpty().entries.firstOrNull {
            !it.key.isNullOrBlank() && !it.value.isNullOrBlank() && tagLanguage(it.key) == target
        }?.value?.trim()
    }

    fun displayName(locale: String): String = localizedName(locale) ?: originalName
}

internal fun tagLanguage(locale: String): String {
    val tag = locale.replace('_', '-').lowercase(Locale.ROOT)
    return when {
        tag.startsWith("zh-tw") || tag.startsWith("zh-hk") || tag.contains("hant") -> "zh-TW"
        tag.startsWith("zh") -> "zh-CN"
        else -> tag.substringBefore('-')
    }
}

fun mergeAlbumTags(first: List<AlbumTag>?, second: List<AlbumTag>?): List<AlbumTag> {
    val byKey = linkedMapOf<String, AlbumTag>()
    (first.orEmpty() + second.orEmpty()).forEach { tag ->
        if (tag.key.isNullOrBlank() || tag.originalName.isNullOrBlank()) return@forEach
        val validNames = tag.names.orEmpty().filter { (language, name) ->
            !language.isNullOrBlank() && !name.isNullOrBlank()
        }
        val previous = byKey[tag.key]
        byKey[tag.key] = if (previous == null) tag.copy(names = validNames) else previous.copy(
            names = previous.names.orEmpty() + validNames
        )
    }
    return byKey.values.toList()
}

fun Album.localizedTags(locale: String = metadataLocale ?: AppLanguage.metadataLocale()): List<String> {
    if (tagDetails.isNullOrEmpty()) return tags
    val details = tagDetails.orEmpty()
    val labels = tags.map { original ->
        val matches = details.filter { it.originalName == original || original in it.names.orEmpty().values }
        // Multiple sources may agree on a name without sharing the same tag identity.
        matches.mapNotNull { it.localizedName(locale) }.distinct().singleOrNull() ?: original
    }
    val collisions = labels.zip(tags).groupBy({ it.first }, { it.second })
        .filterValues { it.distinct().size > 1 }.keys
    return labels.mapIndexed { index, label -> if (label in collisions) tags[index] else label }
}

fun Album.originalTagForDisplay(label: String): String {
    val normalized = label.trim().removePrefix("#")
    return tags.zip(localizedTags()).filter { (_, display) -> display.trim().removePrefix("#") == normalized }
        .map { it.first.trim().removePrefix("#") }.distinct().singleOrNull() ?: label
}

object AlbumTagCodec {
    private val gson = Gson()
    private val type = object : TypeToken<List<AlbumTag>>() {}.type

    fun encode(tags: List<AlbumTag>?): String = gson.toJson(tags.orEmpty())
    fun decode(json: String?): List<AlbumTag> = if (json.isNullOrBlank()) emptyList() else runCatching {
        mergeAlbumTags(gson.fromJson<List<AlbumTag>?>(json, type), null)
    }.getOrDefault(emptyList())
}
