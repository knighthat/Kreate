package app.kreate.android.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kreate.android.themed.rimusic.component.playlist.PlaylistSongsSort
import app.kreate.database.models.Playlist
import app.kreate.database.models.Song
import app.kreate.preferences.Preferences
import co.touchlab.kermit.Logger
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.bodies.NextBody
import it.fast4x.innertube.requests.relatedSongs
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.ui.components.MenuState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class LocalPlaylistViewModel(
    savedStateHandle: SavedStateHandle,
    menuState: MenuState
) : ViewModel() {

    private val _relatedSongs = MutableStateFlow(emptyMap<Song, Int>())
    private val _items = MutableStateFlow(emptyList<Song>())

    val playlistId: Long = savedStateHandle["id"]!!
    val relatedSongs: StateFlow<Map<Song, Int>> = _relatedSongs.asStateFlow()
    val items: StateFlow<List<Song>> = _items.asStateFlow()
    val sort: PlaylistSongsSort = PlaylistSongsSort(viewModelScope, menuState)
    val playlist: StateFlow<Playlist?> =
        Database.playlistTable
                .findById( playlistId )
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.Eagerly,
                    initialValue = null
                )

    init {
        viewModelScope.launch {
            combine(
                Preferences.LOCAL_PLAYLIST_SMART_RECOMMENDATION,
                Preferences.MAX_NUMBER_OF_SMART_RECOMMENDATIONS
            ) { enabled, count ->
                if( !enabled )
                    return@combine emptyMap()

                val items = items.value

                /*
                    This process will be run before [items]
                       most of the time.
                    When it does, an exception will
                       be thrown because [items] is not ready yet.
                    To make sure that it is ready to use, a
                       delay is set to suspend the thread.
                */
                while( items.isEmpty() )
                    delay( 100L )

                withContext( Dispatchers.IO ) {
                    val requestBody = NextBody( videoId =  items.random().id )
                    Innertube.relatedSongs( requestBody )
                             ?.getOrNull()      // If result is null, all subsequence calls are cancelled
                             ?.songs
                             ?.filterNot { songItem ->
                                 // Fetched Song may not have properties like [likedAt]
                                 // so the result of [List.any] may be false.
                                 // Comparing their IDs is the most effective way
                                 items.map( Song::id )
                                     .any{ songItem.info?.endpoint?.videoId == it }
                             }
                             ?.take( count )
                             ?.associate { songItem ->
                                 with( songItem ) {
                                     Song(
                                         // Song's ID & title must not be "null". If they are,
                                         // Something is wrong with Innertube.
                                         id = info!!.endpoint!!.videoId!!,
                                         title = info!!.name!!,
                                         artistsText = authors?.joinToString { author -> author.name ?: "" },
                                         durationText = durationText,
                                         thumbnailUrl = thumbnail?.url,
                                         isExplicit = explicit
                                     ) to (0..items.size).random()      // Map this song with a random position from [items]
                                 }
                             }
                             .orEmpty()
                }
            }.collectLatest { songs ->
                _relatedSongs.update { songs }
            }
        }
        viewModelScope.launch {
            try {
                Database.songPlaylistMapTable
                        .sortSongs( playlistId, sort.sortBy, sort.sortOrder )
            } catch( err: Exception ) {
                Logger.e( "", err, "LocalPlaylist" )

                // This steps fails mostly because a ghost map lingers
                // after a failed deletion or failed attempt of insertion
                Database.songPlaylistMapTable.clearGhostMaps()

                Database.songPlaylistMapTable
                        .sortSongs( playlistId, sort.sortBy, sort.sortOrder )
            }
                .flowOn( Dispatchers.IO )
                .distinctUntilChanged()
                .collectLatest { newList ->
                    _items.update { newList }
                }
        }
    }
}