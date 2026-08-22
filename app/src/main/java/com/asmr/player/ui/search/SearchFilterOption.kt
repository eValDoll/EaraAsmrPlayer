package com.asmr.player.ui.search

import androidx.annotation.DrawableRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.ui.graphics.vector.ImageVector
import com.asmr.player.R

sealed class SearchFilterIcon {
    data class Vector(val imageVector: ImageVector) : SearchFilterIcon()

    data class Drawable(@DrawableRes val resId: Int) : SearchFilterIcon()
}

enum class SearchFilterOption(
    val label: String,
    val icon: SearchFilterIcon,
    val mode: SearchFilterMode
) {
    Collected(
        label = "已收录",
        icon = SearchFilterIcon.Drawable(R.drawable.ic_search_collected_library),
        mode = SearchFilterMode.CollectedOnly
    ),
    ChineseTranslated(
        label = "中文作品",
        icon = SearchFilterIcon.Drawable(R.drawable.ic_search_chinese_book),
        mode = SearchFilterMode.ChineseTranslated
    ),
    Standard(
        label = "全部作品",
        icon = SearchFilterIcon.Vector(Icons.Rounded.Search),
        mode = SearchFilterMode.Standard
    ),
    Presale(
        label = "预售",
        icon = SearchFilterIcon.Vector(Icons.Rounded.CalendarMonth),
        mode = SearchFilterMode.PresaleOnly
    ),
    PurchasedOnly(
        label = "已购",
        icon = SearchFilterIcon.Vector(Icons.Rounded.ShoppingBag),
        mode = SearchFilterMode.PurchasedOnly
    );

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

    companion object {
        fun fromState(
            purchasedOnly: Boolean,
            presaleOnly: Boolean,
            chineseTranslatedOnly: Boolean,
            collectedOnly: Boolean
        ): SearchFilterOption {
            return when {
                purchasedOnly -> PurchasedOnly
                chineseTranslatedOnly -> ChineseTranslated
                presaleOnly -> Presale
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
