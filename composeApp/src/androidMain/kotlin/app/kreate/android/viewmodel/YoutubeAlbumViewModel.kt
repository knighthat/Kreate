package app.kreate.android.viewmodel

import androidx.compose.ui.util.fastJoinToString
import androidx.compose.ui.util.fastMap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kreate.android.utils.innertube.toMediaItem
import app.kreate.compose.R
import app.kreate.database.Database
import app.kreate.database.insertIgnore
import app.kreate.database.models.Album
import app.kreate.database.models.Song
import app.kreate.database.models.SongAlbumMap
import app.kreate.gateway.innertube.YouTube
import app.kreate.gateway.innertube.models.InnertubeAlbum
import app.kreate.gateway.innertube.models.InnertubeSong
import app.kreate.utils.Toaster
import co.touchlab.kermit.Logger
import it.fast4x.rimusic.utils.isNetworkConnected
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.knighthat.utils.PropUtils
import org.koin.core.component.KoinComponent
import org.koin.core.component.get


class YoutubeAlbumViewModel(savedStateHandle: SavedStateHandle) : ViewModel(), KoinComponent {

    private val _albumPage = MutableStateFlow<InnertubeAlbum?>(null)
    private val _isRefreshing = MutableStateFlow(false)

    // browseId must not be empty or null in any case
    val browseId: String = savedStateHandle["browseId"]!!
    val params: String? = savedStateHandle["params"]
    val albumPage = _albumPage.asStateFlow()
    val isRefreshing = _isRefreshing.asStateFlow()
    val dbAlbum =
        Database.albumTable
                .findById( browseId )
                .stateIn( viewModelScope, SharingStarted.Eagerly, null )

    fun librarySongs(): StateFlow<List<Song>> =
        Database.songAlbumMapTable
                .allSongsOf( browseId )
                .stateIn( viewModelScope, SharingStarted.Lazily, emptyList() )

    fun onRefresh() {
        _isRefreshing.update { true }

        if( !isNetworkConnected(get()) ) {
            Toaster.noInternet()
            _isRefreshing.update { false }
            return
        }

        viewModelScope.launch( Dispatchers.IO ) {
            get<YouTube>()
                .getAlbum( browseId, params )
                .onSuccess { page ->
                    _albumPage.update { page }

                    Database.asyncTransaction {
                        val dbAlbum = dbAlbum.value
                        val onlineAlbum = Album(
                            id = page.id,
                            title = PropUtils.retainIfModified( dbAlbum?.title, page.name ),
                            thumbnailUrl = PropUtils.retainIfModified(
                                dbAlbum?.thumbnailUrl,
                                page.thumbnails.firstOrNull()?.url
                            ),
                            year = page.year?.toString(),
                            authorsText = PropUtils.retainIfModified(
                                dbAlbum?.authorsText,
                                page.artists.fastJoinToString { it.text }
                            ),
                            shareUrl = dbAlbum?.shareUrl,
                            timestamp = dbAlbum?.timestamp ?: System.currentTimeMillis(),
                            bookmarkedAt = dbAlbum?.bookmarkedAt,
                            isYoutubeAlbum = true
                        )

                        // Upsert to override/update default values
                        albumTable.upsert( onlineAlbum )

                        // Map ignore to make sure only positions
                        // are overridden, not the songs themselves
                        page.songs
                            .fastMap( InnertubeSong::toMediaItem )
                            .onEach( ::insertIgnore )
                            .mapIndexed { position, mediaItem ->
                                SongAlbumMap(
                                    songId = mediaItem.mediaId,
                                    albumId = page.id,
                                    position = position
                                )
                            }
                            .also( songAlbumMapTable::upsert )
                    }
                }
                .onFailure { err ->
                    Logger.e( "", err, "YouTubeAlbum" )
                    Toaster.e( R.string.error_failed_to_load_album )
                }

            _isRefreshing.update { false }
        }
    }
}