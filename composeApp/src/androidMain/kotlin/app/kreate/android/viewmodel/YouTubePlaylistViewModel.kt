package app.kreate.android.viewmodel

import android.content.Context
import androidx.annotation.AnyThread
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastMap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.themed.common.component.LoadMoreContentType
import app.kreate.android.themed.rimusic.component.Search
import app.kreate.android.utils.innertube.CURRENT_LOCALE
import app.kreate.android.utils.innertube.toSong
import app.kreate.database.models.Song
import app.kreate.util.EXPLICIT_PREFIX
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import it.fast4x.rimusic.utils.isNetworkConnected
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.knighthat.innertube.Innertube
import me.knighthat.innertube.model.InnertubePlaylist
import me.knighthat.innertube.model.InnertubeSong
import me.knighthat.utils.Toaster
import timber.log.Timber
import javax.inject.Inject


@HiltViewModel
class YouTubePlaylistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val _playlistPage = MutableStateFlow<InnertubePlaylist?>(null)
    private val _continued = MutableStateFlow(emptyList<InnertubeSong>())
    private val _continuation = MutableStateFlow<String?>(null)

    // browseId must not be empty or null in any case
    val browseId: String = savedStateHandle["browseId"]!!
    val params: String? = savedStateHandle["params"]
    val useLogin: Boolean = savedStateHandle["useLogin"]!!
    val listState = LazyListState()
    val search = Search(listState)
    val playlistPage = _playlistPage.asStateFlow()
    val continuation = _continuation.asStateFlow()
    val songs: StateFlow<List<Song>>
    val hasMore: StateFlow<Boolean>

    init {
        //<editor-fold desc="Fetch more at the end of list">
        viewModelScope.launch { // Interact with UI component [listState], keep it on Main thread
            snapshotFlow { listState.layoutInfo.visibleItemsInfo }
                .filter { info ->
                    info.fastAny { it.contentType === LoadMoreContentType }
                }
                .collectLatest { onGetMore() }
        }
        //</editor-fold>
        //<editor-fold desc="Combine initial song list and its continuation + filter search">
        this.songs = combine( _playlistPage, _continued ) { page, continued ->
                page?.songs.orEmpty() + continued
            }
            .combine( snapshotFlow { search.input } ) { songs, input ->
                songs.fastFilter {
                         !Preferences.PARENTAL_CONTROL.value
                                 || !it.name.startsWith( EXPLICIT_PREFIX, true )
                     }
                     .fastFilter {
                         val query = input.text
                         it.name.contains( query, true )
                                 || it.artistsText.contains( query, true )
                     }
            }
            .distinctUntilChanged()
            .map { it.fastMap(InnertubeSong::toSong ) }
            .flowOn( Dispatchers.Default )
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = emptyList()
            )
        //</editor-fold>
        this.hasMore = _continuation.map { it != null }
                                    .stateIn(
                                        scope = viewModelScope,
                                        started = SharingStarted.Lazily,
                                        initialValue = false
                                    )
    }

    @AnyThread
    fun onFetch() = viewModelScope.launch( Dispatchers.IO ) {
        if( !isNetworkConnected(context) ) {
            Toaster.noInternet()
            return@launch
        }

        Innertube.browsePlaylist( browseId, CURRENT_LOCALE, useLogin )
                 .onSuccess { page ->
                     _playlistPage.update { page }
                     _continuation.update { page.songContinuation }
                 }
                 .onFailure { err ->
                     Timber.tag( "YouTubePlaylist" ).e( err )
                     Toaster.e( R.string.error_failed_to_load_playlist )
                 }
    }

    @AnyThread
    fun onGetMore() = viewModelScope.launch( Dispatchers.IO ) {
        if( !isNetworkConnected(context) ) {
            Toaster.noInternet()
            return@launch
        }

        // Capture values here to guarantee no race-condition can happen after this
        val continuation = _continuation.value
        val visitorData = _playlistPage.value?.visitorData
        if( continuation == null || (visitorData == null && !useLogin) ) {
            Toaster.w( R.string.warning_end_of_list )
            return@launch
        }

        Innertube.playlistContinued(
            _playlistPage.value?.visitorData,
            continuation,
            CURRENT_LOCALE,
            params,
            useLogin
        ).onSuccess { continued ->
            _continued.update { it + continued.songs }
            _continuation.update { continued.continuation }
        }.onFailure { err ->
            Timber.tag( "YouTubePlaylist" ).e( err )
            Toaster.e( R.string.error_failed_to_get_playlists_next_songs )
        }
    }
}