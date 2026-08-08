package com.asmr.player.subtitle

import org.junit.Assert.assertEquals
import org.junit.Test

class DeepSeekAccountFormattingTest {
    @Test
    fun tokenTotal_usesCompactDecimalUnits() {
        assertEquals("999", formatDeepSeekTokenTotal(999L))
        assertEquals("1K", formatDeepSeekTokenTotal(1_000L))
        assertEquals("12.3K", formatDeepSeekTokenTotal(12_345L))
        assertEquals("1M", formatDeepSeekTokenTotal(1_000_000L))
        assertEquals("2.5B", formatDeepSeekTokenTotal(2_500_000_000L))
    }

    @Test
    fun balances_formatsKnownCurrenciesAndFallback() {
        assertEquals(
            "¥12.35 / $3 / JPY 90",
            formatDeepSeekBalances(
                listOf(
                    DeepSeekBalance("CNY", "12.345"),
                    DeepSeekBalance("USD", "3.00"),
                    DeepSeekBalance("JPY", "90")
                )
            )
        )
        assertEquals("--", formatDeepSeekBalances(emptyList()))
    }
}
