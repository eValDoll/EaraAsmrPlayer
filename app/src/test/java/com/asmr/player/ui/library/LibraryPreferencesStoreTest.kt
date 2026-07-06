package com.asmr.player.ui.library

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class LibraryPreferencesStoreTest {
    private val context = RuntimeEnvironment.getApplication()
    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
        scopes.clear()
    }

    private data class TestStore(
        val store: LibraryPreferencesStore,
        val dataStore: DataStore<Preferences>
    )

    private fun createStore(name: String = "library-${System.nanoTime()}"): TestStore {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scopes += scope
        val file = File(context.cacheDir, "$name.preferences_pb")
        runCatching { file.delete() }
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file }
        )
        return TestStore(
            store = LibraryPreferencesStore(context = context, dataStore = dataStore),
            dataStore = dataStore
        )
    }

    @Test
    fun sort_defaultsToAddedDescWhenUnset() = runBlocking {
        val store = createStore().store

        assertEquals(LibrarySort.AddedDesc, store.sort.first())
    }

    @Test
    fun sort_roundTripsStoredValue() = runBlocking {
        val store = createStore().store

        store.setSort(LibrarySort.LastPlayedDesc)

        assertEquals(LibrarySort.LastPlayedDesc, store.sort.first())
    }

    @Test
    fun sort_fallsBackWhenStoredValueIsInvalid() = runBlocking {
        val testStore = createStore()
        val store = testStore.store

        testStore.dataStore.edit { prefs ->
            prefs[stringPreferencesKey("library_sort_v1")] = "MissingSort"
        }

        assertEquals(LibrarySort.AddedDesc, store.sort.first())
    }

    @Test
    fun filters_roundTripOnlyAppliedFilterFields() = runBlocking {
        val store = createStore().store
        val filters = PersistedLibraryFilters(
            includeTagIds = setOf(7L, -1L),
            excludeTagIds = setOf(9L),
            circles = setOf(" 社团A ", ""),
            cvs = setOf("CV A"),
            source = LibrarySourceFilter.LocalOnly
        )

        store.setFilters(filters)

        assertEquals(
            PersistedLibraryFilters(
                includeTagIds = setOf(7L),
                excludeTagIds = setOf(9L),
                circles = setOf("社团A"),
                cvs = setOf("CV A"),
                source = LibrarySourceFilter.LocalOnly
            ),
            store.filters.first()
        )
    }

    @Test
    fun filters_emptyHasNoActiveFilters() {
        assertEquals(false, PersistedLibraryFilters.Empty.hasActiveFilters)
        assertEquals(LibraryQuerySpec(), PersistedLibraryFilters.Empty.toQuerySpec())
    }

    @Test
    fun filters_dropDeletedTagIdsWhenValidIdsAreProvided() {
        val filters = PersistedLibraryFilters(
            includeTagIds = setOf(1L, 2L),
            excludeTagIds = setOf(3L),
            circles = setOf("社团A")
        )

        assertEquals(
            PersistedLibraryFilters(
                includeTagIds = setOf(2L),
                circles = setOf("社团A")
            ),
            filters.normalized(validTagIds = setOf(2L))
        )
    }
}
