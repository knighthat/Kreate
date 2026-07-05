package app.kreate.android.viewmodel.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kreate.android.R
import app.kreate.android.utils.innertube.InnertubeUtils
import app.kreate.database.Database
import app.kreate.database.models.Album
import app.kreate.preferences.Preferences
import co.touchlab.kermit.Logger
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.pages.BrowseResult
import it.fast4x.rimusic.enums.AlbumsType
import it.fast4x.rimusic.enums.FilterBy
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
class HomeAlbumsViewModel : ViewModel(), KoinComponent {

    private val _syncedAlbums = MutableStateFlow(emptyList<Album>())
    private val _localAlbums = MutableStateFlow(emptyList<Album>())
    private val _albums = MutableStateFlow(emptyList<Album>())
    private val _isRefreshing = MutableStateFlow(false)

    val albums = _albums.asStateFlow()
    val isRefreshing = _isRefreshing.asStateFlow()

    init {
        viewModelScope.launch( Dispatchers.IO ) {
            combine( Preferences.HOME_ALBUM_TYPE, Preferences.HOME_ALBUMS_SORT_BY, Preferences.HOME_ALBUM_SORT_ORDER, ::Triple )
                .flatMapLatest { (type, sortBy, sortOrder) ->
                    when ( type ) {
                        AlbumsType.Favorites    -> Database.albumTable.sortBookmarked( sortBy, sortOrder )
                        AlbumsType.Library      -> Database.albumTable.sortInLibrary( sortBy, sortOrder )
                    }
                }
                .distinctUntilChanged()
                .collectLatest { localAlbums ->
                    _localAlbums.update { localAlbums }
                }
        }
        viewModelScope.launch( Dispatchers.Default ) {
            combine( _syncedAlbums, _localAlbums, Preferences.HOME_ARTIST_AND_ALBUM_FILTER ) { online, local, filterBy ->
                val combined = online + local

                when( filterBy ) {
                    FilterBy.All            -> combined
                    FilterBy.YoutubeLibrary -> combined.filter { it.isYoutubeAlbum }
                    FilterBy.Local          -> combined.filterNot { it.isYoutubeAlbum }
                }
            }.collectLatest { albums -> _albums.update { albums } }
        }

        // Trigger sync on first run
        onRefresh()
    }

    private suspend fun syncAlbums() {
        val isEnabled = withContext( Dispatchers.Main ) {
            InnertubeUtils.isLoggedIn && Preferences.YOUTUBE_ALBUMS_SYNC.value
        }
        if( !isEnabled ) return

        YouTube.browse( "FEmusic_library_landing", null )
               .onFailure { err ->
                   Logger.e( "", err, "HomeAlbum" )
                   Toaster.e(
                       R.string.error_failed_to_sync_tab,
                       get<Context>().getString( R.string.albums ).lowercase()
                   )
               }
               .onSuccess { result ->
                   result.items
                         .flatMap( BrowseResult.Item::items )
                         .mapNotNull { it as? AlbumItem }
                         .map { item ->
                             Album(
                                 id = item.id,
                                 title = item.title,
                                 thumbnailUrl = item.thumbnail,
                                 year = item.year?.toString(),
                                 authorsText = item.artists?.joinToString { it.name },
                                 shareUrl = item.shareLink,
                                 isYoutubeAlbum = true
                             )
                         }
                         .also { albums ->
                             _syncedAlbums.update { albums }
                         }
               }
    }

    fun onRefresh() {
        _isRefreshing.update { true }
        // Clear fetched artists to prevent stale items
        _syncedAlbums.update { emptyList() }
        viewModelScope.launch {
            syncAlbums()
            _isRefreshing.update { false }
        }
    }
}