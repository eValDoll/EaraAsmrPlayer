package com.asmr.player.ui.search

enum class SearchSortOption(
    val label: String,
    val dlsiteOrder: String
) {
    Trend("人气顺序", "trend"),
    ReleaseNew("发售日期最新", "release_d"),
    ReleaseOld("发售日期最旧", "release"),
    DLCount("销量最高", "dl_d"),
    PriceLow("价格最低", "price"),
    PriceHigh("价格最高", "price_d"),
    Rating("评分最高", "rate_d"),
    ReviewCount("赏析最多", "review_d")
}

