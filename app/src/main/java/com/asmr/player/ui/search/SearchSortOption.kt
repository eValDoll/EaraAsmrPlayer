package com.asmr.player.ui.search

import com.asmr.player.i18n.appString
import com.asmr.player.R

import com.asmr.player.domain.model.CollectedSearchSource

internal fun normalizedCollectedSort(sourceName: String?, sort: SearchCollectedSortOption): SearchCollectedSortOption =
    if (CollectedSearchSource.fromName(sourceName) == CollectedSearchSource.JapaneseAsmr) SearchCollectedSortOption.ReleaseNew else sort

enum class SearchSortOption(
    private val labelProvider: () -> String,
    val dlsiteOrder: String
) {
    Trend( { appString(R.string.ui_popularity_order) }, "trend"),
    ReleaseNew( { appString(R.string.ui_latest_sale) }, "release_d"),
    DLCount( { appString(R.string.ui_highest_sales_volume) }, "dl_d"),
    PriceHigh( { appString(R.string.ui_highest_price) }, "price_d");

    val label: String get() = labelProvider()
}

enum class SearchCollectedSortOption(
    private val labelProvider: () -> String,
    val backendSort: String
) {
    ReleaseNew( { appString(R.string.ui_latest_sale) }, "release"),
    CollectedNew( { appString(R.string.ui_latest_collection) }, "create_date"),
    RatingHigh( { appString(R.string.ui_top_rated) }, "rating");

    val label: String get() = labelProvider()


    companion object {
        fun fromName(name: String?): SearchCollectedSortOption {
            return values().firstOrNull { it.name == name } ?: ReleaseNew
        }
    }
}
