package com.asmr.player.util

import org.junit.Assert.assertEquals
import org.junit.Test

class SmartSortKeyTest {
    @Test
    fun chapterNumber_sortsNaturally() {
        val input = listOf("第1章", "第10章", "第2章")
        val out = input.sortedBy { SmartSortKey.of(it) }
        assertEquals(listOf("第1章", "第2章", "第10章"), out)
    }

    @Test
    fun chineseOrdinal_sortsNaturally() {
        val input = listOf("第一回", "第十回", "第十一回", "第二回")
        val out = input.sortedBy { SmartSortKey.of(it) }
        assertEquals(listOf("第一回", "第二回", "第十回", "第十一回"), out)
    }

    @Test
    fun chineseNumber_sortsNaturally() {
        val input = listOf("二十三", "十一", "二", "十", "三")
        val out = input.sortedBy { SmartSortKey.of(it) }
        assertEquals(listOf("二", "三", "十", "十一", "二十三"), out)
    }

    @Test
    fun arabicNumberWithPunctuation_sortsNaturally() {
        val input = listOf("1.", "10.", "2.")
        val out = input.sortedBy { SmartSortKey.of(it) }
        assertEquals(listOf("1.", "2.", "10."), out)
    }

    @Test
    fun fullWidthDigits_sortsNaturally() {
        val input = listOf("０１", "１０", "０２")
        val out = input.sortedBy { SmartSortKey.of(it) }
        assertEquals(listOf("０１", "０２", "１０"), out)
    }

    @Test
    fun mixedEnglish_sortsNaturally() {
        val input = listOf("Track 2", "Track 10", "Track 1")
        val out = input.sortedBy { SmartSortKey.of(it) }
        assertEquals(listOf("Track 1", "Track 2", "Track 10"), out)
    }
}

