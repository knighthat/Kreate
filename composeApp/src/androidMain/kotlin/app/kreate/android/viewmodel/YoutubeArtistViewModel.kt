package app.kreate.android.viewmodel

import android.content.Context
import androidx.annotation.MainThread
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFlatMap
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastMapNotNull
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import app.kreate.android.R
import app.kreate.android.utils.innertube.CURRENT_LOCALE
import app.kreate.android.utils.innertube.toMediaItem
import app.kreate.android.utils.innertube.toSong
import app.kreate.database.models.Artist
import app.kreate.database.models.Song
import app.kreate.database.models.SongArtistMap
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.utils.asMediaItem
import it.fast4x.rimusic.utils.isNetworkConnected
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.knighthat.innertube.Innertube
import me.knighthat.innertube.model.InnertubeArtist
import me.knighthat.innertube.model.InnertubeSong
import me.knighthat.utils.PropUtils
import me.knighthat.utils.Toaster
import timber.log.Timber
import javax.inject.Inject


@HiltViewModel
class YoutubeArtistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {

    private val _artistPage = MutableStateFlow<InnertubeArtist?>(null)

    // browseId must not be empty or null in any case
    val browseId: String = savedStateHandle["browseId"]!!
    val params: String? = savedStateHandle["params"]
    val listState = LazyListState()
    val sectionTextModifier = Modifier.padding( 16.dp, 24.dp, 16.dp, 8.dp )
    var tabIndex by mutableIntStateOf( if( isNetworkConnected(context) ) 0 else 1 )
    val artistPage = _artistPage.asStateFlow()
    val dbArtist: StateFlow<Artist?>
    val artistLibrarySongs: StateFlow<List<Song>>

    init {
        this.dbArtist = Database.artistTable
                                .findById(browseId)
                                .stateIn(
                                    scope = viewModelScope,
                                    started = SharingStarted.Lazily,
                                    initialValue = null
                                )
        this.artistLibrarySongs = Database.songArtistMapTable
                                          .findArtistMostPlayedSongs(browseId)
                                          .stateIn(
                                              scope = viewModelScope,
                                              started = SharingStarted.Lazily,
                                              initialValue = emptyList()
                                          )
    }

    var isRefreshing by mutableStateOf( false )

    private fun updateArtistInDatabase(
        dbArtist: Artist,
        innertubeArtist: InnertubeArtist
    ) = Database.asyncTransaction {
        val onlineArtist = Artist(
            id = innertubeArtist.id,
            name = PropUtils.retainIfModified( dbArtist.name, innertubeArtist.name ),
            thumbnailUrl = PropUtils.retainIfModified( dbArtist.thumbnailUrl, innertubeArtist.thumbnails.firstOrNull()?.url ),
            timestamp = dbArtist.timestamp ?: System.currentTimeMillis(),
            bookmarkedAt = dbArtist.bookmarkedAt,
            isYoutubeArtist = true
        )

        // Upsert to override/update default values
        artistTable.upsert( onlineArtist )

        // Map ignore to make sure only positions
        // are overridden, not the songs themselves
        innertubeArtist.sections
                       .fastFlatMap { section ->
                           section.contents
                                  // Only take InnertubeSongs and turn them into media items
                                  .fastMapNotNull {
                                      (it as? InnertubeSong)?.toMediaItem
                                  }
                       }
                       .onEach( ::insertIgnore )
                       .fastMap { mediaItem ->
                           SongArtistMap(
                               songId = mediaItem.mediaId,
                               artistId = innertubeArtist.id
                           )
                       }
                       .also( songArtistMapTable::upsert )
    }

    @MainThread
    fun onRefresh() {
        isRefreshing = true

        if( !isNetworkConnected( context ) ) {
            Toaster.noInternet()
            isRefreshing = false
            return
        }

        viewModelScope.launch( Dispatchers.IO ) {
            Innertube.browseArtist( browseId, CURRENT_LOCALE, params )
                     .onSuccess { page ->
                         _artistPage.update { page }

                         // Prevent artist from being inserted
                         // into the database. Limit to following
                         // artists in most cases
                         dbArtist.value?.also {
                             updateArtistInDatabase( it, page )
                         }
                     }
                     .onFailure { err ->
                         Timber.tag( "YouTubeArtist" ).e( err )
                         Toaster.e( R.string.error_failed_to_load_artist )
                     }

            isRefreshing = false
        }
    }

    fun onTabChanged( tab: Int ) { this.tabIndex = tab }

    fun getSongs(): List<Song> =
        if( tabIndex == 0 )
            _artistPage.value
                       ?.sections
                       ?.fastFlatMap { it.contents }
                       ?.fastMapNotNull { it as? InnertubeSong }
                       ?.fastMap( InnertubeSong::toSong )
                       .orEmpty()
        else
            artistLibrarySongs.value

    @OptIn(UnstableApi::class)
    fun getMediaItems() = getSongs().map( Song::asMediaItem )
}