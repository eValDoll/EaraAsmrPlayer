package com.asmr.player.ui.search

import com.asmr.player.domain.model.Album
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchAlbumDetailMergeTest {
    @Test
    fun mergeSearchAlbumDetail_prefersCompleteDetailCvOverPartialListCv() {
        val base = Album(
            title = "作品",
            path = "",
            rjCode = "RJ123456",
            cv = "Alice",
            tags = listOf("耳语")
        )
        val detail = base.copy(
            cv = "Alice, Bob",
            tags = listOf("耳语", "安眠"),
            ratingValue = 4.8,
            ratingCount = 42,
            releaseDate = "2026-06-19"
        )

        val merged = mergeSearchAlbumDetail(base, detail)

        assertEquals("Alice, Bob", merged.cv)
        assertEquals(listOf("耳语"), merged.tags)
        assertEquals(4.8, merged.ratingValue ?: 0.0, 0.0)
        assertEquals(42, merged.ratingCount)
        assertEquals("2026-06-19", merged.releaseDate)
    }

    @Test
    fun mergeSearchAlbumDetail_usesLocalizedDetailTagsWithoutReplacingResultIdentity() {
        val base = Album(
            title = "日文标题",
            path = "",
            workId = "RJ123456",
            rjCode = "RJ123456",
            tags = listOf("バイノーラル", "癒し")
        )
        val localizedDetail = base.copy(
            title = "简中标题",
            tags = listOf("双耳录音", "治愈")
        )

        val merged = mergeSearchAlbumDetail(
            base = base,
            detail = localizedDetail,
            preferDetailTags = true
        )

        assertEquals("RJ123456", merged.rjCode)
        assertEquals("日文标题", merged.title)
        assertEquals(listOf("双耳录音", "治愈"), merged.tags)
    }

    @Test
    fun mergeSearchAlbumDetail_canRestoreJapaneseTagsAfterLocaleSwitch() {
        val base = Album(
            title = "日文标题",
            path = "",
            workId = "RJ123456",
            rjCode = "RJ123456",
            tags = listOf("双耳录音", "治愈")
        )
        val japaneseDetail = base.copy(tags = listOf("バイノーラル", "癒し"))

        val merged = mergeSearchAlbumDetail(
            base = base,
            detail = japaneseDetail,
            preferDetailTags = true
        )

        assertEquals("RJ123456", merged.rjCode)
        assertEquals("日文标题", merged.title)
        assertEquals(listOf("バイノーラル", "癒し"), merged.tags)
    }

    @Test
    fun mergeSearchAlbumDetail_localeRefreshChangesOnlyLocalizedText() {
        val base = Album(
            title = "日文标题",
            path = "",
            workId = "RJ123456",
            rjCode = "RJ123456",
            cv = "原声优",
            tags = listOf("バイノーラル"),
            ratingValue = 4.5,
            ratingCount = 100
        )
        val localizedDetail = base.copy(
            title = "简中标题",
            cv = "本地化声优",
            tags = listOf("双耳录音"),
            ratingValue = 4.9,
            ratingCount = 200
        )

        val merged = mergeSearchAlbumDetail(
            base = base,
            detail = localizedDetail,
            preferDetailTags = true,
            localizedTextOnly = true
        )

        assertEquals(
            base.copy(
                title = "简中标题",
                tags = listOf("双耳录音")
            ),
            merged
        )
    }

    @Test
    fun resolveSearchDetailLocale_keepsChineseWorksLocalizedAndNormalizesSelection() {
        assertEquals("ja_JP", resolveSearchDetailLocale("ja_JP", chineseTranslatedOnly = false))
        assertEquals("zh_CN", resolveSearchDetailLocale("zh_CN", chineseTranslatedOnly = false))
        assertEquals("zh_TW", resolveSearchDetailLocale("zh_TW", chineseTranslatedOnly = false))
        assertEquals("zh_CN", resolveSearchDetailLocale("ja_JP", chineseTranslatedOnly = true))
    }

    @Test
    fun resolveSearchRequestLocale_usesSelectedPageLanguageUnlessChineseWorksForcesChinese() {
        assertEquals("ja_JP", resolveSearchRequestLocale("ja_JP", chineseTranslatedOnly = false))
        assertEquals("zh_CN", resolveSearchRequestLocale("zh_CN", chineseTranslatedOnly = false))
        assertEquals("zh_TW", resolveSearchRequestLocale("zh_TW", chineseTranslatedOnly = false))
        assertEquals("zh_CN", resolveSearchRequestLocale("ja_JP", chineseTranslatedOnly = true))
    }
}
