package me.knighthat.component.song

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import app.kreate.android.R
import app.kreate.android.utils.innertube.CURRENT_LOCALE
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.appContext
import it.fast4x.rimusic.enums.NavRoutes
import it.fast4x.rimusic.models.Song
import it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.knighthat.innertube.Innertube
import me.knighthat.utils.Toaster
import timber.log.Timber
import java.util.Optional

class GoToArtist(
    private val navController: NavController,
    private val song: Song,
): MenuIcon, Descriptive {

    override val iconId: Int = R.drawable.people
    // TODO: Add string "About this artist"
    override val messageId: Int = R.string.artists
    override val menuIconTitle: String
        @Composable
        get() = appContext().getString( R.string.about ) + " ${song.cleanArtistsText()}"

    private var channelId: Optional<String> = Optional.empty()

    init {
        CoroutineScope( Dispatchers.IO ).launch {
            Database.artistTable
                    .findBySongId( song.id )
                    .first()
                    .firstOrNull()
                    ?.id
                    ?.also { channelId = Optional.of( it ) }
        }
    }

    override fun onShortClick() {
        channelId.ifPresentOrElse(
            { NavRoutes.YT_ARTIST.navigateHere( navController, it ) },
            {
                Toaster.n( R.string.looking_up_artist_online, song.cleanArtistsText() )

                CoroutineScope( Dispatchers.IO ).launch {
                    Innertube.songBasicInfo( song.id, CURRENT_LOCALE)
                             .onSuccess { song ->
                                 song.artists
                                     .firstOrNull()
                                     ?.navigationEndpoint
                                     ?.browseEndpoint
                                     ?.also {
                                         NavRoutes.YT_ARTIST.navigateHere(
                                             navController,
                                             "${it.browseId}?params=${it.params}"
                                         )
                                     }
                             }
                             .onFailure { err ->
                                 Timber.tag( "GoToArtist" ).e( err )
                                 Toaster.e( R.string.error_failed_to_load_artist )
                             }
                }
            }
        )
    }
}