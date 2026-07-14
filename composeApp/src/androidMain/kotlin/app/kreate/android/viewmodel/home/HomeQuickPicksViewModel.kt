package app.kreate.android.viewmodel.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kreate.android.R
import app.kreate.android.service.player.StatefulPlayer
import app.kreate.android.utils.innertube.toMediaItem
import app.kreate.database.Database
import app.kreate.database.models.Artist
import app.kreate.database.models.Song
import app.kreate.gateway.innertube.YouTube
import app.kreate.gateway.innertube.models.InnertubeAlbum
import app.kreate.gateway.innertube.models.InnertubeCharts
import app.kreate.gateway.innertube.models.InnertubeExplore
import app.kreate.gateway.innertube.models.InnertubeHomePage
import app.kreate.gateway.innertube.models.InnertubeItem
import app.kreate.gateway.innertube.models.InnertubeSong
import app.kreate.gateway.innertube.models.MultiContent
import co.touchlab.kermit.Logger
import it.fast4x.rimusic.utils.asMediaItem
import it.fast4x.rimusic.utils.forcePlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.knighthat.utils.Toaster
import org.koin.core.component.KoinComponent


class HomeQuickPicksViewModel(
    private val youtube: YouTube,
    private val player: StatefulPlayer
) : ViewModel(), KoinComponent {

    private val logger = Logger.withTag( "HomeQuickPicks" )
    private val _charts = MutableStateFlow<InnertubeCharts?>(null)
    private val _homePage = MutableStateFlow<InnertubeHomePage?>(null)
    private val _relatedPage = MutableStateFlow<MultiContent?>(null)
    private val _explorePage = MutableStateFlow<InnertubeExplore?>(null)
    private val _albumsFromArtists = MutableStateFlow(emptyList<InnertubeAlbum>())

    val charts = _charts.asStateFlow()
    val homePage = _homePage.asStateFlow()
    val relatedPage = _relatedPage.asStateFlow()
    val explorePage = _explorePage.asStateFlow()
    val albumsFromArtists = _albumsFromArtists.asStateFlow()

    init {
        viewModelScope.launch( Dispatchers.Default ) {
            // Get following artists, if there's none, exit the coroutine
            val artistIds = withContext( Dispatchers.IO ) {
                Database.artistTable.allFollowing().firstOrNull()?.map( Artist::id )
            } ?: return@launch

            explorePage.collectLatest { page ->
                if( page == null ) {
                    _albumsFromArtists.update { emptyList() }
                    return@collectLatest
                }

                page.newAlbumsAndSingles
                    ?.contents
                    ?.mapNotNull { it as? InnertubeAlbum }
                    ?.filter { item ->
                        // Take if one of the artists is followed by this user
                        item.artists.any { it.navigationEndpoint?.browseEndpoint?.browseId in artistIds }
                    }
                    ?.distinctBy( InnertubeItem::id )
                    ?.also { albums ->
                        _albumsFromArtists.update { albums }
                    }
            }
        }
    }

    fun loadCharts() {
        viewModelScope.launch( Dispatchers.IO ) {

            youtube.getCharts()
                .onFailure { err ->
                    logger.e( "", err )
                    Toaster.e( R.string.error_failed_to_get_charts )
                }
                .onSuccess { charts ->
                    _charts.update { charts }
                }
        }
    }

    fun loadHomePage() {
        viewModelScope.launch( Dispatchers.IO ) {

            youtube.account
                   .getHomePage()
                   .onFailure { err ->
                       logger.e( "", err )
                       Toaster.e( R.string.error_failed_to_get_home_page )
                   }
                   .onSuccess { charts ->
                       _homePage.update { charts }
                   }
        }
    }

    fun loadRelatedSong( songId: String ) {
        viewModelScope.launch( Dispatchers.IO ) {

            youtube.getRelated( songId )
                   .onFailure { err ->
                       logger.e( "", err )
                       Toaster.e( R.string.error_failed_to_get_related_songs )
                   }
                   .onSuccess { multiContent ->
                       _relatedPage.update { multiContent }
                   }
        }
    }

    fun loadExplorePage() {
        viewModelScope.launch( Dispatchers.IO ) {

            youtube.explore()
                   .onFailure { err ->
                       logger.e( "", err )
                       Toaster.e( R.string.error_failed_to_get_explore_page )
                   }
                   .onSuccess { multiContent ->
                       _explorePage.update { multiContent }
                   }
        }
    }

    fun playAll( trendingSong: Song ) {
        player.stopRadio()
        player.forcePlay( trendingSong.asMediaItem )

        viewModelScope.launch( Dispatchers.Default ) {
            val queue =
                relatedPage.value
                           ?.sections
                           ?.flatMap { it.contents }
                           ?.filterIsInstance<InnertubeSong>()
                           ?.map { it.toMediaItem }
                           .orEmpty()
            withContext( Dispatchers.Main ) {
                player.addMediaItems( queue )
            }
        }
    }
}