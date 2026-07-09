package app.kreate.android.viewmodel.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kreate.android.R
import app.kreate.android.utils.innertube.InnertubeUtils
import app.kreate.database.Database
import app.kreate.database.models.Playlist
import app.kreate.database.models.PlaylistPreview
import app.kreate.gateway.innertube.YouTube
import app.kreate.preferences.Preferences
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.knighthat.utils.Toaster
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

@OptIn(ExperimentalCoroutinesApi::class)
class HomeLibraryViewModel : ViewModel(), KoinComponent {

    private val _syncedPlaylists = MutableStateFlow(emptyList<PlaylistPreview>())
    private val _localPlaylists = MutableStateFlow(emptyList<PlaylistPreview>())
    private val _playlists = MutableStateFlow(emptyList<PlaylistPreview>())
    private val _isRefreshing = MutableStateFlow(false)

    val playlists = _playlists.asStateFlow()
    val isRefreshing = _isRefreshing.asStateFlow()

    init {
        viewModelScope.launch( Dispatchers.IO ) {
            combine( Preferences.HOME_LIBRARY_SORT_BY, Preferences.HOME_LIBRARY_SORT_ORDER ) { a, b -> a to b }
                .flatMapLatest { (sortBy, sortOrder) ->
                    Database.playlistTable.sortPreviews( sortBy, sortOrder )
                }
                .distinctUntilChanged()
                .collectLatest { localPlaylists ->
                    _localPlaylists.update { localPlaylists }
                }
        }
        viewModelScope.launch( Dispatchers.Default ) {
            combine( _syncedPlaylists, _localPlaylists ) { online, local -> online + local }
                .collectLatest { playlists ->
                    _playlists.update { playlists }
                }
        }

        // Trigger sync on first run
        onRefresh()
    }

    private suspend fun syncPlaylists() {
        val isEnabled = withContext( Dispatchers.Main ) {
            InnertubeUtils.isLoggedIn && Preferences.YOUTUBE_PLAYLISTS_SYNC.value
        }
        if( !isEnabled ) return

        get<YouTube>()
            .account
            .getLikedPlaylists()
            .onFailure { err ->
               Logger.e( "", err, "HomePlaylist" )
               Toaster.e(
                   R.string.error_failed_to_sync_tab,
                   get<Context>().getString( R.string.playlists ).lowercase()
               )
            }
            .onSuccess { result ->
               result.map { item ->
                         PlaylistPreview(
                             playlist = Playlist(
                                 name = item.name,
                                 browseId = item.id,
                                 isEditable = false,
                                 isYoutubePlaylist = true,
                                 isPinned = false,
                                 isMonthly = false
                             ),
                             songCount = -1,
                             thumbnailUrl = item.thumbnails.lastOrNull()?.url
                         )
                     }
                     .also { playlists ->
                         _syncedPlaylists.update { playlists }
                     }
            }
    }

    fun onRefresh() {
        _isRefreshing.update { true }
        // Clear fetched artists to prevent stale items
        _syncedPlaylists.update { emptyList() }
        viewModelScope.launch {
            syncPlaylists()
            _isRefreshing.update { false }
        }
    }
}