package me.knighthat.component.song

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import app.kreate.android.R
import it.fast4x.innertube.Innertube
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.appContext
import it.fast4x.rimusic.enums.NavRoutes
import it.fast4x.rimusic.models.Artist
import it.fast4x.rimusic.models.Song
import it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.guava.future
import me.knighthat.utils.Toaster
import timber.log.Timber

class GoToArtist private constructor(
    private val navController: NavController,
    private val getSong: () -> Song
): MenuIcon, Descriptive {

    companion object {
        @Composable
        operator fun invoke( navController: NavController, getSong: () -> Song ): GoToArtist =
            GoToArtist( navController, getSong )
    }

    override val iconId: Int = R.drawable.artists
    // TODO: Add string "About this artist"
    override val messageId: Int = R.string.artists
    override val menuIconTitle: String
        @Composable
        get() = appContext().getString( R.string.about ) + " ${getSong().artistsText}"

    override fun onShortClick() {
        val song = getSong()

        CoroutineScope( Dispatchers.IO ).future {
            var result = Database.findArtistOfSong( song.id ).firstOrNull()

            // If artist isn't stored inside database, attempt to fetch
            if( result == null ) {
                Toaster.n( R.string.looking_up_artist_online, song.artistsText )

                Innertube.player(videoId = song.id)
                         .onSuccess { response ->
                             response.second
                                     ?.videoDetails
                                     ?.channelId
                                     ?.let { result = Artist(it) }
                         }.onFailure {
                             Timber.tag("go_to_artist").e(it)
                             Toaster.e( R.string.failed_to_fetch_artist )
                         }
            }

            result
        }.get()?.let {
            navController.navigate(route = "${NavRoutes.artist.name}/${it.id}")
        }
    }
}