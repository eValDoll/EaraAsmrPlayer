package com.asmr.player.domain.model

import com.asmr.player.data.remote.api.Tag
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class AlbumTagTest {
    private val earCleaning = AlbumTag("dlsite:genre:1", "dlsite", "耳かき", mapOf("en" to "Ear cleaning", "zh-CN" to "掏耳"))

    @Test fun localizedNamesDoNotChangeIdentityOrRawTags() {
        val album = Album(title = "作品", path = "", rjCode = "RJ123456", tags = listOf("耳かき", "我的标签"), tagDetails = listOf(earCleaning))
        assertEquals(listOf("Ear cleaning", "我的标签"), album.localizedTags("en_US"))
        assertEquals(listOf("掏耳", "我的标签"), album.localizedTags("zh_CN"))
        assertEquals(listOf("耳かき", "我的标签"), album.tags)
        assertEquals("RJ123456", album.rjCode)
    }

    @Test fun missingLanguageAndLegacyCacheKeepRawTags() {
        val album = Gson().fromJson("""{"title":"Work","path":"","tags":["耳かき"]}""", Album::class.java)
        assertEquals(listOf("耳かき"), album.localizedTags("en"))
        assertEquals("耳かき", earCleaning.displayName("ko"))
        assertEquals(emptyList<AlbumTag>(), AlbumTagCodec.decode("not JSON"))
    }

    @Test fun sameSourceIdentityCombinesLanguagesWithoutJoiningOtherSources() {
        val translated = earCleaning.copy(originalName = "Ear cleaning", names = mapOf("en" to "Ear cleaning"))
        val other = earCleaning.copy(key = "asmr-one:tag:1", source = "asmr-one")
        val result = mergeAlbumTags(listOf(earCleaning), listOf(translated, other))
        assertEquals(2, result.size)
        assertEquals("耳かき", result.first().originalName)
        assertEquals("掏耳", result.first().displayName("zh"))
        assertEquals(result, AlbumTagCodec.decode(AlbumTagCodec.encode(result)))
    }

    @Test fun malformedUpstreamNamesDoNotBreakOtherTranslations() {
        val tag = Gson().fromJson("""{"id":42,"name":"耳かき","i18n":{"en":"Ear cleaning","zh":null,"bad":{},"count":3}}""", Tag::class.java).toAlbumTag()
        assertEquals("asmr-one:tag:42", tag.key)
        assertEquals("Ear cleaning", tag.displayName("en-GB"))
        assertEquals("耳かき", tag.displayName("zh"))
    }

    @Test fun crossSourceNamesMustAgreeAndNeverReplaceCustomTags() {
        val other = earCleaning.copy(key = "asmr-one:tag:1", source = "asmr-one")
        val album = Album(title = "", path = "", tags = listOf("耳かき"), tagDetails = listOf(earCleaning, other))
        assertEquals(listOf("Ear cleaning"), album.localizedTags("en"))
        assertEquals(listOf("耳かき"), album.copy(tagDetails = listOf(earCleaning, other.copy(names = mapOf("en" to "Different")))).localizedTags("en"))
    }

    @Test fun nullMetadataDoesNotDiscardValidSiblingTags() {
        val tags = AlbumTagCodec.decode("""[{"key":null,"originalName":null},{"key":"dlsite:genre:1","source":"dlsite","originalName":"耳かき","names":{"en":"Ear cleaning","zh":null}}]""")
        assertEquals(1, tags.size)
        assertEquals("Ear cleaning", tags.single().displayName("en"))
        assertEquals("耳かき", tags.single().displayName("zh"))
    }

    @Test fun translatedLabelsKeepCanonicalSearchValuesAndAvoidCollisions() {
        val album = Album(title = "", path = "", metadataLocale = "en", tags = listOf("耳かき"), tagDetails = listOf(earCleaning))
        assertEquals("耳かき", album.originalTagForDisplay("Ear cleaning"))
        val duplicate = earCleaning.copy(key = "dlsite:genre:2", originalName = "別タグ")
        assertEquals(listOf("耳かき", "別タグ"), album.copy(tags = listOf("耳かき", "別タグ"), tagDetails = listOf(earCleaning, duplicate)).localizedTags("en"))
    }

    @Test fun upstreamNestedNamesUseCurrentNameAndIgnoreHistory() {
        // api.asmr.one/api/works?order=id&sort=desc&page=1, sampled 2026-11-08.
        val tag = Gson().fromJson("""{"id":399,"name":"青年","i18n":{"en-us":{"name":"Seinen","history":[{"name":"Young Man"}]},"ja-jp":{"name":"青年"},"zh-cn":{"name":"青年","history":[]},"missing":{"name":null}}}""", Tag::class.java).toAlbumTag()
        assertEquals("Seinen", tag.displayName("en_US"))
        assertEquals("青年", tag.displayName("zh_CN"))
        assertEquals(3, tag.names.orEmpty().size)
    }
}
