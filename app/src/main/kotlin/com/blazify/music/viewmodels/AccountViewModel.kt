/**
 * Blazify Project (C) 2026
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See NOTICE for contributors
 */

package com.blazify.music.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blazify.innertube.YouTube
import com.blazify.innertube.models.AlbumItem
import com.blazify.innertube.models.ArtistItem
import com.blazify.innertube.models.PlaylistItem
import com.blazify.music.db.MusicDatabase
import com.blazify.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AccountContentType {
    PLAYLISTS, ALBUMS, ARTISTS, PODCASTS
}

@HiltViewModel
class AccountViewModel @Inject constructor(
    database: MusicDatabase,
) : ViewModel() {
    val playlists = MutableStateFlow<List<PlaylistItem>?>(null)
    val albums = MutableStateFlow<List<AlbumItem>?>(null)
    val artists = MutableStateFlow<List<ArtistItem>?>(null)
    // SE "Episodes for Later" playlist shown in Podcasts tab
    val sePlaylist = MutableStateFlow<PlaylistItem?>(null)
    // RDPN "New Episodes" playlist (real thumbnail + count from YouTube)
    val rdpnPlaylist = MutableStateFlow<PlaylistItem?>(null)
    // Subscribed podcast shows (from local DB, synced from YT Music)
    val podcastPlaylists = database.subscribedPodcasts()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    // Podcast host channels from YT Music library
    val podcastChannels = MutableStateFlow<List<ArtistItem>>(emptyList())

    // Selected content type for chips
    val selectedContentType = MutableStateFlow(AccountContentType.PLAYLISTS)

    init {
        // Keep the account connection for search, but do not fetch the user's
        // personal YouTube Music playlists, albums or followed artists here.
        viewModelScope.launch {
            YouTube.newEpisodesPlaylistInfo().onSuccess {
                rdpnPlaylist.value = it
            }.onFailure {
                reportException(it)
            }
        }
    }

    fun setSelectedContentType(contentType: AccountContentType) {
        selectedContentType.value = contentType
    }
}
