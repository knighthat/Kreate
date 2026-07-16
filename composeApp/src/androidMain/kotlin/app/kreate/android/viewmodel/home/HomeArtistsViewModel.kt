package app.kreate.android.viewmodel.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kreate.android.utils.innertube.InnertubeUtils
import app.kreate.compose.R
import app.kreate.database.Database
import app.kreate.database.models.Artist
import app.kreate.gateway.innertube.YouTube
import app.kreate.preferences.Preferences
import co.touchlab.kermit.Logger
import it.fast4x.rimusic.enums.ArtistsType
import it.fast4x.rimusic.enums.FilterBy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
class HomeArtistsViewModel(
    private val youtube: YouTube
) : ViewModel(), KoinComponent {

    private val _syncedArtists = MutableStateFlow(emptyList<Artist>())
    private val _localArtists = MutableStateFlow(emptyList<Artist>())
    private val _artists = MutableStateFlow(emptyList<Artist>())
    private val _isRefreshing = MutableStateFlow(false)

    val artists = _artists.asStateFlow()
    val isRefreshing = _isRefreshing.asStateFlow()

    init {
        viewModelScope.launch( Dispatchers.IO ) {
            combine( Preferences.HOME_ARTIST_TYPE, Preferences.HOME_ARTISTS_SORT_BY, Preferences.HOME_ARTISTS_SORT_ORDER, ::Triple )
                .flatMapLatest { (type, sortBy, sortOrder) ->
                    when( type ) {
                        ArtistsType.Favorites -> Database.artistTable.sortFollowing( sortBy, sortOrder )
                        ArtistsType.Library -> Database.artistTable.sortInLibrary( sortBy, sortOrder )
                    }
                }
                .distinctUntilChanged()
                .collectLatest { localAlbums ->
                    _localArtists.update { localAlbums }
                }
        }
        viewModelScope.launch( Dispatchers.Default ) {
            combine( _syncedArtists, _localArtists, Preferences.HOME_ARTIST_AND_ALBUM_FILTER ) { online, local, filterBy ->
                val combined = online + local

                when( filterBy ) {
                    FilterBy.All            -> combined
                    FilterBy.YoutubeLibrary -> combined.filter { it.isYoutubeArtist }
                    FilterBy.Local          -> combined.filterNot { it.isYoutubeArtist }
                }
            }.collectLatest { artists -> _artists.update { artists } }
        }
        viewModelScope.launch( Dispatchers.Default ) {
            artists.collectLatest { items ->
                updateNullThumbnails( items )
            }
        }

        // Trigger sync on first run
        onRefresh()
    }

    private suspend fun syncArtists() {
        val isEnabled = withContext( Dispatchers.Main ) {
            InnertubeUtils.isLoggedIn && Preferences.YOUTUBE_ARTISTS_SYNC.value
        }
        if( !isEnabled ) return

        youtube
            .account
            .getLikedArtists()
            .onFailure { err ->
               Logger.e( "", err, "HomeArtists" )
               Toaster.e(
                   R.string.error_failed_to_sync_tab,
                   get<Context>().getString( R.string.artists ).lowercase()
               )
            }
            .onSuccess { result ->
               result.map { item ->
                         Artist(
                             id = item.id,
                             name = item.name,
                             thumbnailUrl = item.thumbnails.lastOrNull()?.url,
                             isYoutubeArtist = true
                         )
                     }
                     .also { artists ->
                         _syncedArtists.update { artists }
                     }
            }
    }

    private suspend fun updateNullThumbnails( artists: List<Artist> ) = coroutineScope {
        artists.filter { it.thumbnailUrl.isNullOrEmpty() }
               // Fetch all artist at concurrently. Ktor has rate limit, so only
               // a certain amount of requests can go out at the same time
               .map { artist ->
                   async {
                       youtube.getArtist( artist.id, null )
                              .onFailure { err ->
                                  Logger.w( err, "HomeArtistsViewModel" ) { "Failed to fetch thumbnail for: ${artist.name} (${artist.id})" }
                              }
                              .onSuccess { onlineArtist ->
                                  val thumbnailUrl = onlineArtist.thumbnails.lastOrNull()?.url
                                  Database.artistTable.updateIgnore( artist.copy(thumbnailUrl = thumbnailUrl) )
                              }
                   }
               }
               .forEach { it.await() }
    }

    fun onRefresh() {
        _isRefreshing.update { true }
        // Clear fetched artists to prevent stale items
        _syncedArtists.update { emptyList() }
        viewModelScope.launch {
            syncArtists()
            _isRefreshing.update { false }
        }
    }
}