package com.asmr.player.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import com.asmr.player.ui.createEaraComposeRule
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ActivePagingCollectionTest {
    @get:Rule
    val composeRule = createEaraComposeRule()

    @Test
    fun inactiveInitialCompositionDoesNotCollectTheSource() {
        val source = MutableSharedFlow<PagingData<String>>(replay = 1)
        source.tryEmit(PagingData.from(listOf("cached")))
        lateinit var items: LazyPagingItems<String>

        composeRule.setContent {
            items = source.collectAsLazyPagingItemsWhileActive(isActive = false)
        }

        composeRule.runOnIdle {
            assertEquals(0, source.subscriptionCount.value)
            assertEquals(0, items.itemCount)
        }
    }

    @Test
    fun leavingCancelsCollectionAndRetainsItemsUntilReactivation() {
        val source = MutableSharedFlow<PagingData<String>>(replay = 1)
        source.tryEmit(PagingData.from(listOf("cached")))
        var active by mutableStateOf(true)
        lateinit var items: LazyPagingItems<String>

        composeRule.setContent {
            items = source.collectAsLazyPagingItemsWhileActive(isActive = active)
        }

        composeRule.runOnIdle {
            assertEquals(1, source.subscriptionCount.value)
            assertEquals(listOf("cached"), items.itemSnapshotList.items)
        }
        val retained = items
        composeRule.runOnIdle { active = false }
        composeRule.runOnIdle {
            assertEquals(0, source.subscriptionCount.value)
            assertSame(retained, items)
            source.tryEmit(PagingData.from(listOf("updated")))
        }
        composeRule.runOnIdle {
            assertEquals(listOf("cached"), items.itemSnapshotList.items)
            active = true
        }
        composeRule.runOnIdle {
            assertEquals(1, source.subscriptionCount.value)
            assertEquals(listOf("updated"), items.itemSnapshotList.items)
        }
    }
}
