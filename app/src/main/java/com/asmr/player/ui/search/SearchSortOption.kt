package com.asmr.player.ui.search

enum class SearchSortOption(
    val label: String,
    val dlsiteOrder: String
) {
    Trend("人气顺序", "trend"),
    ReleaseNew("最新发售", "release_d"),
    DLCount("销量最高", "dl_d"),
    PriceHigh("价格最高", "price_d")
}

enum class SearchCollectedSortOption(
    val label: String,
    val backendSort: String
) {
    ReleaseNew("最新发售", "release"),
    CollectedNew("最新收录", "create_date"),
    RatingHigh("评分最高", "rating");

    companion object {
        fun fromName(name: String?): SearchCollectedSortOption {
            return values().firstOrNull { it.name == name } ?: ReleaseNew
        }
    }
}
