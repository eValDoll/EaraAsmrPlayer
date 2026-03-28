package com.asmr.player.benchmark

import android.content.Intent

enum class BenchmarkScenario(
    val value: String
) {
    LibraryAlbums("library_albums"),
    LibraryTracks("library_tracks"),
    SearchNetwork("search_network"),
    FavoritesDetail("favorites_detail"),
    PlaylistsList("playlists_list"),
    PlaylistDetail("playlist_detail"),
    PlaylistPicker("playlist_picker"),
    GroupsList("groups_list"),
    GroupDetail("group_detail"),
    GroupPicker("group_picker"),
    Queue("queue");

    companion object {
        const val EXTRA_SCENARIO = "benchmark_scenario"

        fun fromIntent(intent: Intent?): BenchmarkScenario {
            val raw = intent?.getStringExtra(EXTRA_SCENARIO).orEmpty()
            return values().firstOrNull { it.value == raw } ?: LibraryAlbums
        }
    }
}
