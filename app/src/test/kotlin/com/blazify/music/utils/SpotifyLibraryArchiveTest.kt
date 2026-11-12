package com.blazify.music.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyLibraryArchiveTest {
    @Test
    fun parsesLikedSongsFromYourLibraryExport() {
        val collections = SpotifyLibraryArchive.parse(
            mapOf(
                "YourLibrary.json" to """
                    [
                      {"artist":"Daft Punk","track":"Harder, Better, Faster, Stronger","album":"Discovery","uri":"spotify:track:1"},
                      {"artist":"The Strokes","track":"Reptilia","album":"Room on Fire","uri":"spotify:track:2"}
                    ]
                """.trimIndent(),
            ),
        )

        assertEquals(1, collections.size)
        assertEquals("Liked Songs", collections.single().name)
        assertEquals(listOf("Harder, Better, Faster, Stronger", "Reptilia"), collections.single().tracks.map { it.title })
        assertEquals("Daft Punk", collections.single().tracks.first().artists)
    }

    @Test
    fun parsesPlaylistExportWithNestedTrackObjects() {
        val collections = SpotifyLibraryArchive.parse(
            mapOf(
                "Playlist1.json" to """
                    {
                      "playlists": [{
                        "name": "Entrenamiento",
                        "items": [
                          {"track": {"trackName": "Song One", "artistName": "Artist One", "trackUri": "spotify:track:1"}},
                          {"track": {"trackName": "Song Two", "artists": [{"name": "Artist Two"}]}}
                        ]
                      }]
                    }
                """.trimIndent(),
            ),
        )

        assertEquals(1, collections.size)
        assertEquals("Entrenamiento", collections.single().name)
        assertEquals(2, collections.single().tracks.size)
        assertEquals("Artist Two", collections.single().tracks.last().artists)
    }

    @Test
    fun ignoresUnrelatedJsonAndEmptyCollections() {
        val collections = SpotifyLibraryArchive.parse(
            mapOf(
                "StreamingHistory.json" to """[{"trackName":"Not a library item"}]""",
                "Playlist2.json" to """{"playlists":[{"name":"Empty","items":[]}]}""",
            ),
        )

        assertTrue(collections.isEmpty())
    }

    @Test
    fun removesDuplicateSavedTracks() {
        val collections = SpotifyLibraryArchive.parse(
            mapOf(
                "YourLibrary.json" to """
                    [
                      {"artist":"Artist","track":"Same Song"},
                      {"artist":"Artist","track":"Same Song"}
                    ]
                """.trimIndent(),
            ),
        )

        assertEquals(1, collections.single().tracks.size)
    }
}
