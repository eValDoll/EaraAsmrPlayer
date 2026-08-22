package com.asmr.player.ui.search

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchFilterOptionSupportTest {
    @Test
    fun supportsWorkFilters_onlyForCollectedAndStandardScopes() {
        assertTrue(SearchFilterOption.Collected.supportsWorkFilters)
        assertTrue(SearchFilterOption.Standard.supportsWorkFilters)
        assertFalse(SearchFilterOption.ChineseTranslated.supportsWorkFilters)
        assertFalse(SearchFilterOption.Presale.supportsWorkFilters)
        assertFalse(SearchFilterOption.PurchasedOnly.supportsWorkFilters)
    }
}
