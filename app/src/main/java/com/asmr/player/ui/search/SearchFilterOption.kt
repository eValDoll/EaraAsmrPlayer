package com.asmr.player.ui.search

import com.asmr.player.i18n.appString

import androidx.annotation.DrawableRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.ui.graphics.vector.ImageVector
import com.asmr.player.R
import com.asmr.player.domain.model.CollectedSearchSource

sealed class SearchFilterIcon {
    data class Vector(val imageVector: ImageVector) : SearchFilterIcon()

    data class Drawable(@DrawableRes val resId: Int) : SearchFilterIcon()
}

enum class SearchFilterOption(
    private val labelProvider: () -> String,
    val icon: SearchFilterIcon,
    val mode: SearchFilterMode
) {
    Collected(
        labelProvider = { "asmr.one" },
        icon = SearchFilterIcon.Drawable(R.drawable.ic_search_collected_library),
        mode = SearchFilterMode.CollectedOnly
    ),
    JapaneseAsmr(
        labelProvider = { "jp-asmr" },
        icon = SearchFilterIcon.Drawable(R.drawable.ic_search_collected_library),
        mode = SearchFilterMode.CollectedOnly
    ),
    ChineseTranslated(
        labelProvider = { appString(R.string.ui_chinese_works) },
        icon = SearchFilterIcon.Drawable(R.drawable.ic_search_chinese_book),
        mode = SearchFilterMode.ChineseTranslated
    ),
    Standard(
        labelProvider = { appString(R.string.ui_all_works) },
        icon = SearchFilterIcon.Vector(Icons.Rounded.Search),
        mode = SearchFilterMode.Standard
    ),
    Presale(
        labelProvider = { appString(R.string.ui_pre_sale) },
        icon = SearchFilterIcon.Vector(Icons.Rounded.CalendarMonth),
        mode = SearchFilterMode.PresaleOnly
    ),
    PurchasedOnly(
        labelProvider = { appString(R.string.ui_purchased) },
        icon = SearchFilterIcon.Vector(Icons.Rounded.ShoppingBag),
        mode = SearchFilterMode.PurchasedOnly
    );

    val label: String get() = labelProvider()


    val isPurchasedOnly: Boolean
        get() = mode == SearchFilterMode.PurchasedOnly

    val isPresaleOnly: Boolean
        get() = mode == SearchFilterMode.PresaleOnly

    val isChineseTranslated: Boolean
        get() = mode == SearchFilterMode.ChineseTranslated

    val isCollectedOnly: Boolean
        get() = mode == SearchFilterMode.CollectedOnly

    val supportsWorkFilters: Boolean
        get() = mode == SearchFilterMode.CollectedOnly || mode == SearchFilterMode.Standard

    val collectedSource: CollectedSearchSource
        get() = if (this == JapaneseAsmr) CollectedSearchSource.JapaneseAsmr else CollectedSearchSource.AsmrOne

    val supportsSubtitleFilter: Boolean
        get() = supportsWorkFilters && this != JapaneseAsmr

    val collectedSortOptions: List<SearchCollectedSortOption>
        get() = if (this == JapaneseAsmr) listOf(SearchCollectedSortOption.ReleaseNew) else SearchCollectedSortOption.entries

    val supportsSortAndLanguageOptions: Boolean
        get() = mode != SearchFilterMode.PurchasedOnly && mode != SearchFilterMode.PresaleOnly

    companion object {
        fun fromState(
            purchasedOnly: Boolean,
            presaleOnly: Boolean,
            chineseTranslatedOnly: Boolean,
            collectedOnly: Boolean,
            collectedSourceName: String? = null
        ): SearchFilterOption {
            return when {
                purchasedOnly -> PurchasedOnly
                chineseTranslatedOnly -> ChineseTranslated
                presaleOnly -> Presale
                collectedOnly && CollectedSearchSource.fromName(collectedSourceName) == CollectedSearchSource.JapaneseAsmr -> JapaneseAsmr
                collectedOnly -> Collected
                else -> Standard
            }
        }
    }
}

enum class SearchFilterMode {
    Standard,
    PurchasedOnly,
    PresaleOnly,
    ChineseTranslated,
    CollectedOnly
}
